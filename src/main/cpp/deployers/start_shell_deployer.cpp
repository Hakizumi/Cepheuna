#include "start_shell_deployer.h"

#include <fstream>
#include <iostream>
#include <string>

#include "../utils/platform_util.h"

void deployStartShell()
{
    const std::string os = detectOs();

    if (os == "windows")
    {
        std::ofstream ofs("start.bat");

        if (!ofs.is_open())
        {
            throw std::runtime_error("Error opening start shell file.");
        }

        ofs << "@echo off";
        ofs << "runtime/bin/java.exe -Dsherpa_onnx.native.path=./lib -jar hakihive.jar";
        ofs << "pause";

        ofs.close();
    }
    else if (os == "linux" || os == "mac")
    {
        std::ofstream ofs("start.sh");

        if (!ofs.is_open())
        {
            throw std::runtime_error("Error opening start shell file.");
        }

        ofs << "#!/bin/sh";
        ofs << "runtime/bin/java.exe -Dsherpa_onnx.native.path=./lib -jar hakihive.jar";

        ofs.close();
    }
    else
    {
        throw std::runtime_error("Unsupported OS: " + os);
    }
}