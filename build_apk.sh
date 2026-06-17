#!/bin/bash
# ==============================================================================
# TERMUX COMPILATION ORCHESTRATOR
# Comprehensive script to setup environment and build APK in Termux
# Integrates build.nvidia.com AI for interactive build troubleshooting
# ==============================================================================

# DO NOT use set -e to allow the loop to handle errors gracefully

echo "=================================================="
echo "🚀 INITIATING TERMUX COMPILATION ENGINE"
echo "=================================================="

# 1. Setup local storage access
echo "[1/6] Ensuring Termux storage access..."
if [ ! -d "$HOME/storage" ]; then
    termux-setup-storage
    echo "⚠️ Storage permissions requested. If prompted, grant them and RE-RUN THIS SCRIPT."
    sleep 5
fi

# 2. Install core dependencies
echo "[2/6] Installing necessary Termux packages..."
pkg update -y
pkg install -y openjdk-17 wget unzip curl jq

# The current directory is assumed to be the project root
PROJECT_ROOT=$(pwd)
OUTPUT_APK="$HOME/storage/downloads/compiled-app.apk"
SDK_DIR="$HOME/android-sdk"

# 3. Prepping Gradle environment
echo "[3/6] Prepping Gradle environment and Local SDK properties..."
if [ -f "gradlew" ]; then
    chmod +x gradlew
else
    echo "❌ ERROR: gradlew not found in current directory. Please run this script from the project root."
    exit 1
fi

# Auto-accept Android SDK Licenses for headless download via Gradle
mkdir -p "$SDK_DIR/licenses"
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK_DIR/licenses/android-sdk-license"
echo "84831b9409646a918e30573bab4c9c91346d8abd" >> "$SDK_DIR/licenses/android-sdk-license"
echo "d975f751698a77b662f1254ddbeed3901e976f5a" >> "$SDK_DIR/licenses/android-sdk-license"

# Force standard Android properties for SDK setup
cat <<EOF > local.properties
sdk.dir=$SDK_DIR
EOF

# Optimize gradle properties for Termux memory limits
cat <<EOF > gradle.properties
org.gradle.jvmargs=-Xmx1536m
org.gradle.daemon=false
org.gradle.parallel=true
android.builder.sdkDownload=true
EOF

# 4. Compilation Engine Execution & Interactive AI Troubleshooting
echo "[4/6] Firing the Compiler Engine with Interactive AI..."

MAX_RETRIES=5
RETRY_COUNT=0
BUILD_SUCCESS=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    echo "⏳ Running Gradle Build (Attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)..."
    
    # Run the build and log output
    ./gradlew assembleDebug --no-daemon > build_output.log 2>&1
    BUILD_EXIT_CODE=$?
    
    if [ $BUILD_EXIT_CODE -eq 0 ]; then
        BUILD_SUCCESS=true
        echo "✅ Build completed successfully."
        break
    else
        echo "❌ Build failed with exit code $BUILD_EXIT_CODE."
        
        if [ -z "$NVIDIA_API_KEY" ]; then
            echo "⚠️ NVIDIA_API_KEY environment variable is not set. Cannot use Nvidia AI to troubleshoot."
            echo "Please set it: export NVIDIA_API_KEY='your_api_key_here'"
            echo "Dumping the end of the build log:"
            tail -n 30 build_output.log
            echo "Entering interactive shell for manual fixes. Type 'exit' to retry, or Ctrl+C to abort."
            bash
            RETRY_COUNT=$((RETRY_COUNT + 1))
            continue
        fi
        
        echo "🤔 Contacting build.nvidia.com AI to analyze build failure..."
        
        # Prepare the tail of the error log for the JSON payload
        ERROR_LOG=$(tail -n 120 build_output.log | jq -Rs .)
        
        # Request response from Nvidia AI (Llama 3 70B Instruct or similar API)
        AI_RESPONSE=$(curl -s -X POST "https://integrate.api.nvidia.com/v1/chat/completions" \
            -H "Authorization: Bearer $NVIDIA_API_KEY" \
            -H "Content-Type: application/json" \
            -d "{
                \"model\": \"meta/llama3-70b-instruct\",
                \"messages\": [
                    {
                        \"role\": \"system\",
                        \"content\": \"You are an elite autonomous Android build troubleshooter operating within a Termux environment. The compilation engine encountered a Gradle failure. Briefly describe the core issue based on the provided logs, what files need edits, or what bash script commands need running. Provide explicit step-by-step instructions. Keep it actionable and concise.\"
                    },
                    {
                        \"role\": \"user\",
                        \"content\": \"The build failed with this output:\\n\\n${ERROR_LOG}\"
                    }
                ],
                \"max_tokens\": 1024,
                \"temperature\": 0.2
            }")
            
        AI_SUGGESTION=$(echo "$AI_RESPONSE" | jq -r '.choices[0].message.content')
        
        echo -e "\n=================================================="
        echo "🤖 AI TROUBLESHOOTER NVIDIA DIAGNOSTIC:"
        echo "=================================================="
        echo -e "$AI_SUGGESTION"
        echo "=================================================="
        
        echo "🔧 Interactive AI Mode Active: Entering an interactive rescue shell."
        echo "Follow the AI's instructions above to fix the error."
        echo "When ready to retry the compile engine, type 'exit'."
        echo "To abort entirely, use Ctrl+C."
        
        bash
        
        RETRY_COUNT=$((RETRY_COUNT + 1))
    fi
done

if [ "$BUILD_SUCCESS" = false ]; then
    echo "❌ Maximum retries reached. Compilation could not be completed."
    exit 1
fi

# 5. Payload Extraction
echo "[5/6] Extracting Compiled APK..."
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)

if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$OUTPUT_APK"
    echo "=================================================="
    echo "✅ MAXIMUM SUCCESS! APK successfully compiled and ported to:"
    echo "➡️ $OUTPUT_APK"
    echo "=================================================="
else
    echo "❌ FAILURE: Compilation terminated without generating the APK artifact."
    exit 1
fi
