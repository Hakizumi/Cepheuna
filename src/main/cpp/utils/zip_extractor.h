#pragma once

#include <filesystem>
#include <string>
#include <vector>

namespace fs = std::filesystem;

class ZipExtractor {
public:
    struct Result {
        bool success = false;
        std::string message;
        bool stripped_duplicate_root = false;
    };

    static Result extract(const std::string& archive_path, const std::string& output_parent_dir);

private:
    static void cleanup(struct archive* in, archive* out);

    static bool openArchiveReader(archive*& a, const std::string& archive_path);

    static int copyData(archive* ar, archive* aw);

    static std::string getArchiveBaseName(const fs::path& archive_path);

    static std::vector<std::string> splitPath(const std::string& raw);

    static fs::path partsToPath(const std::vector<std::string>& parts);

    static fs::path normalizePathLexically(const fs::path& p);

    static bool isSubPath(const fs::path& base, const fs::path& target);

    static bool detectStripDuplicateBase(
        const std::string& archivePath,
        const std::string& baseName,
        size_t& stripCount,
        std::string& error
    );
};
