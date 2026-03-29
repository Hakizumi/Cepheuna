#include "hakihive_jar_downloader.h"

#include <iostream>

#include "../utils/list_select_tui_util.h"
#include "../utils/net/downloader.h"

namespace fs = std::filesystem;

void downloadHakihiveJar()
{
    std::cout << "=== Choose a Hakihive version ===" << std::endl;

    const std::vector<std::string> versions = {
        "1.5.0"
    };

    const std::string version = singleChoicesTui(versions);

    downloader::DownloadRequest req;

    req.url = "https://github.com/Hakizumi/hakihive/releases/download/v" + version + "/hakihive-v" + version + ".jar";
    req.displayName = "hakihive-v" + version + ".jar";
    req.outputPath = fs::current_path();

    if (const auto result = downloader::downloadFile(req); !result.ok) {
        throw std::runtime_error("Error downloading Hakihive jar: ");
    }
}
