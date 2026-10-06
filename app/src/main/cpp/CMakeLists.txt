cmake_minimum_required(VERSION 3.22.1)
project(retrolink_native)

add_library(retrolink_native SHARED retrolink_native.cpp)

target_include_directories(retrolink_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(retrolink_native
        android
        log
        EGL
        GLESv3
        dl)
