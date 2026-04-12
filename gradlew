#!/bin/sh
# Gradle start up script for POSIX compatible shells.
DIRNAME=$(dirname "$0")
exec "$DIRNAME/gradle/wrapper/gradlew" "$@" 2>/dev/null || \
  gradle "$@" 2>/dev/null || \
  echo "Run: Open in Android Studio and click Sync Project with Gradle Files"
