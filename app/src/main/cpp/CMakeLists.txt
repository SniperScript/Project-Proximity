cmake_minimum_required(VERSION 3.22.1)

project("proximity-native")

add_library(
    proximity-native
    SHARED
    native-bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    proximity-native
    ${log-lib}
)