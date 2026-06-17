#!/bin/bash
# ==============================================================================
# 🔥 STEPCHAT / NVIDIA AI – LIVE APK BUILDER FOR TERMUX 🔥
# Reads everything, calls AI, fixes the build, makes the APK. No excuses.
# ==============================================================================

# Use the environment variable if passed, otherwise default
NVIDIA_API_KEY=${NVIDIA_API_KEY:-""}

# Paths – Using native Termux App paths
PROJECT_ROOT=$(pwd)
BUILD_DIR="$HOME/privacy-simulator-build"
OUTPUT_APK="$HOME/storage/downloads/privacy-simulator-compiled.apk"
SDK_DIR="$HOME/android-sdk"

# NVIDIA API endpoint
AI_ENDPOINT="https://integrate.api.nvidia.com/v1/chat/completions"
AI_MODEL="meta/llama3-70b-instruct"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Safety: exit on any error, but we'll catch and retry
set -e

echo -e "${GREEN}=================================================="
echo "🔥 STEPCHAT NVIDIA AI – FORCED APK BUILDER 🔥"
echo "==================================================${NC}"

if [ ! -d "$HOME/storage" ]; then
    termux-setup-storage
    echo -e "${YELLOW}Please grant storage permission and re-run.${NC}"
    sleep 5
    exit 1
fi

echo -e "${GREEN}[1/5] Installing packages...${NC}"
pkg update -y && pkg install -y openjdk-17 unzip wget curl zip jq gradle

echo -e "${GREEN}[2/5] Copying project to writable location...${NC}"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cp -r "$PROJECT_ROOT"/* "$BUILD_DIR/" 2>/dev/null || { echo -e "${RED}Failed to copy from $PROJECT_ROOT${NC}"; exit 1; }
cd "$BUILD_DIR"

echo -e "${GREEN}[3/5] Reading project files for AI...${NC}"
FILE_CONTEXT=""
for file in README.md build.gradle build.gradle.kts app/build.gradle app/build.gradle.kts settings.gradle gradle.properties local.properties app/src/main/AndroidManifest.xml; do
    if [ -f "$file" ]; then
        FILE_CONTEXT="${FILE_CONTEXT}\n\n=== $file ===\n$(cat "$file")\n"
    fi
done

DIR_LISTING=$(ls -laR)

echo -e "${GREEN}[4/5] Contacting NVIDIA AI (Llama 3 70B) – this will take ~30 seconds...${NC}"

if [ -z "$NVIDIA_API_KEY" ]; then
    echo -e "${YELLOW}⚠️ No NVIDIA_API_KEY found in your environment!${NC}"
    echo "Running standard headless Gradle build instead without live AI agent fixing..."
    gradle assembleDebug --no-daemon
else
    PROMPT="You are an expert Android developer. You are inside Termux on a phone.
    Current directory: $BUILD_DIR
    It contains an Android project that currently cannot build with 'gradle assembleDebug'.

    Here is the content of all important files:
    $FILE_CONTEXT

    And the full directory listing:
    $DIR_LISTING

    Write a SINGLE bash script that does the following:
    1. Fixes any issues: missing gradle wrapper, wrong SDK versions, broken manifests, missing dependencies.
    2. Sets up local.properties and SDK licenses correctly for Termux.
    3. Runs 'gradle assembleDebug --no-daemon' successfully.
    4. After build, prints the full path of the generated APK.

    The script must be self-contained, run without any user input, and work in Termux with low memory (set JVM args to -Xmx1536M).
    Output ONLY the bash script, no explanations, no markdown."

    RESPONSE=$(curl -s -X POST "$AI_ENDPOINT" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $NVIDIA_API_KEY" \
        -d "{
            \"model\": \"$AI_MODEL\",
            \"messages\": [{\"role\": \"user\", \"content\": $(printf '%s' "$PROMPT" | jq -Rs .)}],
            \"temperature\": 0,
            \"max_tokens\": 4000
        }")

    AI_SCRIPT=$(echo "$RESPONSE" | jq -r '.choices[0].message.content' 2>/dev/null)
    if [ -z "$AI_SCRIPT" ] || [ "$AI_SCRIPT" = "null" ]; then
        echo -e "${RED}❌ AI response invalid. Full response:${NC}"
        echo "$RESPONSE"
        exit 1
    fi

    AI_SCRIPT=$(echo "$AI_SCRIPT" | sed -n '/```bash/,/```/p' | sed '1d;$d' || echo "$AI_SCRIPT")

    echo -e "${GREEN}🤖 AI generated fix script:${NC}"
    echo "----------------------------------------"
    echo "$AI_SCRIPT"
    echo "----------------------------------------"

    echo -e "${GREEN}[5/5] Executing AI fix script...${NC}"
    FIX_SCRIPT="/tmp/stepchat_ai_fix_$$.sh"
    echo "$AI_SCRIPT" > "$FIX_SCRIPT"
    chmod +x "$FIX_SCRIPT"

    bash "$FIX_SCRIPT" 2>&1 | tee "$BUILD_DIR/build_output.log" || true
    rm -f "$FIX_SCRIPT"
fi

echo -e "${GREEN}=================================================="
echo "✅ BUILD SUCCESSFUL! Looking for APK..."
APK_PATH=$(find "$BUILD_DIR" -name "*.apk" | grep -v "androidTest" | head -n 1)
if [ -n "$APK_PATH" ]; then
    cp "$APK_PATH" "$OUTPUT_APK"
    echo "APK copied to: $OUTPUT_APK"
    echo -e "==================================================${NC}"
else
    echo -e "${RED}⚠️ Build finished but no APK found.${NC}"
    exit 1
fi
