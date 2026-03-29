#include "java_runtime_base_installer.h"

#include <atomic>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>

#include <nlohmann/json.hpp>
#include "../utils/net/downloader.h"
#include "../utils/platform_util.h"
#include "../utils/zip_extractor.h"

namespace fs = std::filesystem;
using json = nlohmann::json;

struct TemurinAsset {
    std::string link;
    std::string checksum;
    std::string fileName;
};

std::string buildTemurinApiUrl(const std::string& os, const std::string& arch) {
    return "https://api.adoptium.net/v3/assets/feature_releases/21/ga"
           "?image_type=jre"
           "&jvm_impl=hotspot"
           "&vendor=eclipse"
           "&heap_size=normal"
           "&os=" + os +
           "&architecture=" + arch;
}

std::string getStringOrThrow(const json& obj, const char* key) {
    if (!obj.contains(key) || !obj.at(key).is_string()) {
        throw std::runtime_error(std::string("missing or invalid string field: ") + key);
    }
    return obj.at(key).get<std::string>();
}

TemurinAsset parseFirstTemurinAsset(const std::string& jsonText) {
    json root;
    try {
        root = json::parse(jsonText);
    } catch (const std::exception& ex) {
        throw std::runtime_error(std::string("invalid JSON from Adoptium API: ") + ex.what());
    }

    if (!root.is_array()) {
        throw std::runtime_error("unexpected API response: root is not an array");
    }

    if (root.empty()) {
        throw std::runtime_error("no matching Temurin JRE 21 asset found");
    }

    const json& first = root.at(0);

    if (!first.contains("binaries") || !first.at("binaries").is_array() || first.at("binaries").empty()) {
        throw std::runtime_error("unexpected API response: missing or empty binaries array");
    }

    const json& binary0 = first.at("binaries").at(0);
    if (!binary0.is_object()) {
        throw std::runtime_error("unexpected API response: binaries[0] is not an object");
    }

    const json* chosen = nullptr;

    // zip/package
    if (binary0.contains("package") && binary0.at("package").is_object()) {
        chosen = &binary0.at("package");
    } else if (binary0.contains("installer") && binary0.at("installer").is_object()) {
        chosen = &binary0.at("installer");
    }

    if (chosen == nullptr) {
        throw std::runtime_error("unexpected API response: neither package nor installer found");
    }

    if (!chosen->contains("link") || !chosen->at("link").is_string()) {
        throw std::runtime_error("unexpected API response: missing package/installer link");
    }

    TemurinAsset asset;
    asset.link = chosen->at("link").get<std::string>();

    if (chosen->contains("checksum") && chosen->at("checksum").is_string()) {
        asset.checksum = chosen->at("checksum").get<std::string>();
    }

    if (chosen->contains("name") && chosen->at("name").is_string()) {
        asset.fileName = chosen->at("name").get<std::string>();
    } else {
        const auto pos = asset.link.find_last_of('/');
        asset.fileName = pos == std::string::npos ? "temurin-jre21.bin" : asset.link.substr(pos + 1);
    }

    return asset;
}

void installTemurinJre() {
    try {
        const std::string os = detectOs();
        const std::string arch = detectArch();

        const std::string apiUrl = buildTemurinApiUrl(os, arch);

        std::string jsonText;
        std::string httpErr;
        if (!downloader::httpGetText(apiUrl, jsonText, httpErr)) {
            throw std::runtime_error("Failed to query Adoptium API: ");
        }

        const auto [link, checksum, fileName] = parseFirstTemurinAsset(jsonText);

        fs::path outputDir = fs::current_path() / "runtime";
        fs::create_directories(outputDir);

        fs::path outputPath = outputDir / fileName;

        std::atomic<bool> cancelFlag{false};

        downloader::DownloadRequest req;
        req.url = link;
        req.outputPath = outputPath;
        req.displayName = "Temurin JRE 21";
        req.overwrite = true;
        req.showProgress = true;
        req.enableResume = true;
        req.verifySha256 = true;
        req.expectedSha256 = checksum;
        req.maxRetries = 3;
        req.connectTimeoutSeconds = 15;
        req.requestTimeoutSeconds = 0;
        req.cancelFlag = &cancelFlag;

        const auto [
            ok,
            canceled,
            resumed,
            sha256Verified,
            error,
            savedPath,
            httpCode,
            bytesWritten,
            attempts
            ] = downloader::downloadFile(req);

        if (!ok) {
            throw std::runtime_error(
                "Download failed: "
                + error
                +", http="
                + std::to_string(httpCode)
                + ", attempts="
                + std::to_string(attempts)
                + ", resumed="
                + (resumed ? "true" : "false")
            );
        }

        ZipExtractor::extract(savedPath.string(),(fs::current_path() / "runtime").string());
    } catch (const std::exception& ex) {
        throw std::runtime_error("Fatal error: " + std::string(ex.what()));
    }
}
