# WebView Instrumentation

Status: development

Hands the RUM session to web content running inside an Android `WebView`, so the browser RUM SDK
on those pages reports into the same session as the native app. No app code changes are needed.

## How it works

At build time, the `webview-agent` Byte Buddy plugin replaces these calls in the app's own classes
with `WebViewSubstitutions`:

* `WebView.loadUrl(String)`
* `WebView.loadUrl(String, Map)`
* `WebView.postUrl(String, byte[])`
* `WebView.loadDataWithBaseURL(...)` (uses the base URL)

Calls on `WebView` subclasses are covered too. Before the first page loads, the WebView is attached
to, which needs the `DOCUMENT_START_SCRIPT` and `WEB_MESSAGE_LISTENER` features (checked through
`androidx.webkit`). There is no `attach()` to call and nothing to detach: WebViews are tracked
weakly.

### Session context

A document-start script sets `window.__VUNET_CTX__` before any script of the page runs:

```json
{
  "v": 1,
  "sessionId": "<32 hex>",
  "sessionStartTs": 1700000000000,
  "sampled": true,
  "app": { "id": "com.example.bank", "version": "2.1.0" },
  "device": { "manufacturer": "Google", "model": "Pixel 8", "os": "Android", "osVersion": "14" },
  "host": { "platform": "android", "webviewId": "<uuid>", "parentViewName": "DashboardActivity" }
}
```

Fields may be added within `v: 1`; renaming or removing one needs a new version. The context lives
in page memory only: nothing is written to cookies or web storage, so a reload or a new launch never
picks up a stale session.

### Bridge

A `VunetBridge` object is available to the page. Messages are JSON strings; only the main frame is
answered:

```js
VunetBridge.onmessage = (e) => { const { context } = JSON.parse(e.data); /* adopt */ };
VunetBridge.postMessage(JSON.stringify({ type: "getContext" }));
VunetBridge.postMessage(JSON.stringify({ type: "brumReady", version: "3.2.0" }));
```

Both requests are answered with `{"type":"context","context":{...}}`.

### Session rotation

When the session rotates, later navigations get the new context, and the loaded page has
`window.__VUNET_CTX__` replaced, `window.__VUNET_BRIDGE__.onContext(ctx)` called if defined, and a
`vunet:context` window event dispatched with the context as `detail`. An activity resume reads the
session, so an inactivity rotation that is due is pushed then.

### Scope

The script and the bridge are limited to the origins the app loaded (plus `allowedHosts`), so a
page navigated to another site does not see them. Only `http`/`https` URLs are handled. If anything
fails, the page loads exactly as it would without the instrumentation.

### Events

| Event | Attributes |
|---|---|
| `webview.opened` | `webview.id`, `webview.origin`, `parent.view.name`, `webview.context.supported` |
| `webview.brum_attached` | `webview.id`, `brum.version` (once per WebView and session) |

## Limitations

* WebViews without `DOCUMENT_START_SCRIPT` and `WEB_MESSAGE_LISTENER` get no context; they are
  reported with `webview.context.supported=false`.
* Calls inside third-party libraries (for example a payment SDK's own WebView) are not rewritten.
* Browser RUM must read `window.__VUNET_CTX__` and listen for `onContext` to join the session.
* `sampled` is always `true`: every native session is recorded.
* User id is not part of the context yet, and a WebView renderer crash is not reported yet.

## Installation

### Applying the Byte Buddy plugin

```kotlin
plugins {
  id("net.bytebuddy.byte-buddy-gradle-plugin") version "LATEST_VERSION"
}
```

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:webview-library:LATEST_VERSION")
byteBuddy("io.opentelemetry.android.instrumentation:webview-agent:LATEST_VERSION")
```

## Configuration

When using `android-agent`:

```kotlin
OpenTelemetryRumInitializer.initialize(context = applicationContext) {
    instrumentations {
        webView {
            // Default: the origin of every http(s) page the app loads. List every host a
            // journey redirects through, such as an OAuth host.
            allowedHosts("id.example.com", "oauth.example.com")
        }
    }
}
```
