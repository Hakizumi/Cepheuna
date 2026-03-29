cmake_minimum_required(VERSION 3.16)

project(Hakihive VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB_RECURSE SRC_FILES CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/*.cpp"
)

add_executable(Hakihive ${SRC_FILES})

if (WIN32)
    # Prevent macro definition pollution of Windows
    target_compile_definitions(Hakihive PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
endif()

find_package(CURL REQUIRED)
find_package(nlohmann_json REQUIRED)
find_package(ftxui CONFIG REQUIRED)
find_package(OpenSSL REQUIRED)
find_package(LibArchive REQUIRED)

target_link_libraries(Hakihive PRIVATE
        CURL::libcurl
        nlohmann_json::nlohmann_json
        ftxui::screen
        ftxui::dom
        ftxui::component
        OpenSSL::SSL
        OpenSSL::Crypto
        LibArchive::LibArchive
)
