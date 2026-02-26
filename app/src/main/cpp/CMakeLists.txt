cmake_minimum_required(VERSION 3.22.1)

project("sary360_native")

# Set the path to OpenCV SDK
# This will be passed from Gradle: -DOpenCV_DIR=...
find_package(OpenCV REQUIRED)

add_library(sary360_native SHARED
    native-lib.cpp
)

target_link_libraries(sary360_native
    ${OpenCV_LIBS}
    android
    log
)
