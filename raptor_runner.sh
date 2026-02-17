#!/bin/bash

# ==========================================
# 🦖 RAPTOR MINI: AUTOMATED SAFETY PROTOCOL
# ==========================================
# Usage: ./raptor_runner.sh [mode]
# Modes:
#   check   -> Runs Unit Tests (Safety Check)
#   lint    -> Runs Ktlint Check (Style Check)
#   fix     -> Runs Ktlint Format (Auto-fix Style)
#   full    -> Runs Tests + Lint (Deep Validation)
# ==========================================

MODE=$1

function print_header() {
    echo -e "\n🦖 \033[1;32mRAPTOR AGENT:\033[0m $1\n"
}

function run_tests() {
    print_header "Running Unit Tests (Safety Baseline)..."
    # Runs standard Debug Unit Tests as per best practices [cite: 325]
    ./gradlew testDebugUnitTest
    
    if [ $? -eq 0 ]; then
        echo -e "\n✅ \033[1;32mTESTS PASSED. Code is stable.\033[0m"
        return 0
    else
        echo -e "\n❌ \033[1;31mTESTS FAILED. Aborting operation.\033[0m"
        return 1
    fi
}

function run_lint() {
    print_header "Scanning Code Style (Ktlint)..."
    # Checks for style violations [cite: 134]
    ./gradlew ktlintCheck
    
    if [ $? -eq 0 ]; then
        echo -e "\n✅ \033[1;32mSTYLE CHECK PASSED.\033[0m"
        return 0
    else
        echo -e "\n⚠️ \033[1;33mSTYLE ISSUES FOUND.\033[0m"
        return 1
    fi
}

function run_format() {
    print_header "Auto-Fixing Style (Ktlint Format)..."
    # Automatically fixes formatting issues [cite: 136]
    ./gradlew ktlintFormat
    echo -e "\n✨ \033[1;32mFORMATTING APPLIED.\033[0m"
}

#Dispatcher
case $MODE in
    "check")
        run_tests
        ;;
    "lint")
        run_lint
        ;;
    "fix")
        run_format
        ;;
    "full")
        run_format
        run_tests
        if [ $? -eq 0 ]; then
            run_lint
        fi
        ;;
    *)
        echo "Usage: ./raptor_runner.sh [check|lint|fix|full]"
        exit 1
        ;;
esac