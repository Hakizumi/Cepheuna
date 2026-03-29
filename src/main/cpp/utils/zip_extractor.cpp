#include "zip_extractor.h"

#include <filesystem>
#include <iostream>
#include <string>
#include <vector>

#include <archive.h>
#include <archive_entry.h>

namespace fs = std::filesystem;

ZipExtractor::Result ZipExtractor::extract(const std::string& archive_path, const std::string& output_parent_dir) {
        Result result;

        fs::path archivePath(archive_path);
        if (!fs::exists(archivePath)) {
            result.message = "Archive not found: " + archive_path;
            return result;
        }

        const std::string baseName = getArchiveBaseName(archivePath);

        std::error_code ec;
        fs::create_directories(output_parent_dir, ec);
        if (ec) {
            result.message = "Failed to create output parent dir: " + ec.message();
            return result;
        }

        size_t stripCount = 0;
        std::string detectError;
        if (!detectStripDuplicateBase(archive_path, baseName, stripCount, detectError)) {
            result.message = "Detect structure failed: " + detectError;
            return result;
        }

        archive* in = nullptr;
        if (!openArchiveReader(in, archive_path)) {
            result.message = "Failed to open archive: " + archive_path;
            return result;
        }

        archive* out = archive_write_disk_new();
        if (!out) {
            archive_read_close(in);
            archive_read_free(in);
            result.message = "Failed to create disk writer";
            return result;
        }

        archive_write_disk_set_options(
            out,
            ARCHIVE_EXTRACT_TIME |
            ARCHIVE_EXTRACT_PERM |
            ARCHIVE_EXTRACT_ACL |
            ARCHIVE_EXTRACT_FFLAGS
        );
        archive_write_disk_set_standard_lookup(out);

        struct archive_entry* entry = nullptr;

        const fs::path outputBase = fs::weakly_canonical(fs::path(output_parent_dir), ec);
        if (ec) {
            archive_write_close(out);
            archive_write_free(out);
            archive_read_close(in);
            archive_read_free(in);
            result.message = "Failed to canonicalize output dir: " + ec.message();
            return result;
        }

        while (true) {
            int r = archive_read_next_header(in, &entry);
            if (r == ARCHIVE_EOF) {
                break;
            }
            if (r != ARCHIVE_OK) {
                result.message = std::string("Read header failed: ") + archive_error_string(in);
                cleanup(in, out);
                return result;
            }

            const char* rawPath = archive_entry_pathname(entry);
            if (!rawPath) {
                archive_read_data_skip(in);
                continue;
            }

            std::vector<std::string> parts = splitPath(rawPath);
            if (parts.empty()) {
                archive_read_data_skip(in);
                continue;
            }

            if (stripCount > 0) {
                if (parts.size() <= stripCount) {
                    archive_read_data_skip(in);
                    continue;
                }
                parts.erase(parts.begin(), parts.begin() + static_cast<std::ptrdiff_t>(stripCount));
            }

            fs::path relativePath = partsToPath(parts);

            if (relativePath.empty()) {
                archive_read_data_skip(in);
                continue;
            }

            if (relativePath.is_absolute()) {
                archive_read_data_skip(in);
                continue;
            }

            fs::path finalPath = outputBase / relativePath;

            fs::path normalizedFinal = normalizePathLexically(finalPath);
            if (!isSubPath(outputBase, normalizedFinal)) {
                archive_read_data_skip(in);
                continue;
            }

            if (archive_entry_filetype(entry) == AE_IFDIR) {
                std::error_code dirEc;
                fs::create_directories(normalizedFinal, dirEc);
            } else {
                std::error_code dirEc;
                fs::create_directories(normalizedFinal.parent_path(), dirEc);
            }

            archive_entry_set_pathname(entry, normalizedFinal.string().c_str());

            r = archive_write_header(out, entry);
            if (r != ARCHIVE_OK) {
                if (r < ARCHIVE_WARN) {
                    result.message = std::string("Write header failed: ") + archive_error_string(out);
                    cleanup(in, out);
                    return result;
                }
            } else {
                r = copyData(in, out);
                if (r != ARCHIVE_OK) {
                    result.message = std::string("Copy data failed: ") + archive_error_string(out);
                    cleanup(in, out);
                    return result;
                }
            }

            r = archive_write_finish_entry(out);
            if (r != ARCHIVE_OK) {
                result.message = std::string("Finish entry failed: ") + archive_error_string(out);
                cleanup(in, out);
                return result;
            }
        }

        cleanup(in, out);

        result.success = true;
        result.stripped_duplicate_root = (stripCount == 1);
        result.message = result.stripped_duplicate_root
            ? "Extracted successfully. Duplicate root stripped."
            : "Extracted successfully.";
        return result;
    }

void ZipExtractor::cleanup(archive* in, archive* out) {
    if (out) {
        archive_write_close(out);
        archive_write_free(out);
    }
    if (in) {
        archive_read_close(in);
        archive_read_free(in);
    }
}

bool ZipExtractor::openArchiveReader(archive*& a, const std::string& archive_path) {
    a = archive_read_new();
    if (!a) {
        return false;
    }

    archive_read_support_format_tar(a);
    archive_read_support_filter_gzip(a);
    archive_read_support_filter_bzip2(a);
    archive_read_support_filter_xz(a);

    int r = archive_read_open_filename(a, archive_path.c_str(), 10240);
    if (r != ARCHIVE_OK) {
        archive_read_free(a);
        a = nullptr;
        return false;
    }
    return true;
}

int ZipExtractor::copyData(archive* ar, archive* aw) {
    const void* buff = nullptr;
    size_t size = 0;
    la_int64_t offset = 0;

    while (true) {
        int r = archive_read_data_block(ar, &buff, &size, &offset);
        if (r == ARCHIVE_EOF) {
            return ARCHIVE_OK;
        }
        if (r != ARCHIVE_OK) {
            return r;
        }

        r = archive_write_data_block(aw, buff, size, offset);
        if (r != ARCHIVE_OK) {
            return r;
        }
    }
}

std::string ZipExtractor::getArchiveBaseName(const fs::path& archive_path) {
    const std::string name = archive_path.filename().string();

    static const std::vector<std::string> exts = {
        ".tar.bz2", ".tbz2",
        ".tar.gz", ".tgz",
        ".tar.xz", ".txz",
        ".tar"
    };

    for (const auto& ext : exts) {
        if (name.size() > ext.size() &&
            name.compare(name.size() - ext.size(), ext.size(), ext) == 0) {
            return name.substr(0, name.size() - ext.size());
            }
    }

    return archive_path.stem().string();
}

std::vector<std::string> ZipExtractor::splitPath(const std::string& raw) {
    std::vector<std::string> parts;
    fs::path p(raw);

    for (const auto& part : p) {
        std::string s = part.string();
        if (s.empty() || s == "." || s == ".." || s == "/") {
            continue;
        }
        parts.push_back(s);
    }
    return parts;
}

fs::path ZipExtractor::partsToPath(const std::vector<std::string>& parts) {
    fs::path p;
    for (const auto& part : parts) {
        p /= part;
    }
    return p;
}

fs::path ZipExtractor::normalizePathLexically(const fs::path& p) {
    return p.lexically_normal();
}

bool ZipExtractor::isSubPath(const fs::path& base, const fs::path& target) {
    auto baseIt = base.begin();
    auto targetIt = target.begin();

    for (; baseIt != base.end() && targetIt != target.end(); ++baseIt, ++targetIt) {
        if (*baseIt != *targetIt) {
            return false;
        }
    }

    return baseIt == base.end();
}

bool ZipExtractor::detectStripDuplicateBase(
    const std::string& archivePath,
    const std::string& baseName,
    size_t& stripCount,
    std::string& error
    )
{
    stripCount = 0;
    error.clear();

    archive* in = nullptr;
    if (!openArchiveReader(in, archivePath)) {
        error = "cannot open archive for detection";
        return false;
    }

    struct archive_entry* entry = nullptr;
    bool foundAnyValidPath = false;
    bool allMatchDoubleBase = true;

    while (true) {
        int r = archive_read_next_header(in, &entry);
        if (r == ARCHIVE_EOF) {
            break;
        }
        if (r != ARCHIVE_OK) {
            error = archive_error_string(in) ? archive_error_string(in) : "unknown archive read error";
            archive_read_close(in);
            archive_read_free(in);
            return false;
        }

        const char* rawPath = archive_entry_pathname(entry);
        if (!rawPath) {
            archive_read_data_skip(in);
            continue;
        }

        std::vector<std::string> parts = splitPath(rawPath);
        if (parts.empty()) {
            archive_read_data_skip(in);
            continue;
        }

        foundAnyValidPath = true;

        // Is duplicate root directory only if all entries start with base
        if (parts.size() < 2 || parts[0] != baseName || parts[1] != baseName) {
            allMatchDoubleBase = false;
        }

        archive_read_data_skip(in);
    }

    archive_read_close(in);
    archive_read_free(in);

    if (foundAnyValidPath && allMatchDoubleBase) {
        stripCount = 1;
    }

    return true;
}
