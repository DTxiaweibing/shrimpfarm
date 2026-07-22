cmake_minimum_required(VERSION 3.22)
project("shrimpfarm_watermark")

add_library(native_watermark SHARED native-lib.cpp)

target_link_libraries(native_watermark
    android
    log
    jnigraphics)
