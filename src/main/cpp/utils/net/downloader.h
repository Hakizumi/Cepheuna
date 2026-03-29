#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <string>

namespace downloader {
    struct DownloadRequest {
        std::string url;
        std::filesystem::path outputPath;
        std::string displayName;
        std::string userAgent = "Hakihive-downloader/3.0";

        bool overwrite = true;
        bool showProgress = true;
        bool verifySslPeer = true;
        bool verifySslHost = true;
        bool followRedirects = true;

        bool enableResume = true;                 // Continue to transmit .part files
        bool verifySha256 = false;                // Check after download done
        std::string expectedSha256;               // hex

        long connectTimeoutSeconds = 15;
        long requestTimeoutSeconds = 0;           // 0 = no hard timeout
        long lowSpeedLimitBytes = 0;              // 0 = disabled
        long lowSpeedTimeSeconds = 0;             // 0 = disabled
        int maxRetries = 2;
        std::chrono::milliseconds retryDelay{800};

        const std::atomic<bool>* cancelFlag = nullptr; // Cancel when true
    };

    struct DownloadResult {
        bool ok = false;
        bool canceled = false;
        bool resumed = false;
        bool sha256Verified = false;

        std::string error;
        std::filesystem::path savedPath;
        long httpCode = 0;
        std::uintmax_t bytesWritten = 0;
        int attempts = 0;
    };

    bool httpGetText(const std::string& url, std::string& out, std::string& err);

    DownloadResult downloadFile(const DownloadRequest& request);

    std::filesystem::path makeDownloadPath(const std::string& fileName);

    std::string sha256FileHex(const std::filesystem::path& file, std::string& err);

} // namespace downloader
