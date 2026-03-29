#include "downloader.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <memory>
#include <mutex>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include <curl/curl.h>
#include <openssl/sha.h>

namespace fs = std::filesystem;

namespace {
    std::mutex g_consoleMutex;

    std::string toLower(std::string s) {
        std::ranges::transform(s, s.begin(), [](const unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        return s;
    }

    std::string formatBytes(const curl_off_t bytes) {
        constexpr double KB = 1024.0;
        constexpr double MB = 1024.0 * 1024.0;
        constexpr double GB = 1024.0 * 1024.0 * 1024.0;

        const auto v = static_cast<double>(bytes);
        std::ostringstream oss;
        if (v >= GB) {
            oss << std::fixed << std::setprecision(1) << (v / GB) << " GB";
        } else if (v >= MB) {
            oss << std::fixed << std::setprecision(1) << (v / MB) << " MB";
        } else if (v >= KB) {
            oss << std::fixed << std::setprecision(1) << (v / KB) << " KB";
        } else {
            oss << bytes << " B";
        }
        return oss.str();
    }

    std::string formatDuration(const std::chrono::steady_clock::duration d) {
        using namespace std::chrono;
        const auto s = duration_cast<seconds>(d).count();
        std::ostringstream oss;
        if (s >= 3600) {
            oss << (s / 3600) << "h " << ((s % 3600) / 60) << "m " << (s % 60) << "s";
        } else if (s >= 60) {
            oss << (s / 60) << "m " << (s % 60) << "s";
        } else {
            oss << s << "s";
        }
        return oss.str();
    }

    bool isHttpSuccess(const long code) {
        return code >= 200 && code < 300;
    }

    bool isRetryableHttpCode(const long code) {
        return code == 408 || code == 425 || code == 429 || (code >= 500 && code <= 599);
    }

    bool isRetryableCurlCode(const CURLcode code) {
        switch (code) {
            case CURLE_COULDNT_RESOLVE_HOST:
            case CURLE_COULDNT_CONNECT:
            case CURLE_OPERATION_TIMEDOUT:
            case CURLE_RECV_ERROR:
            case CURLE_SEND_ERROR:
            case CURLE_GOT_NOTHING:
            case CURLE_PARTIAL_FILE:
            case CURLE_HTTP2:
            case CURLE_HTTP2_STREAM:
            case CURLE_SSL_CONNECT_ERROR:
                return true;
            default:
                return false;
        }
    }

    class CurlGlobal {
    public:
        CurlGlobal(const CurlGlobal&) = delete;
        CurlGlobal& operator=(const CurlGlobal&) = delete;

        static void ensureInitialized() {
            static CurlGlobal g;
            (void)g;
        }

    private:
        CurlGlobal() {
            const CURLcode code = curl_global_init(CURL_GLOBAL_DEFAULT);
            if (code != CURLE_OK) {
                throw std::runtime_error(std::string("curl_global_init failed: ") + curl_easy_strerror(code));
            }
        }

        ~CurlGlobal() {
            curl_global_cleanup();
        }
    };

    class UniqueFile {
    public:
        UniqueFile() = default;
        ~UniqueFile() { close(); }

        UniqueFile(const UniqueFile&) = delete;
        UniqueFile& operator=(const UniqueFile&) = delete;

        void open(const fs::path& path, const char* mode) {
            close();
            path_ = path;
            file_ = std::fopen(path.string().c_str(), mode);
            if (!file_) {
                throw std::runtime_error("cannot open file: " + path.string());
            }
        }

        void close() noexcept {
            if (file_) {
                std::fclose(file_);
                file_ = nullptr;
            }
        }

        void flush() const {
            if (file_ && std::fflush(file_) != 0) {
                throw std::runtime_error("fflush failed: " + path_.string());
            }
        }

        [[nodiscard]] std::FILE* get() const noexcept { return file_; }

    private:
        std::FILE* file_ = nullptr;
        fs::path path_;
    };

    class TempFileGuard {
    public:
        explicit TempFileGuard(fs::path path) : path_(std::move(path)) {}
        ~TempFileGuard() {
            if (active_) {
                std::error_code ec;
                fs::remove(path_, ec);
            }
        }

        TempFileGuard(const TempFileGuard&) = delete;
        TempFileGuard& operator=(const TempFileGuard&) = delete;

        [[nodiscard]] const fs::path& path() const noexcept { return path_; }
        void release() noexcept { active_ = false; }

    private:
        fs::path path_;
        bool active_ = true;
    };

    class ProgressDisplay {
    public:
        explicit ProgressDisplay(std::string name) : name_(shortenName(std::move(name), 36)) {}

        ~ProgressDisplay() { stop(); }

        void start() {
            bool expected = false;
            if (!running_.compare_exchange_strong(expected, true)) return;
            startedAt_ = std::chrono::steady_clock::now();
            worker_ = std::thread([this]() { loop(); });
        }

        void update(const curl_off_t now, const curl_off_t total, const curl_off_t baseAlreadyHave) {
            currentBytes_.store(now, std::memory_order_relaxed);
            totalBytes_.store(total, std::memory_order_relaxed);
            baseBytes_.store(baseAlreadyHave, std::memory_order_relaxed);
            hasTotal_.store(total > 0, std::memory_order_relaxed);
        }

        void finish(const std::string& state) {
            stop();
            std::lock_guard<std::mutex> lock(g_consoleMutex);
            const std::string line = finalLine(state);
            clearAndPrint(line, true);
        }

    private:
        static std::string shortenName(std::string s, const std::size_t maxLen) {
            if (s.size() <= maxLen) return s;
            if (maxLen <= 3) return s.substr(0, maxLen);
            return s.substr(0, maxLen - 3) + "...";
        }

        void stop() {
            bool expected = true;
            if (!running_.compare_exchange_strong(expected, false)) {
                if (worker_.joinable()) worker_.join();
                return;
            }
            if (worker_.joinable()) worker_.join();
        }

        std::string bar(const int pct) const {
            constexpr int W = 20;
            const int filled = (std::max(0, std::min(100, pct)) * W) / 100;
            std::string s;
            s.reserve(W);
            for (int i = 0; i < W; ++i) {
                s.push_back(i < filled ? '#' : '.');
            }
            return s;
        }

        static void clearAndPrint(const std::string& line, const bool newline) {
            static std::size_t lastLen = 0;

            std::cout << '\r' << line;
            if (lastLen > line.size()) {
                std::cout << std::string(lastLen - line.size(), ' ');
            }
            if (newline) {
                std::cout << '\n';
                lastLen = 0;
            } else {
                std::cout << std::flush;
                lastLen = line.size();
            }
        }

        std::string liveLine(const char* frame) const {
            const auto now = currentBytes_.load(std::memory_order_relaxed);
            const auto total = totalBytes_.load(std::memory_order_relaxed);
            const auto base = baseBytes_.load(std::memory_order_relaxed);
            const bool hasTotal = hasTotal_.load(std::memory_order_relaxed);

            const auto allNow = now + base;
            const auto allTotal = (total > 0 ? total + base : 0);

            std::ostringstream oss;
            oss << name_ << " " << frame << " ";

            if (hasTotal && allTotal > 0) {
                int pct = static_cast<int>(
                    (100.0 * static_cast<double>(allNow)) / static_cast<double>(allTotal));
                pct = std::max(0, std::min(100, pct));

                oss << "[" << bar(pct) << "] "
                    << std::setw(3) << pct << "% "
                    << formatBytes(allNow) << "/" << formatBytes(allTotal);
            } else {
                oss << "[" << bar(0) << "] "
                    << " ?% "
                    << formatBytes(allNow) << "/unknown";
            }

            oss << " " << formatDuration(std::chrono::steady_clock::now() - startedAt_);
            return oss.str();
        }

        std::string finalLine(const std::string& state) const {
            const auto now = currentBytes_.load(std::memory_order_relaxed);
            const auto base = baseBytes_.load(std::memory_order_relaxed);

            std::ostringstream oss;
            oss << name_ << " " << state
                << " (" << formatBytes(now + base) << ", "
                << formatDuration(std::chrono::steady_clock::now() - startedAt_) << ")";
            return oss.str();
        }

        void loop() const {
            static constexpr const char* frames[] = {"|", "/", "-", "\\"};
            std::size_t i = 0;

            while (running_.load(std::memory_order_relaxed)) {
                {
                    std::lock_guard<std::mutex> lock(g_consoleMutex);
                    clearAndPrint(liveLine(frames[i % 4]), false);
                }
                ++i;
                std::this_thread::sleep_for(std::chrono::milliseconds(120));
            }
        }

        std::string name_;
        std::atomic<bool> running_{false};
        std::atomic<bool> hasTotal_{false};
        std::atomic<curl_off_t> currentBytes_{0};
        std::atomic<curl_off_t> totalBytes_{0};
        std::atomic<curl_off_t> baseBytes_{0};
        std::thread worker_;
        std::chrono::steady_clock::time_point startedAt_{};
    };

    struct ProgressContext {
        ProgressDisplay* display = nullptr;
        const std::atomic<bool>* cancelFlag = nullptr;
        curl_off_t resumeBase = 0;
    };

    size_t writeStringCallback(const void* contents,const size_t size, const size_t nmemb, void* userp) {
        const size_t total = size * nmemb;
        auto* out = static_cast<std::string*>(userp);
        out->append(static_cast<const char*>(contents), total);
        return total;
    }

    size_t writeFileCallback(const void* ptr, const size_t size, const size_t nmemb, void* stream) {
        auto* file = static_cast<std::FILE*>(stream);
        return std::fwrite(ptr, size, nmemb, file);
    }

    int progressCallback(void* clientp, const curl_off_t totalDownload, const curl_off_t nowDownload, curl_off_t, curl_off_t) {
        const auto* ctx = static_cast<ProgressContext*>(clientp);
        if (ctx) {
            if (ctx->display) {
                ctx->display->update(nowDownload, totalDownload, ctx->resumeBase);
            }
            if (ctx->cancelFlag && ctx->cancelFlag->load(std::memory_order_relaxed)) {
                return 1; // abort
            }
        }
        return 0;
    }

    void applyCommonOptions(CURL* curl, const downloader::DownloadRequest& req) {
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, req.followRedirects ? 1L : 0L);
        curl_easy_setopt(curl, CURLOPT_USERAGENT, req.userAgent.c_str());
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, req.verifySslPeer ? 1L : 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, req.verifySslHost ? 2L : 0L);
        curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, req.connectTimeoutSeconds);
        curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
        curl_easy_setopt(curl, CURLOPT_FAILONERROR, 0L);

        if (req.requestTimeoutSeconds > 0) {
            curl_easy_setopt(curl, CURLOPT_TIMEOUT, req.requestTimeoutSeconds);
        }

        if (req.lowSpeedLimitBytes > 0 && req.lowSpeedTimeSeconds > 0) {
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, req.lowSpeedLimitBytes);
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, req.lowSpeedTimeSeconds);
        }
    }

    void ensureParentExists(const fs::path& p) {
        std::error_code ec;
        const auto parent = p.parent_path();
        if (!parent.empty()) {
            fs::create_directories(parent, ec);
            if (ec) {
                throw std::runtime_error("failed to create parent directory: " + parent.string());
            }
        }
    }

    std::string makeTempSuffix() {
        const auto now = std::chrono::steady_clock::now().time_since_epoch().count();
        const auto tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
        std::ostringstream oss;
        oss << ".part-" << std::hex << now << "-" << tid;
        return oss.str();
    }

    fs::path makeTempPathFor(const fs::path& finalPath) {
        return finalPath.string() + ".part";
    }

    void moveIntoPlace(const fs::path& from, const fs::path& to, const bool overwrite) {
        std::error_code ec;
        if (fs::exists(to, ec)) {
            if (!overwrite) {
                throw std::runtime_error("target file already exists: " + to.string());
            }
            fs::remove(to, ec);
            if (ec) {
                throw std::runtime_error("failed to remove target file: " + to.string());
            }
        }

        fs::rename(from, to, ec);
        if (!ec) return;

        ec.clear();
        fs::copy_file(from, to, fs::copy_options::overwrite_existing, ec);
        if (ec) {
            throw std::runtime_error("failed to move file into place: " + to.string());
        }
        ec.clear();
        fs::remove(from, ec);
    }

    struct AttemptResult {
        CURLcode curlCode = CURLE_OK;
        long httpCode = 0;
        std::uintmax_t bytesWritten = 0;
        bool resumed = false;
        std::string error;
    };

    AttemptResult doSingleAttempt(const downloader::DownloadRequest& req, const fs::path& tempPath, ProgressDisplay* display) {
        AttemptResult ret;

        const std::unique_ptr<CURL,const decltype(&curl_easy_cleanup)> curl(curl_easy_init(), &curl_easy_cleanup);
        if (!curl) {
            ret.curlCode = CURLE_FAILED_INIT;
            ret.error = "curl_easy_init failed";
            return ret;
        }

        curl_off_t resumeFrom = 0;
        std::error_code ec;
        if (req.enableResume && fs::exists(tempPath, ec) && !ec) {
            resumeFrom = static_cast<curl_off_t>(fs::file_size(tempPath, ec));
            if (ec) resumeFrom = 0;
        }

        UniqueFile file;
        file.open(tempPath, (resumeFrom > 0) ? "ab" : "wb");
        ret.resumed = resumeFrom > 0;

        curl_easy_setopt(curl.get(), CURLOPT_URL, req.url.c_str());
        curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, writeFileCallback);
        curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, file.get());
        applyCommonOptions(curl.get(), req);

        if (resumeFrom > 0) {
            curl_easy_setopt(curl.get(), CURLOPT_RESUME_FROM_LARGE, resumeFrom);
        }

        #if LIBCURL_VERSION_NUM >= 0x072000
            ProgressContext ctx;
            ctx.display = display;
            ctx.cancelFlag = req.cancelFlag;
            ctx.resumeBase = resumeFrom;
            if (display || req.cancelFlag) {
                curl_easy_setopt(curl.get(), CURLOPT_XFERINFOFUNCTION, progressCallback);
                curl_easy_setopt(curl.get(), CURLOPT_XFERINFODATA, &ctx);
                curl_easy_setopt(curl.get(), CURLOPT_NOPROGRESS, 0L);
            }
        #endif

        ret.curlCode = curl_easy_perform(curl.get());
        curl_easy_getinfo(curl.get(), CURLINFO_RESPONSE_CODE, &ret.httpCode);

        try {
            file.flush();
        } catch (const std::exception& ex) {
            if (ret.curlCode == CURLE_OK) {
                ret.curlCode = CURLE_WRITE_ERROR;
                ret.error = ex.what();
            }
        }

        file.close();

        ret.bytesWritten = fs::exists(tempPath, ec) ? fs::file_size(tempPath, ec) : 0;
        if (ec) ret.bytesWritten = 0;

        if (ret.curlCode != CURLE_OK && ret.error.empty()) {
            ret.error = curl_easy_strerror(ret.curlCode);
        }
        if (ret.curlCode == CURLE_OK && !isHttpSuccess(ret.httpCode)) {
            ret.error = "HTTP " + std::to_string(ret.httpCode);
        }

        return ret;
    }
} // namespace

namespace downloader {
    bool httpGetText(const std::string& url, std::string& out, std::string& err) {
        try {
            CurlGlobal::ensureInitialized();
        } catch (const std::exception& ex) {
            err = ex.what();
            return false;
        }

        const std::unique_ptr<CURL, decltype(&curl_easy_cleanup)> curl(curl_easy_init(), &curl_easy_cleanup);
        if (!curl) {
            err = "curl_easy_init failed";
            return false;
        }

        out.clear();
        err.clear();

        const DownloadRequest req;
        curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, writeStringCallback);
        curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &out);
        applyCommonOptions(curl.get(), req);

        const CURLcode code = curl_easy_perform(curl.get());
        long httpCode = 0;
        curl_easy_getinfo(curl.get(), CURLINFO_RESPONSE_CODE, &httpCode);

        if (code != CURLE_OK) {
            err = curl_easy_strerror(code);
            return false;
        }
        if (!isHttpSuccess(httpCode)) {
            err = "HTTP " + std::to_string(httpCode);
            return false;
        }
        return true;
    }

    std::string sha256FileHex(const fs::path& file, std::string& err) {
        err.clear();

        std::FILE* fp = std::fopen(file.string().c_str(), "rb");
        if (!fp) {
            err = "cannot open file for sha256: " + file.string();
            return {};
        }

        SHA256_CTX ctx;
        SHA256_Init(&ctx);

        std::vector<unsigned char> buffer(1024 * 1024);
        while (true) {
            const size_t n = std::fread(buffer.data(), 1, buffer.size(), fp);
            if (n > 0) {
                SHA256_Update(&ctx, buffer.data(), n);
            }
            if (n < buffer.size()) {
                if (std::ferror(fp)) {
                    std::fclose(fp);
                    err = "read error while computing sha256: " + file.string();
                    return {};
                }
                break;
            }
        }

        std::fclose(fp);

        unsigned char digest[SHA256_DIGEST_LENGTH];
        SHA256_Final(digest, &ctx);

        static auto hex = "0123456789abcdef";
        std::string out;
        out.reserve(SHA256_DIGEST_LENGTH * 2);
        for (const unsigned char b : digest) {
            out.push_back(hex[(b >> 4) & 0xF]);
            out.push_back(hex[b & 0xF]);
        }
        return out;
    }

    DownloadResult downloadFile(const DownloadRequest& req) {
        DownloadResult result;
        result.savedPath = req.outputPath;

        if (req.url.empty()) {
            result.error = "url is empty";
            return result;
        }
        if (req.outputPath.empty()) {
            result.error = "outputPath is empty";
            return result;
        }

        try {
            CurlGlobal::ensureInitialized();
            ensureParentExists(req.outputPath);
        } catch (const std::exception& ex) {
            result.error = ex.what();
            return result;
        }

        if (std::error_code ec; fs::exists(req.outputPath, ec) && !req.overwrite) {
            result.error = "target file already exists: " + req.outputPath.string();
            return result;
        }

        const fs::path tempPath = makeTempPathFor(req.outputPath);
        TempFileGuard tempGuard(tempPath);

        std::optional<ProgressDisplay> display;
        if (req.showProgress) {
            std::string name = !req.displayName.empty() ? req.displayName :
                               (req.outputPath.filename().empty() ? "download" : req.outputPath.filename().string());
            display.emplace(name);
            display->start();
        }

        int totalAttempts = std::max(1, req.maxRetries + 1);
        for (int i = 1; i <= totalAttempts; ++i) {
            result.attempts = i;

            const auto [
                curlCode,
                httpCode,
                bytesWritten,
                resumed,
                error
                ] = doSingleAttempt(req, tempPath, display ? &(*display) : nullptr);

            result.httpCode = httpCode;
            result.bytesWritten = bytesWritten;
            result.resumed = resumed;

            if (curlCode == CURLE_ABORTED_BY_CALLBACK) {
                result.canceled = true;
                result.error = "download canceled";
                if (display) display->finish("canceled");
                return result;
            }

            if (curlCode == CURLE_OK && isHttpSuccess(httpCode)) {
                try {
                    moveIntoPlace(tempPath, req.outputPath, req.overwrite);
                    tempGuard.release();
                } catch (const std::exception& ex) {
                    result.error = ex.what();
                    if (display) display->finish("failed");
                    return result;
                }

                if (req.verifySha256) {
                    std::string shaErr;
                    std::string actual = sha256FileHex(req.outputPath, shaErr);
                    if (!shaErr.empty()) {
                        result.error = shaErr;
                        if (display) display->finish("failed");
                        return result;
                    }
                    if (toLower(actual) != toLower(req.expectedSha256)) {
                        result.error = "sha256 mismatch";
                        if (display) display->finish("failed");
                        return result;
                    }
                    result.sha256Verified = true;
                }

                result.ok = true;
                result.savedPath = req.outputPath;
                if (display) display->finish("done");
                return result;
            }

            result.error = error.empty() ? "download failed" : error;
            bool retry = (i < totalAttempts) &&
                         (isRetryableCurlCode(curlCode) || isRetryableHttpCode(httpCode));

            if (!retry) {
                if (display) display->finish("failed");
                return result;
            }

            {
                std::lock_guard<std::mutex> lock(g_consoleMutex);
                std::cerr << "\nretry " << i << "/" << req.maxRetries
                          << " because: " << result.error << std::endl;
            }
            std::this_thread::sleep_for(req.retryDelay);
        }

        if (display) display->finish("failed");
        if (result.error.empty()) result.error = "download failed";
        return result;
    }

    std::filesystem::path makeDownloadPath(const std::string& fileName) {
        #if defined(_WIN32)
            const char* tempEnv = std::getenv("TEMP");
            const fs::path base = tempEnv ? fs::path(tempEnv) : fs::temp_directory_path();
        #else
            const fs::path base = fs::temp_directory_path();
        #endif
            return base / fileName;
    }
} // namespace downloader
