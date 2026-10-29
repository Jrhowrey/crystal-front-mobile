#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "============================================================================"
echo "  Crystal Front: Mobile Command v2.0 (AGDK + Vulkan TBDR + C++20 NDK)"
echo "  One-Click Self-Healing Android Studio & Gradle APK/AAB Compiler"
echo "============================================================================"

# 1. Clean any stale v1 duplicate package directory
if [ -d "app/src/main/java/com/tiberianmobile" ]; then
  echo "[CLEAN] Removing legacy v1 duplicate package folder app/src/main/java/com/tiberianmobile..."
  rm -rf "app/src/main/java/com/tiberianmobile"
fi
rm -rf "app/build"

# 2. Auto-detect Android Studio bundled JDK
if [ -z "$JAVA_HOME" ]; then
  if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
  elif [ -d "/opt/android-studio/jbr" ]; then
    export JAVA_HOME="/opt/android-studio/jbr"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

# 3. Auto-detect Android SDK
if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
[ -n "$ANDROID_HOME" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleRelease bundleRelease --stacktrace
