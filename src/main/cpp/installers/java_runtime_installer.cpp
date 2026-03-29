#include <iostream>
#include <regex>
#include <cstdio>
#include <cstdlib>

#include "../utils/input_util.h"
#include "java_runtime_base_installer.h"
#include "java_runtime_installer.h"

#ifdef _WIN32
    // For compatibility
    // Windows will call an error when using popen
    #define popen _popen
    #define pclose _pclose
#endif

using std::cout;
using std::endl;
using std::cin;

void installJavaRuntime()
{
    if (const int java_ev = getEnvJavaRuntimeVersion(); java_ev >= 21)
    {
        cout
        << "=== Detected Java Runtime "
        << java_ev
        << " on environment,do you want to download a Java Runtime 21 in current path? ( Default No -- recommended ) ==="
        << endl;

        if (getInputBoolean()) installTemurinJre();
    }
    else if (const int java_pv = getPathJavaRuntimeVersion(); java_pv >= 21)
    {
        cout
        << "=== Detected Java Runtime "
        << java_pv
        << " on current path,skipped downloading ==="
        << endl;
    }
    else
    {
        cout << "=== Java Runtime not detected,downloading ... ===" << endl;
        installTemurinJre();
    }
}

int getEnvJavaRuntimeVersion() {
    #ifdef _WIN32
        const int ret = system("java -version >nul 2>&1");
    #else
        const int ret = system("java -version >/dev/null 2>&1");
    #endif

    if (ret != 0) return -1;

    FILE* pipe = popen("java -version 2>&1", "r");
    if (!pipe) {
        return -1;
    }

    return parseJavaVersion(pipe);
}

int getPathJavaRuntimeVersion()
{
    #ifdef _WIN32
        const auto javaCmd = "runtime/bin/java.exe";
        const int ret = system("runtime/bin/java.exe -version >nul 2>&1");
    #else
        const char* javaCmd = "runtime/bin/java";
        const int ret = system("runtime/bin/java -version >/dev/null 2>&1");
    #endif

    if (ret != 0) return -1;

    const std::string cmd = std::string(javaCmd) + " -version 2>&1";
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) {
        return -1;
    }

    return parseJavaVersion(pipe);
}

int parseJavaVersion(FILE*& pipe)
{
    std::string output;

    char buffer[256];
    while (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        output += buffer;
    }
    pclose(pipe);

    const std::regex re(R"(version\s+\"([^"]+)\")");
    std::smatch match;
    if (!std::regex_search(output, match, re)) {
        return -1;
    }

    std::string ver = match[1].str();
    int major;

    if (ver.rfind("1.", 0) == 0) {
        const size_t dot = ver.find('.', 2);
        major = std::stoi(dot == std::string::npos ? ver.substr(2) : ver.substr(2, dot - 2));
    } else {
        const size_t dot = ver.find('.');
        major = std::stoi(dot == std::string::npos ? ver : ver.substr(0, dot));
    }

    return major;
}