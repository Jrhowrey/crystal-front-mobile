cmake_minimum_required(VERSION 3.18.1)
project("crystalfront_native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
    include(CheckCXXCompilerFlag)
    check_cxx_compiler_flag("-march=armv8.2-a+lse+fp16+dotprod" HAS_ARMV82_LSE)
    if(HAS_ARMV82_LSE)
        add_compile_options(-O3 -fvisibility=hidden -fno-exceptions -fno-rtti -march=armv8.2-a+lse+fp16+dotprod)
    else()
        add_compile_options(-O3 -fvisibility=hidden -fno-exceptions -fno-rtti)
    endif()
else()
    add_compile_options(-O3 -fvisibility=hidden -fno-exceptions -fno-rtti)
endif()

add_library(crystalfront_native SHARED
    crystal_front_native_engine.cpp
)

find_library(log-lib log)

target_link_libraries(crystalfront_native
    ${log-lib}
    dl
)
