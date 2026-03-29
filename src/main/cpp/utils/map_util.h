#pragma once

#include <map>
#include <set>
#include <vector>
#include <utility>

template<typename K, typename V>
std::set<K> keySet(const std::map<K, V>& map)
{
    std::set<K> keys;
    for (const auto& entry : map) {
        keys.insert(entry.first);
    }
    return keys;
}

template<typename K, typename V>
std::vector<K> keyVec(const std::map<K, V>& map)
{
    std::vector<K> keys;
    keys.reserve(map.size());
    for (const auto& entry : map) {
        keys.push_back(entry.first);
    }
    return keys;
}

template<typename K, typename V>
std::vector<V> valueList(const std::map<K, V>& map)
{
    std::vector<V> values;
    values.reserve(map.size());
    for (const auto& entry : map) {
        values.push_back(entry.second);
    }
    return values;
}

template<typename K, typename V>
std::set<std::pair<K, V>> entrySet(const std::map<K, V>& map)
{
    std::set<std::pair<K, V>> entries;
    for (const auto& entry : map) {
        entries.insert(entry);
    }
    return entries;
}
