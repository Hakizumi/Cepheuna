#include <algorithm>
#include <string>
#include <ranges>
#include <vector>

#include "string_util.h"

using std::string;

string toLower(string s)
{
    std::ranges::transform(s, s.begin(), [](const unsigned char c) {
        return std::tolower(c);
    });
    return s;
}

string toUpper(string s)
{
    std::ranges::transform(s, s.begin(), [](const unsigned char c) {
        return std::toupper(c);
    });
    return s;
}

std::string trimCopy(std::string s)
{
    auto not_space = [](const unsigned char c) { return !std::isspace(c); };
    s.erase(s.begin(), std::ranges::find_if(s, not_space));
    s.erase(std::find_if(s.rbegin(), s.rend(), not_space).base(), s.end());
    return s;
}

std::string toLowerCopy(std::string s)
{
    std::ranges::transform(s, s.begin(), [](const unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return s;
}

std::vector<string> splitBy(const std::string& s, std::string delimiter)
{
    std::vector<std::string> tokens;

    for (auto&& part : s | std::views::split(delimiter)) {
        string str(part.begin(), part.end());
        tokens.push_back(str);
    }

    return tokens;
}

std::string replace(std::string s, const std::string& target, const std::string& replacement)
{
    size_t pos = 0;
    while ((pos = s.find(target, pos)) != string::npos) {
        s.replace(pos, target.length(), replacement);
        pos += replacement.length();
    }

    return s;
}