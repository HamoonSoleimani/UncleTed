cmake_minimum_required(VERSION 3.22.1)

project("uncleted_native")

# Enforce C++17 Standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure Clang Security Flags: ARMv8.5-A MTE, Stack Protectors, and Optimization
add_compile_options(
    -march=armv8.5-a+memtag
    -fsanitize=memtag
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
    -O3
    -Wall
    -Werror
    -fPIC
)

# Declare native defense library
add_library(
    uncleted_native
    SHARED
    native-security.cpp
)

# Locate Android NDK platform libraries
find_library(log-lib log)
find_library(android-lib android)

# Link essential libraries (zlib for compression, log for diagnostics)
target_link_libraries(
    uncleted_native
    ${log-lib}
    ${android-lib}
    -lz
    -latomic
    -lm
)