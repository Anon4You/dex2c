cmake_minimum_required(VERSION 3.20)
project(dex2c_runtime CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB DEX2C_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/nc/*.cpp")

add_library(dex2c SHARED ${DEX2C_SOURCES})
target_include_directories(dex2c PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/nc")
target_compile_options(dex2c PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_options(dex2c PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(dex2c log)
