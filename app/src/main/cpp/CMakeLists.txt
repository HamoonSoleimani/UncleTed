cmake_minimum_required(VERSION 3.22.1)

project("uncleted_native")

add_library(
    uncleted_native
    SHARED
    native-security.cpp
)

target_compile_options(
    uncleted_native
    PRIVATE
    -march=armv8.5-a+memtag
    -fsanitize=memtag
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
    -O3
    -Wall
    -Werror
)

target_link_libraries(
    uncleted_native
    android
    log
)