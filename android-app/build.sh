#!/usr/bin/env bash
# 一键构建 debug APK
set -euo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/jdk-17.0.20.1+1/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

if [ -x "$HOME/Library/Android/gradle-8.11.1/bin/gradle" ] && [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  GRADLE="$HOME/Library/Android/gradle-8.11.1/bin/gradle"
else
  GRADLE="./gradlew"
fi

echo "JAVA_HOME   = $JAVA_HOME"
echo "ANDROID_HOME= $ANDROID_HOME"
echo "gradle      = $GRADLE"

$GRADLE --console=plain assembleDebug

echo
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
