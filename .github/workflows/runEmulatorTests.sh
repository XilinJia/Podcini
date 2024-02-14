#!/bin/zsh

set -o pipefail

runTests() {
    ./gradlew connectedPlayDebugAndroidTest connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.notAnnotation=de.test.podvinci.IgnoreOnCi
}

# Retry tests to make them less flaky
runTests || runTests || runTests
