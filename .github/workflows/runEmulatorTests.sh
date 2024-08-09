#!/bin/zsh

set -o pipefail

runTests() {
    ./gradlew connectedPlayDebugAndroidTest connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.notAnnotation=de.test.podcini.IgnoreOnCi
}

# Retry tests to make them less flaky
runTests || runTests || runTests
