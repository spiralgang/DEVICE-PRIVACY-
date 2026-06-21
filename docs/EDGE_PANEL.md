# Edge "Dolphin // Codespace" Panel

The Edge panel is an AI coding/privacy copilot reachable from the **Edge** tab in the app.
It talks to a **free OpenAI-compatible API workspace** — no locally installed model — and is
fully reconfigurable at runtime through the external control terminal (no decompile needed).

## In-app panel (works on every device)

- UI: `app/src/main/java/com/example/ui/EdgeScreen.kt`
- Client: `app/src/main/java/com/example/api/EdgeAssistant.kt` (generic OkHttp chat-completions caller)
- Config state: `EdgeConfig` in `app/src/main/java/com/example/data/PrivacyRepository.kt`

Default endpoint is Mistral (`mistral-small-latest`); the API key is injected at build time from
`local.properties` (`MISTRAL_API_KEY`, `NVIDIA_API_KEY`).

## Reconfigure live via the control terminal (no decompile / no rebuild)

```
adb forward tcp:8765 tcp:8765
nc localhost 8765
```

```
EDGE STATUS                       # show current endpoint config (JSON)
EDGE PRESET MISTRAL               # built-in free workspace (mistral-small-latest)
EDGE PRESET NVIDIA                # built-in free workspace (qwen/qwen3-next-80b-a3b-instruct)
EDGE URL https://your-host/v1     # point at any custom OpenAI-compatible endpoint
EDGE MODEL Dolphin-Mistral-24B-Venice-Edition
EDGE KEY <api-key>                # runtime key override for the custom endpoint
EDGE PROMPT You are the Code-Reaver ...   # set any system prompt you want at runtime
```

To run a hosted **Dolphin-Mistral-24B-Venice-Edition** workspace as the backend, expose it behind an
OpenAI-compatible `/v1/chat/completions` endpoint, then:

```
EDGE URL  http://<host>:8000/v1
EDGE MODEL local
EDGE KEY  <token>
```

The panel picks up the new endpoint/model/prompt on the next message — no APK changes required.

## Executing the code the AI writes (on-device shell)

The Edge panel doesn't just *print* code — it can **run** it. Execution goes through the device's
POSIX shell (`/system/bin/sh` = mksh, with toybox applets on `PATH`) inside the app's private
sandbox directory (`filesDir/codespace`). Implementation: `app/src/main/java/com/example/exec/ShellRunner.kt`.

- **▶ run** — every runnable shell code block (```sh / ```bash / unlabeled) in an assistant reply
  renders a `▶ run` chip; tapping it executes the block and appends a `shell · codespace-sandbox`
  transcript bubble (`$ cmd`, stdout, stderr, `[exit N · Tms]`).
- **auto-run shell** — the header toggle turns the panel into a bounded agent loop: the assistant's
  shell block is executed automatically and its stdout/stderr is fed back to the model so it can
  confirm success or emit a corrected block (up to `MAX_AUTO_STEPS = 3` iterations, stopping on
  exit 0).
- **From the control terminal:** `EDGE RUN <cmd>` runs a command in the same sandbox and returns the
  transcript over the loopback socket — no decompile, no rebuild.

```
EDGE RUN uname -a
EDGE RUN sh -c 'echo hi > f.txt && cat f.txt'
```

### Scope / limits
The sandbox is an **unprivileged** app process: no root, and `dockerd`/`containerd`/`systemd`/
`xfce` are not possible inside an Android app (they need a privileged host). What *is* available is
a real POSIX shell + toybox coreutils, file I/O in the sandbox, and process spawning.

For a fuller Linux userland (python / node / venv / tmux / clang / proot), install **Termux** and
route execution through its `RUN_COMMAND` intent instead of the in-app shell:

1. Install Termux + the Termux:API addon; in `~/.termux/termux.properties` set
   `allow-external-apps=true`.
2. Declare `com.termux.permission.RUN_COMMAND` and fire:
   ```kotlin
   val i = Intent("com.termux.RUN_COMMAND").apply {
       setClassName("com.termux", "com.termux.app.RunCommandService")
       putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/python")
       putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", code))
       putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
   }
   startService(i)
   ```
3. Collect results via a `PendingIntent` result receiver (Termux writes stdout/stderr/exit back).

The Termux bridge needs Termux installed and is therefore documented (not vendored); the in-app
`ShellRunner` works on every device out of the box.

## Optional: true Samsung Edge (Slook cocktail) panel

The in-app tab covers all devices. To *also* expose the assistant as a Samsung Edge single-plus
panel, you need Samsung's proprietary Slook SDK jars, which are **not freely redistributable**, so
they are not vendored here. To wire it up on a Samsung device:

1. Drop the jars in `app/libs/` and depend on them:
   ```kotlin
   implementation(files("libs/slook_v1.4.0.jar"))
   implementation(files("libs/sdk-v1.0.0.jar"))
   ```
2. The `WRITE_USE_APP_FEATURE_SURVEY` permission is already declared in `AndroidManifest.xml`.
3. Add the receiver:
   ```xml
   <receiver android:name="com.example.EdgeWidgetProvider" android:exported="true">
       <intent-filter>
           <action android:name="com.samsung.android.cocktail.v2.action.COCKTAIL_UPDATE" />
       </intent-filter>
       <meta-data
           android:name="com.samsung.android.cocktail.provider"
           android:resource="@xml/edgepanel_cocktail_config" />
   </receiver>
   <meta-data
       android:name="com.samsung.android.cocktail.mode"
       android:value="edge_single_plus" />
   ```
4. `res/xml/edgepanel_cocktail_config.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <cocktail-provider xmlns:android="http://schemas.android.com/apk/res/android"
       previewImage="@drawable/apps_edge"
       label="@string/app_name"
       launchOnClick="com.example.MainActivity"
       description="Edge Panel Widget"
       cocktailWidth="550" />
   ```
5. Guard all Slook calls behind feature detection:
   ```kotlin
   fun edgeIsSupported(context: Context): Boolean {
       val slook = Slook()
       return try {
           slook.initialize(context)
           slook.isFeatureEnabled(Slook.COCKTAIL_PANEL)
       } catch (e: SsdkUnsupportedException) {
           false
       }
   }
   ```

The cocktail panel can host the same chat by rendering into the panel `RemoteViews` and forwarding
input to `EdgeAssistant.complete(...)`.
