#pragma once

#include <iostream>

void installJavaRuntime();

int getEnvJavaRuntimeVersion();

int getPathJavaRuntimeVersion();

int parseJavaVersion(FILE*& pipe);