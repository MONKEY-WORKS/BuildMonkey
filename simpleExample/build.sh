#!/bin/bash
./gradlew clean
./gradlew mirrorP2Repository --refresh-dependencies
./gradlew mavenizeP2Repository
./gradlew createTargetPlatform
./gradlew build