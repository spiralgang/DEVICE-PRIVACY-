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
