cmake_minimum_required(VERSION 3.22.1)
project(axioma LANGUAGES CXX)

add_library(axioma SHARED calculator.cpp decimal.cpp expression.cpp approximation.cpp
    big_integer.cpp rational.cpp exact_math.cpp symbolic_math.cpp calculation_result.cpp native_bridge.cpp)
target_compile_features(axioma PRIVATE cxx_std_20)
set_target_properties(axioma PROPERTIES CXX_EXTENSIONS OFF)
