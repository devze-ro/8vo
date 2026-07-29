cmake_minimum_required(VERSION 3.22.1)

project(octavo_android_port1 LANGUAGES C)

if(NOT ANDROID)
  message(FATAL_ERROR "The 8vo Android host requires the Android NDK toolchain")
endif()

if(NOT DEFINED OCTAVO_REPO_ROOT)
  message(FATAL_ERROR "OCTAVO_REPO_ROOT must name the 8vo repository root")
endif()

add_library(
  octavo
  SHARED
  octavo_android_jni.c
)

target_include_directories(
  octavo
  PRIVATE
  "${OCTAVO_REPO_ROOT}/code"
)

target_compile_features(octavo PRIVATE c_std_11)
target_compile_options(
  octavo
  PRIVATE
  -Wall
  -Wextra
  -Werror
)

find_library(android_library android REQUIRED)
find_library(log_library log REQUIRED)
target_link_libraries(octavo PRIVATE "${android_library}" "${log_library}")
