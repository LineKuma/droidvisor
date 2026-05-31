cmake_minimum_required(VERSION 3.18.1)
project("microdroid_payload")

add_library(microdroid_payload SHARED microdroid_payload.cpp)

target_link_libraries(microdroid_payload
    android
    log
)
