#include <string>
#include <filesystem>
#include <fstream>

#include "string_util.h"
#include "platform_util.h"

std::string detectOs() {
    #if defined(_WIN32)
        return "windows";
    #elif defined(__APPLE__)
        return "mac";
    #elif defined(__linux__)
        return "linux";
    #else
        return "unknown";
    #endif
}

string detectArch() {
    #if defined(_M_X64) || defined(__x86_64__)
        return "x64";
    #elif defined(_M_IX86) || defined(__i386__)
        return "x86-32";
    #elif defined(_M_ARM64) || defined(__aarch64__)
        return "aarch64";
    #elif defined(__arm__) || defined(_M_ARM)
        return "arm";
    #elif defined(__powerpc64__) && defined(__LITTLE_ENDIAN__)
        return "ppc64le";
    #elif defined(__s390x__)
        return "s390x";
    #elif defined(__riscv) && (__riscv_xlen == 64)
        return "riscv64";
    #else
        return "unknown";
    #endif
}

string detectLinuxFlavorOsApi() {
    if (std::filesystem::exists("/etc/alpine-release")) {
        return "alpine-linux";
    }
    return "linux";
}

string detectLinuxPackageType() {
    if (std::filesystem::exists("/etc/alpine-release")) return "apk";
    if (std::filesystem::exists("/etc/debian_version")) return "deb";
    if (std::filesystem::exists("/etc/redhat-release")) return "rpm";

    std::ifstream os_release("/etc/os-release");
    std::string content((std::istreambuf_iterator<char>(os_release)),
                        std::istreambuf_iterator<char>());
    content = toLowerCopy(content);

    if (content.find("id_like=debian") != std::string::npos ||
        content.find("id=debian") != std::string::npos ||
        content.find("id=ubuntu") != std::string::npos) {
        return "deb";
        }
    if (content.find("id=fedora") != std::string::npos ||
        content.find("id=rhel") != std::string::npos ||
        content.find("id=centos") != std::string::npos ||
        content.find("id=rocky") != std::string::npos ||
        content.find("id=almalinux") != std::string::npos ||
        content.find("id=opensuse") != std::string::npos ||
        content.find("id=sles") != std::string::npos) {
        return "rpm";
        }
    return "tar.gz";
}

PlatformInfo getPlatformInfo() {
    PlatformInfo p;
    p.os = detectOs();
    p.arch_api = detectArch();

    if (p.os == "Windows")
    {
        p.os_api = "windows";
        p.candidates = {
                        {"msi", ".msi", true},
                        {"zip", ".zip", false}
        };
    }

    if (p.os == "MacOS")
    {
        p.os_api = "mac";
        p.candidates = {
                        {"pkg", ".pkg", true},
                        {"tar.gz", ".tar.gz", false}
        };
    }

    if (p.os == "Linux")
    {
        p.os_api = detectLinuxFlavorOsApi();
        const std::string preferred = detectLinuxPackageType();

        if (preferred == "deb") {
            p.candidates = {
                            {"deb", ".deb", true},
                            {"tar.gz", ".tar.gz", false}
            };
        } else if (preferred == "rpm") {
            p.candidates = {
                            {"rpm", ".rpm", true},
                            {"tar.gz", ".tar.gz", false}
            };
        } else if (preferred == "apk") {
            p.candidates = {
                            {"apk", ".apk", true},
                            {"tar.gz", ".tar.gz", false}
            };
        } else {
            p.candidates = {
                            {"tar.gz", ".tar.gz", false}
            };
        }
    }

    return p;
}