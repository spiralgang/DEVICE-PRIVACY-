# Privacy 

An Android application focused on device privacy management and hardware footprint masking.

## Building via Termux (On-Device)

This project includes a fully automated Bash script (`build_apk.sh`) designed to install dependencies, configure the Android SDK, and compile the APK directly on an Android device using Termux.

### Prerequisites
- [Termux](https://termux.dev/) installed on your Android device.
- (Optional) An Nvidia API key exported as `NVIDIA_API_KEY` in your Termux environment for integrated AI build troubleshooting.

### Build Steps
1. Open Termux.
2. Ensure this project directory is accessible. If exported as a ZIP, extract it to a known directory (e.g., `~/storage/downloads/privacy-simulator`).
3. Run the orchestration script:
   ```bash
   cd /path/to/project
   bash build_apk.sh
   ```
4. The script will automatically:
   - Request core Android storage permissions.
   - Install required packages: `openjdk-17`, `wget`, `unzip`, `curl`, and `jq`.
   - Setup the local Android SDK and accept required licenses.
   - Compile the application using the Gradle daemon.
   - Provide interactive troubleshooting if the build fails, leveraging the Nvidia AI API.
5. Upon successful compilation, the final APK will be extracted to `/storage/emulated/0/Download/compiled-app.apk`.

## Features
- **Target App Selector:** Granularly manage which applications receive masked identities.
- **Hardware Masking:** Configure simulated system properties (Model, Brand, Board, Manufacturer) to mimic different device footprints.
- **Privacy Dashboard:** Monitor application identifier queries.

## Troubleshooting Termux Environments
If you encounter `pip` or `python` package issues (such as `BackendUnavailable: Cannot import 'mesonpy'`) while using external Termux AI tools, verify that your Termux package repositories are correctly configured using the `termux-change-repo` command and ensure you are strictly utilizing `pkg install` for core compiler dependencies before dropping into Python/pip environments.