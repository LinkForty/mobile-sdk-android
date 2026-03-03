# LinkForty Android SDK

Native Android SDK for [LinkForty](https://github.com/LinkForty/core) — the open-source alternative to Branch.io, AppsFlyer OneLink, and Firebase Dynamic Links. Add deferred deep linking, mobile attribution, and smart link routing to your Android app. Self-hosted, privacy-first, no per-click pricing. 100% Kotlin with modern coroutines APIs.

[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.linkforty/sdk.svg)](https://search.maven.org/artifact/com.linkforty/sdk)

## Features

- **Deferred Deep Linking**: Match app installs to link clicks using privacy-compliant fingerprinting
- **App Links**: Full support for Android App Links (verified HTTPS deep links)
- **Custom URL Schemes**: Handle custom app URL schemes
- **Event Tracking**: Track in-app events and conversions
- **Revenue Tracking**: Dedicated revenue tracking with BigDecimal precision
- **Offline Support**: Queue events when offline with automatic retry (max 100 events)
- **Programmatic Link Creation**: Create short links directly from your app
- **Privacy-First**: No GAID collection by default
- **Kotlin-Native**: 100% Kotlin, modern coroutines API

## Requirements

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 1.9+
- JDK 17

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.linkforty:sdk:1.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.linkforty:sdk:1.1.0'
}
```

## Quick Start

### 1. Initialize the SDK

In your `Application` class or main `Activity`:

```kotlin
import com.linkforty.sdk.LinkForty
import com.linkforty.sdk.models.LinkFortyConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            try {
                val config = LinkFortyConfig(
                    baseURL = "https://go.yourdomain.com",
                    apiKey = "your-api-key", // Optional for self-hosted
                    debug = true,
                    attributionWindowHours = 168 // 7 days
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

### 2. Handle Deferred Deep Links (Install Attribution)

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

### 3. Handle Direct Deep Links (App Links)

First, configure App Links in your `AndroidManifest.xml`:

```xml
<activity android:name=".MainActivity">
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

Then handle incoming intents:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            LinkForty.shared.handleDeepLink(uri)
        }
    }
}

// Register callback
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

> **Server-side resolution:** When the SDK is initialized, deep links are automatically resolved via the server to provide enriched data including `deepLinkPath`, `appScheme`, and `linkId`. If the server is unreachable, the SDK falls back to local URL parsing.

### 4. Track Events

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

### 5. Create Links Programmatically

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

> **Note:** Requires an API key in `LinkFortyConfig`. See [API Reference](API.md#createlinkoptions) for all options.

## Advanced Usage

### Self-Hosted LinkForty Core

```kotlin
val config = LinkFortyConfig(
    baseURL = "https://links.yourcompany.com",
    apiKey = null // No API key needed for self-hosted
)
LinkForty.initialize(context, config)
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

### 1. Create Digital Asset Links File

Your backend must serve a Digital Asset Links file at:
`https://go.yourdomain.com/.well-known/assetlinks.json`

Example:
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
# Verify Digital Asset Links
adb shell am start -a android.intent.action.VIEW \
  -d "https://go.yourdomain.com/abc123" \
  com.yourcompany.yourapp
```

## Privacy & Security

### Privacy-First Design

- **No GAID**: Does not collect Google Advertising ID by default
- **No Persistent IDs**: Uses probabilistic fingerprinting only
- **Data Minimization**: Collects only necessary attribution data
- **User Control**: Provides `clearData()` for user data deletion

### Data Collected (for attribution only)

- Device timezone
- Device language
- Screen resolution (pixels)
- Android version
- App version
- User-Agent string

### HTTPS Required

The SDK enforces HTTPS for all API endpoints (except localhost, 127.0.0.1, and 10.0.2.2 for development).

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
- **LinkForty Cloud** (SaaS): Managed service with advanced features

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

## Related Projects

- [LinkForty Core](https://github.com/LinkForty/core) — open-source self-hosted deep linking engine
- [LinkForty Cloud](https://linkforty.com) — hosted SaaS with dashboard, teams, and billing
