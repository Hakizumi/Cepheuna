#include "application_config_deployer.h"

#include <fstream>
#include <filesystem>
#include <iostream>

#include "../utils/input_util.h"
#include "../utils/string_util.h"

namespace fs = std::filesystem;

using std::cout;
using std::endl;

std::string content = R"(
server:
  port: 11622

spring:
  application:
    name: Hakihive

  ai:
    openai:
      api-key: sk-xxxx   # You configure
      base-url: https://api.openai.com

hakihive:
  models:
    # Models config properties,see org/hakizumi/hakihive/config/ModelProperties
    decision-model: gpt-4  # main reply model

  tools:
    # config about ai-agent-tools
    io-tools:
      # see org.hakizumi.hakihive.tools.IOTools
      whitelist:
        - E:/safe_path    # Safe path
      blacklist:
        # blacklist paths ( model cannot access )
        - /    # All

      # If the whitelist and blacklist have duplicated entries, an IllegalArgumentException will be called

logging:
  level:
    root: info
    org.hakizumi.hakihive: debug  # optional
)";

void deployApplicationConfig()
{
    if (!fs::exists("config") || !fs::is_directory("config")) {
        fs::create_directory("config");
    }

    const fs::path p = "config/application.yml";

    if (fs::exists(p) && fs::is_regular_file(p)) {
        cout << "=== Application config file is already in current path,do you want to cover it? ( Default No ) ===" << endl;
        if (!getInputBoolean(false)) return;
    }

    std::ofstream ofs(p);

    if (!ofs.is_open())
    {
        throw std::runtime_error("Error opening file");
    }

    ofs << content;

    ofs.close();
}
