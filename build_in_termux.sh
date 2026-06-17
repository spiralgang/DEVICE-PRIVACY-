#!/bin/bash
# ==============================================================================
# ⚡ GOD-MODE TERMUX COMPILATION ORCHESTRATOR ⚡
# One-shot instant activation script for building Android APKs directly on-device.
# Target: /storage/emulated/0/Download/privacy-simulator.zip
# ==============================================================================

set -e

echo "=================================================="
echo "🚀 INITIATING GOD-MODE TERMUX COMPILATION ENGINE"
echo "=================================================="

# 1. Setup local storage access
echo "[1/6] Ensuring Termux storage access..."
if [ ! -d "$HOME/storage" ]; then
    termux-setup-storage
    echo "⚠️ Storage permissions requested. If prompted, grant them and RE-RUN THIS SCRIPT."
    sleep 5
fi

# 2. Install core dependencies safely
echo "[2/6] Installing necessary Termux packages..."
pkg update -y
pkg install -y openjdk-17 unzip wget

# Variables Configuration
ZIP_FILE="$HOME/storage/downloads/privacy-simulator.zip"
BUILD_DIR="$HOME/privacy-simulator-build"
OUTPUT_APK="$HOME/storage/downloads/privacy-simulator-compiled.apk"
SDK_DIR="$HOME/android-sdk"

# 3. Environment Preparation
echo "[3/6] Cleaning up old build environment..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

if [ ! -f "$ZIP_FILE" ]; then
    echo "❌ ERROR: Cannot find project zip at $ZIP_FILE"
    echo "Please ensure the zip is exported to exactly this path."
    exit 1
fi

# 4. Extraction
echo "[4/6] Extracting project..."
unzip -q "$ZIP_FILE" -d "$BUILD_DIR"

# Navigate to the precise root directory containing gradlew
cd "$BUILD_DIR"
PROJECT_ROOT=$(find . -name "gradlew" -type f | head -n 1 | xargs dirname)
cd "$PROJECT_ROOT"

# 5. Gradle & SDK Injection prep
echo "[5/6] Prepping Gradle environment and Local SDK properties..."
chmod +x gradlew

# Auto-accept all Android SDK Licenses blindly for headless download via Gradle
mkdir -p "$SDK_DIR/licenses"
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK_DIR/licenses/android-sdk-license"
echo "84831b9409646a918e30573bab4c9c91346d8abd" >> "$SDK_DIR/licenses/android-sdk-license"
echo "d975f751698a77b662f1254ddbeed3901e976f5a" >> "$SDK_DIR/licenses/android-sdk-license"

# Force standard Android properties for SDK setup
cat <<EOF > local.properties
sdk.dir=$SDK_DIR
EOF

# Optional: Disable some caching / daemon logic structurally to prevent Termux memory limits
cat <<EOF > gradle.properties
org.gradle.jvmargs=-Xmx1536M
org.gradle.daemon=false
org.gradle.parallel=true
android.builder.sdkDownload=true
EOF

# 6. Compilation Engine Execution
echo "[6/6] Firing the Compiler Engine (!)..."
echo "⏳ First time builds will take several minutes to download the Android toolchain. Please be patient."
./gradlew assembleDebug --no-daemon

# 7. Payload Extraction
echo "=================================================="
echo "📦 EXCAVATING COMPILED APK PAYLOAD..."
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)

if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$OUTPUT_APK"
    echo "✅ MAXIMUM SUCCESS! APK successfully compiled and ported to:"
    echo "➡️ $OUTPUT_APK"
    echo "=================================================="
else
    echo "❌ FAILURE: Compilation terminated without generating the APK artifact."
    exit 1
fi
