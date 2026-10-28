# LinkForty Android SDK

Native Android SDK for [LinkForty](https://github.com/LinkForty/core) — the open-source alternative to Branch.io, AppsFlyer OneLink, and Firebase Dynamic Links. Add deferred deep linking, mobile attribution, and smart link routing to your Android app. Self-hosted, privacy-first, no per-click pricing. 100% Kotlin with modern coroutines APIs.

[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.linkforty/sdk.svg)](https://search.maven.org/artifact/com.linkforty/sdk)

## Features

- **Deferred Deep Linking**: Match app installs to link clicks: exactly, from the Google Play install referrer (on a server that supports it), and otherwise from device signals (fingerprinting)
- **App Links**: Full support for Android App Links (verified HTTPS deep links)
- **Custom URL Schemes**: Handle custom app URL schemes
- **Event Tracking**: Track in-app events and conversions
- **Revenue Tracking**: Dedicated revenue tracking with `BigDecimal` amounts
- **Last-Click Attribution**: In-app events are automatically credited to the deep link that most recently opened the app
- **Screen-Flow Tracking**: Report screen views (manually or automatically via a Jetpack Navigation listener) to see what users do after clicking a link
- **Offline Support**: Events are stored on the device until the server has them (max 100), and sent on the next launch, when the app returns to the foreground, or after the next event that sends
- **Programmatic Link Creation**: Create short links directly from your app
- **Privacy-First**: Never reads the Google Advertising ID (GAID)
- **Kotlin-Native**: 100% Kotlin, modern coroutines API

## Requirements

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 1.9+
- JDK 17

## Installation

The SDK is published to Maven Central. Make sure `mavenCentral()` is in your repositories (new Android Studio projects have it in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.linkforty:sdk:1.8.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.linkforty:sdk:1.8.0'
}
```

## Quick Start

### 1. Initialize the SDK

In your `Application` class (register it with `android:name` on `<application>` in your manifest). `initialize()` is a `suspend` function, so call it from a coroutine:

```kotlin
import android.app.Application
import android.util.Log
import com.linkforty.sdk.LinkForty
import com.linkforty.sdk.models.LinkFortyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {
    // Any dispatcher works: callbacks registered and links handled before
    // initialize() finishes are kept until it does.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            try {
                val config = LinkFortyConfig(
                    baseURL = "https://api.linkforty.com", // the hosted service; your own server's URL if self-hosted
                    appToken = "at_a1b2c3d4...",           // required on the hosted service
                    debug = true,
                    attributionWindowHours = 168           // 7 days (the default)
                )
                val response = LinkForty.initialize(this@MyApplication, config)
                Log.d("LinkForty", "Install ID: ${response.installId}")
            } catch (e: Exception) {
                Log.e("LinkForty", "Initialization failed", e)
            }
        }
    }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `baseURL` | Yes | The LinkForty server the SDK talks to: `https://api.linkforty.com` on the hosted service, or your own server. Must be HTTPS (HTTP is allowed for `localhost`, `127.0.0.1`, `0.0.0.0` and `10.0.2.2`). |
| `appToken` | On the hosted service | Your workspace's public app token (`at_…`). It matches installs only to your workspace's links, puts organic installs in your workspace, and lets the SDK learn your link domains. Safe to ship in the app. A self-hosted Core server doesn't need it. |
| `apiKey` | Only for `createLink()` | An API key. It is a secret: leave it out unless the app creates links. |
| `debug` | No | Logs SDK activity to Logcat (default `false`). |
| `attributionWindowHours` | No | 1 to 2160 hours (default 168, 7 days). |

If `initialize()` can't report the install (for example, offline on the first launch), it throws and leaves the SDK uninitialized, keeping registered callbacks, so you can call it again.

### 2. Handle Deferred Deep Links (Install Attribution)

Register before or after `initialize()`. The callback runs once, on the launch that reports the install.

```kotlin
LinkForty.shared.onDeferredDeepLink { deepLinkData ->
    if (deepLinkData != null) {
        // User installed from a link - navigate to content
        Log.d("LinkForty", "Install attributed to: ${deepLinkData.shortCode}")
        Log.d("LinkForty", "UTM Source: ${deepLinkData.utmParameters?.source}")

        // Navigate to the right content
        deepLinkData.customParameters?.get("productId")?.let { productId ->
            navigateToProduct(productId)
        }
    } else {
        // Organic install - no attribution
        Log.d("LinkForty", "Organic install")
    }
}
```

The deferred deep link also carries how certain the match is: `confidenceScore`, `matchedFactors`, `attributionMethod` and `competingLinks`. `competingLinks` counts the other links that had a click from the install's network that also reached the confidence threshold; above 0, a tap on another device on the same network may be what led to this install. An app acting on a sensitive link, such as a sign-in or a gift, can treat that match as unverified:

```kotlin
LinkForty.shared.onDeferredDeepLink { deepLinkData ->
    if (deepLinkData != null && (deepLinkData.competingLinks ?: 0) > 0) {
        askUserToConfirm(deepLinkData) // e.g. "Did you open a gift link?"
    }
}
```

### 3. Handle Direct Deep Links (App Links)

First, configure App Links in your `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="go.yourdomain.com" />
    </intent-filter>
</activity>
```

Use the host your links are served from (add a `<data>` element for each custom domain). `android:exported="true"` is required for an activity with an intent filter when your app targets Android 12 (API 31) or higher. With `singleTask` (or `singleTop`), a link tapped while the app is running is delivered to the existing activity's `onNewIntent()`, so pass the URI from both `onCreate()` and `onNewIntent()`:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            LinkForty.shared.handleDeepLink(uri)
        }
    }
}

// Register the callback, before or after initialize()
LinkForty.shared.onDeepLink { uri, deepLinkData ->
    Log.d("LinkForty", "Deep link opened: $uri")
    deepLinkData?.let { data ->
        Log.d("LinkForty", "Link data: $data")
        data.deepLinkPath?.let { path ->
            navigateToPath(path)
        }
    }
}
```

A link handed to `handleDeepLink` before `initialize()` has finished, as when it launches the app, is held and delivered once it has.

Pass every URI that opens the app. `handleDeepLink` returns `true` for a LinkForty link, which it looks up on the server, and `false` for anything else, which it doesn't: a custom-scheme URI, or, once the SDK knows your workspace's link domains (it learns them with an `appToken`), a web URI on a host that is neither one of them nor the `baseURL` host. Until it knows them, or without an `appToken`, every web URI is looked up. `onDeepLink` receives both, the second with the URI's own parameters.

> **Server-side resolution:** When the SDK is initialized, LinkForty links are resolved via the server to provide enriched data including `deepLinkPath`, `appScheme`, and `linkId`. If the server is unreachable, the SDK falls back to local URL parsing, as it does for a URI that isn't a LinkForty link.

### 4. Track Events

The tracking calls are `suspend` functions: call them from a coroutine, such as `lifecycleScope.launch { ... }`. They throw `LinkFortyError.NotInitialized` until `initialize()` has finished.

```kotlin
// Track a simple event
LinkForty.shared.trackEvent("button_clicked")

// Track event with properties
LinkForty.shared.trackEvent(
    name = "purchase",
    properties = mapOf(
        "product_id" to "123",
        "amount" to 29.99,
        "currency" to "USD"
    )
)

// Track revenue
LinkForty.shared.trackRevenue(
    amount = BigDecimal("29.99"),
    currency = "USD",
    properties = mapOf("product_id" to "123")
)
```

Every event is automatically stamped with the deep link that most recently opened the app (last-click attribution), so the dashboard can show what users do *after* clicking a link. Events with no preceding deep-link open are reported as organic. No extra code is required.

### 5. Track Screen Views

Reporting screen views lets the dashboard build a per-link screen-flow funnel. Each `screen_view` carries the same last-click attribution stamp as other events.

**Automatic (Jetpack Navigation)** — attach `LinkFortyNavObserver` to your `NavController`. Each destination's `route` (Compose) or `label` (XML nav graph) is reported as it appears:

```kotlin
import com.linkforty.sdk.navigation.LinkFortyNavObserver

navController.addOnDestinationChangedListener(LinkFortyNavObserver())
```

> `LinkFortyNavObserver` requires `androidx.navigation`, which your app already has if it uses Jetpack Navigation. The SDK depends on it only as `compileOnly`, so apps that don't use Navigation aren't affected.

**Manual** — call it yourself (e.g. for screens not driven by a `NavController`):

```kotlin
LinkForty.shared.trackScreenView("ProductDetail")
```

### 6. Create Links Programmatically

```kotlin
val result = LinkForty.shared.createLink(
    CreateLinkOptions(
        deepLinkParameters = mapOf("route" to "VIDEO_VIEWER", "id" to "vid123"),
        title = "Check this out!",
        utmParameters = UTMParameters(source = "app", campaign = "share")
    )
)

Log.d("LinkForty", "Share this link: ${result.url}")
// e.g., "https://go.yourdomain.com/tmpl/abc123"
```

> **Note:** `createLink` is a `suspend` function and requires an `apiKey` in `LinkFortyConfig` (it throws `LinkFortyError.MissingApiKey` without one). It posts to `POST /api/sdk/v1/links`, which the hosted service provides. On a server without that endpoint it falls back to `POST /api/links`; LinkForty Core's `/api/links` requires an `originalUrl`, which `CreateLinkOptions` doesn't send, so against a self-hosted Core server create links from your backend instead. See [API Reference](API.md#createlinkoptions) for all options.

## Advanced Usage

### Self-Hosted LinkForty Core

Point `baseURL` at your Core server. A stock Core server needs no `appToken`, and no `apiKey` unless you use `createLink` (see the note above):

```kotlin
val config = LinkFortyConfig(
    baseURL = "https://links.yourcompany.com"
)
LinkForty.initialize(context, config) // from a coroutine
```

### Custom Attribution Window

```kotlin
val config = LinkFortyConfig(
    baseURL = "https://go.yourdomain.com",
    attributionWindowHours = 24 // 1 day instead of default 7 days
)
```

### Retrieve Install Data

```kotlin
LinkForty.shared.getInstallData()?.let { data ->
    Log.d("LinkForty", "Short code: ${data.shortCode}")
    Log.d("LinkForty", "UTM source: ${data.utmParameters?.source}")
}

LinkForty.shared.getInstallId()?.let { id ->
    Log.d("LinkForty", "Install ID: $id")
}
```

### Event Queue Management

`trackEvent` stores each event on the device and returns once it is sent or queued; an event that can't be sent now is sent later without your app doing anything. These calls are for inspecting or controlling the queue.

```kotlin
// Check queued events count
val count = LinkForty.shared.queuedEventCount

// Manually flush event queue
LinkForty.shared.flushEvents()

// Clear event queue
LinkForty.shared.clearEventQueue()
```

### Clear Data (for testing / GDPR)

```kotlin
LinkForty.shared.clearData()

// Reset SDK to uninitialized state
LinkForty.shared.reset()
```

## App Links Setup

### 1. Digital Asset Links File

Android verifies App Links against a Digital Asset Links file on each link host:
`https://go.yourdomain.com/.well-known/assetlinks.json`

LinkForty serves this file for you: on the hosted service, once your Android package name and SHA-256 certificate fingerprints are entered in your app configuration; on LinkForty Core, from the `ANDROID_PACKAGE_NAME` and `ANDROID_SHA256_FINGERPRINTS` (comma-separated) environment variables. Until they are set, the file returns a 404 and the links open in the browser.

Which fingerprints to list:
- **Google Play builds:** if Play App Signing is on (the default for new apps), Google re-signs your app, so use the **app signing key** SHA-256 from Play Console (App integrity > App signing), not your upload key.
- **Local and sideloaded builds:** the certificate you sign them with, for example the debug keystore: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`.

The file it serves looks like this:
```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.yourcompany.yourapp",
    "sha256_cert_fingerprints": ["YOUR_SHA256_FINGERPRINT"]
  }
}]
```

### 2. Configure AndroidManifest.xml

Add an intent filter to your main activity (see Quick Start section).

### 3. Test App Links

```bash
# Check that the file is served
curl https://go.yourdomain.com/.well-known/assetlinks.json

# Android 12+: re-run verification and show the result for each host ("verified")
adb shell pm verify-app-links --re-verify com.yourcompany.yourapp
adb shell pm get-app-links com.yourcompany.yourapp

# Open a link in the app
adb shell am start -a android.intent.action.VIEW \
  -d "https://go.yourdomain.com/abc123" \
  com.yourcompany.yourapp
```

## Privacy & Security

### Privacy-First Design

- **No advertising ID**: The SDK never reads the Google Advertising ID (GAID). `initialize()` takes an optional `deviceId`, sent only if your app passes one.
- **How installs are matched**: For an install from Google Play, by the Play install referrer, which names the link click exactly; otherwise from device signals (fingerprinting), which is probabilistic.
- **Install ID**: The server assigns an install ID on the first launch. The SDK stores it on the device (in `SharedPreferences`, with the install's attribution, the last deep link that opened the app, and any queued events) and sends it with every event, until `clearData()` or the app is uninstalled.
- **User Control**: `clearData()` deletes the install ID, the install's attribution, the last deep link and queued events from the device; the next launch reports a new install.

### Data Sent

- **Install report (first launch):** time zone, language, screen resolution (pixels), platform and Android version, app version, a User-Agent built from the app name, app version and Android version, the Play install referrer (Google Play installs), the `appToken` if set, the attribution window, the SDK name and version, and `deviceId` if your app passes one. The server also sees the request's IP address, which fingerprint matching uses.
- **Deep link lookup:** the link's path, time zone, language, screen resolution, platform and Android version.
- **Events:** the install ID, event name and properties, a timestamp, the link (and click) the event is credited to, a session ID, an event ID used to store a retried event once, and the SDK name and version.

### Permissions

The SDK's manifest adds `android.permission.INTERNET`, and the Play Install Referrer library it depends on adds `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`. Both are normal permissions: Android grants them without asking the user.

### HTTPS Required

The SDK enforces HTTPS for all API endpoints (except `localhost`, `127.0.0.1`, `0.0.0.0` and `10.0.2.2`, for development).

## Testing

### Unit Tests

```bash
./gradlew sdk:testDebugUnitTest
```

### Lint

```bash
./gradlew sdk:lintDebug
```

### Build

```bash
./gradlew sdk:assembleRelease
```

## Documentation

- [API Reference](API.md)
- [LinkForty Docs](https://docs.linkforty.com)

## Requirements

### Backend

This SDK requires a running LinkForty backend:
- **LinkForty Core** (open source): Self-host for free
- **The hosted LinkForty service** ([linkforty.com](https://linkforty.com)): managed, nothing to run

See: https://github.com/LinkForty/core

## Support

- **Documentation**: [docs.linkforty.com](https://docs.linkforty.com)
- **Issues**: [GitHub Issues](https://github.com/LinkForty/mobile-sdk-android/issues)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

## License

LinkForty Android SDK is available under the MIT license. See [LICENSE](LICENSE) for more info.

## Other SDKs

| Platform | Package |
|----------|---------|
| React Native | [`@linkforty/mobile-sdk-react-native`](https://github.com/LinkForty/mobile-sdk-react-native) |
| Expo | [`@linkforty/mobile-sdk-expo`](https://github.com/LinkForty/mobile-sdk-expo) |
| iOS (Swift) | [LinkFortySDK](https://github.com/LinkForty/mobile-sdk-ios) |
| Flutter | [`linkforty_flutter`](https://github.com/LinkForty/mobile-sdk-flutter) |

## Related Projects

- [LinkForty Core](https://github.com/LinkForty/core) — open-source self-hosted deep linking engine
- [LinkForty](https://linkforty.com) — the hosted service, with a dashboard
