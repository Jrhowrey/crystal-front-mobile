#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

if [ -z "$JAVA_HOME" ]; then
  if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  elif [ -d "/opt/android-studio/jbr" ]; then
    export JAVA_HOME="/opt/android-studio/jbr"
  fi
fi
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p gradle/wrapper
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "[Gradle Wrapper] Downloading official gradle-wrapper.jar..."
  curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -o "gradle/wrapper/gradle-wrapper.jar"
fi

exec java -Xmx64m -Xms64m -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
