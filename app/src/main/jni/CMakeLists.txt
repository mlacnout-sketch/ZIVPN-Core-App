cmake_minimum_required(VERSION 3.22.1)

project("zivpn_jni")

add_library(
        zivpn_jni
        SHARED
        zivpn_jni.c)

find_library(
        log-lib
        log)

target_link_libraries(
        zivpn_jni
        ${log-lib})
