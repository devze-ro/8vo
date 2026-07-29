cmake_minimum_required(VERSION 3.22.1)

project(octavo_android_port2 LANGUAGES C)

if(NOT ANDROID)
  message(FATAL_ERROR "The 8vo Android host requires the Android NDK toolchain")
endif()

foreach(required_directory
        OCTAVO_REPO_ROOT
        OCTAVO_GROUND0_DIR
        OCTAVO_READER0_DIR
        OCTAVO_UI0_DIR
        OCTAVO_READERVIEW0_DIR)
  if(NOT DEFINED ${required_directory} OR
     NOT IS_DIRECTORY "${${required_directory}}")
    message(FATAL_ERROR "${required_directory} must name an existing directory")
  endif()
endforeach()

add_library(
  octavo
  SHARED
  octavo_android_port2_build.c
)

target_include_directories(
  octavo
  PRIVATE
  "${OCTAVO_REPO_ROOT}/code"
  "${OCTAVO_GROUND0_DIR}/code"
  "${OCTAVO_READER0_DIR}/code"
  "${OCTAVO_UI0_DIR}/code"
  "${OCTAVO_READERVIEW0_DIR}/code"
)

target_compile_features(octavo PRIVATE c_std_11)
target_compile_options(
  octavo
  PRIVATE
  -Wall
  -Wextra
  -Werror
  # Source packages are intentionally consumed as one application unity
  # translation unit. Their public/test surfaces leave bounded internal
  # helpers and one design-system-viewer diagnostic accumulator unused by
  # this focused Android milestone.
  -Wno-unused-function
  -Wno-unused-but-set-variable
)

find_library(android_library android REQUIRED)
find_library(log_library log REQUIRED)
target_link_libraries(octavo PRIVATE "${android_library}" "${log_library}")
