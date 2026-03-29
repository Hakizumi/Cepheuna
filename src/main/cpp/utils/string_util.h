#pragma once

#include <vector>

using std::string;

string toLower(string s);

string toUpper(string s);

std::string trimCopy(std::string s);

std::string toLowerCopy(std::string s);

std::vector<string> splitBy(const std::string& s, std::string delimiter);

std::string replace(std::string s, const std::string& target, const std::string& replacement);