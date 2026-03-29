#pragma once

#include <string>
#include <vector>

using std::string;

struct CandidatePackage {
    string package_type; // msi / pkg / deb / rpm / apk / tar.gz / zip
    string package_ext;
    bool installer = true;    // false = archive fallback
};

struct PlatformInfo {
    string os = "unknown";
    string os_api;       // windows / mac / linux / alpine-linux
    string arch_api;     // x64 / x86-32 / aarch64 / arm / ppc64le
    std::vector<CandidatePackage> candidates;
};

string detectOs();

string detectArch();

string detectLinuxFlavorOsApi();

string detectLinuxPackageType();

PlatformInfo getPlatformInfo();