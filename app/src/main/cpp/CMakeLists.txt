cmake_minimum_required(VERSION 3.22.1)

project("uncleted_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
    -fPIC
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    uncleted_native
    PRIVATE
    ${log-lib}
    ${android-lib}
    z
)