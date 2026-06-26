#!/usr/bin/env python3
"""
Remote repackaging build worker for the DEVICE//PRIVACY "Forge" feature.

Runs on any online Linux box (Google Cloud Shell, a GCE/Compute Engine VM, etc.)
that has a JDK, apktool, and the Android build-tools (apksigner, zipalign) on PATH.
The Android app POSTs a target APK plus the identifiers to spoof; this worker
decompiles it, injects the spoofed values into the smali, rebuilds, signs with a
local debug key, and returns the signed clone APK.

Endpoint:
    POST /build   (multipart/form-data)
        apk        : the target .apk file
        androidId  : (optional) value to force for Settings.Secure.ANDROID_ID
        imei       : (optional) value to force for TelephonyManager.getImei/getDeviceId
        mac        : (optional) value to force for WifiInfo.getMacAddress
    -> 200 application/vnd.android.package-archive : the signed clone
    -> 4xx/5xx text/plain : failure reason

Quick start (Cloud Shell):
    pip install flask
    sudo apt-get install -y apktool zipalign apksigner default-jdk
    python3 server.py            # listens on 0.0.0.0:8080
    # expose with: gcloud cloud-shell ... / `cloudshell web-preview` / an SSH tunnel
"""
import os
import re
import shutil
import subprocess
import tempfile

from flask import Flask, request, send_file, Response

app = Flask(__name__)

ANDROID_ID_CALL = ("Landroid/provider/Settings$Secure;->getString("
                   "Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;")
IMEI_CALLS = [
    "Landroid/telephony/TelephonyManager;->getImei()Ljava/lang/String;",
    "Landroid/telephony/TelephonyManager;->getImei(I)Ljava/lang/String;",
    "Landroid/telephony/TelephonyManager;->getDeviceId()Ljava/lang/String;",
    "Landroid/telephony/TelephonyManager;->getDeviceId(I)Ljava/lang/String;",
]
MAC_CALL = "Landroid/net/wifi/WifiInfo;->getMacAddress()Ljava/lang/String;"
MOVE_RESULT = re.compile(r"^\s*move-result-object\s+([vp]\d+)\s*$")


def _escape(value):
    return value.replace("\\", "\\\\").replace('"', '\\"')


def _inject_after_result(lines, call_idx, value):
    j = call_idx + 1
    while j < len(lines) and lines[j].strip() == "":
        j += 1
    if j >= len(lines):
        return False
    m = MOVE_RESULT.match(lines[j])
    if not m:
        return False
    reg = m.group(1)
    indent = lines[j][:len(lines[j]) - len(lines[j].lstrip())]
    lines.insert(j + 1, "")
    lines.insert(j + 2, f'{indent}const-string {reg}, "{_escape(value)}"')
    return True


def _preceded_by_android_id(lines, idx):
    for j in range(idx, max(idx - 7, -1), -1):
        if '"android_id"' in lines[j]:
            return True
    return False


def patch_smali_tree(src_dir, android_id, imei, mac):
    patched = 0
    for root, _, files in os.walk(src_dir):
        for name in files:
            if not name.endswith(".smali"):
                continue
            path = os.path.join(root, name)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                lines = f.read().split("\n")
            changed = False
            i = 0
            while i < len(lines):
                line = lines[i]
                if android_id and ANDROID_ID_CALL in line and _preceded_by_android_id(lines, i):
                    if _inject_after_result(lines, i, android_id):
                        patched += 1; changed = True; i += 1
                elif imei and any(c in line for c in IMEI_CALLS):
                    if _inject_after_result(lines, i, imei):
                        patched += 1; changed = True; i += 1
                elif mac and MAC_CALL in line:
                    if _inject_after_result(lines, i, mac):
                        patched += 1; changed = True; i += 1
                i += 1
            if changed:
                with open(path, "w", encoding="utf-8") as f:
                    f.write("\n".join(lines) + "\n")
    return patched


def run(cmd, cwd=None):
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)}\n{p.stdout}\n{p.stderr}")
    return p.stdout


def ensure_keystore(path):
    if os.path.exists(path):
        return
    run(["keytool", "-genkeypair", "-keystore", path, "-alias", "d",
         "-storepass", "android", "-keypass", "android", "-keyalg", "RSA",
         "-keysize", "2048", "-validity", "10000", "-dname", "CN=DevicePrivacy"])


@app.route("/build", methods=["POST"])
def build():
    if "apk" not in request.files:
        return Response("missing 'apk' file", status=400, mimetype="text/plain")
    android_id = request.form.get("androidId")
    imei = request.form.get("imei")
    mac = request.form.get("mac")
    if not (android_id or imei or mac):
        return Response("no identifiers to spoof", status=400, mimetype="text/plain")

    work = tempfile.mkdtemp(prefix="forge-")
    try:
        target = os.path.join(work, "target.apk")
        request.files["apk"].save(target)
        src = os.path.join(work, "src")
        run(["apktool", "d", "-f", "-o", src, target])

        patched = patch_smali_tree(src, android_id, imei, mac)
        if patched == 0:
            return Response("no patchable identifier reads found", status=422, mimetype="text/plain")

        rebuilt = os.path.join(work, "rebuilt.apk")
        run(["apktool", "b", src, "-o", rebuilt])

        aligned = os.path.join(work, "aligned.apk")
        run(["zipalign", "-f", "4", rebuilt, aligned])

        keystore = os.path.join(work, "debug.keystore")
        ensure_keystore(keystore)
        signed = os.path.join(work, "clone.apk")
        run(["apksigner", "sign", "--ks", keystore, "--ks-pass", "pass:android",
             "--key-pass", "pass:android", "--out", signed, aligned])

        return send_file(signed, mimetype="application/vnd.android.package-archive",
                         as_attachment=True, download_name="clone.apk")
    except Exception as e:
        return Response(f"build failed: {e}", status=500, mimetype="text/plain")
    finally:
        shutil.rmtree(work, ignore_errors=True)


@app.route("/health")
def health():
    return "ok"


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
