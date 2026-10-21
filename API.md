# LinkForty Android SDK - API Reference

Complete API reference for the LinkForty Android SDK.

## Table of Contents

- [LinkForty](#linkforty) - Main SDK class
- [LinkFortyConfig](#linkfortyconfig) - Configuration
- [DeepLinkData](#deeplinkdata) - Deep link data model
- [CreateLinkOptions](#createlinkoptions) - Link creation options
- [CreateLinkResult](#createlinkresult) - Link creation result
- [InstallResponse](#installresponse) - Attribution response
- [UTMParameters](#utmparameters) - UTM tracking parameters
- [LinkFortyError](#linkfortyerror) - Error types
- [Type Aliases](#type-aliases) - Callback types
- [LinkFortyNavObserver](#linkfortynavobserver) - Automatic screen-view tracking

---

## LinkForty

Main singleton class providing the SDK interface.

### Singleton Access

```kotlin
LinkForty.shared
```

Available before `initialize()`. Callbacks registered with `onDeferredDeepLink` / `onDeepLink` and links passed to `handleDeepLink` before then are kept, and the links are resolved once `initialize()` has configured the SDK. Methods that need a reported install (`trackEvent`, `createLink`, …) throw `LinkFortyError.NotInitialized` until `initialize()` succeeds.

### Methods

#### initialize(context, config, attributionWindowHours, deviceId)

Initializes the SDK with configuration and reports the install.

```kotlin
suspend fun initialize(
    context: Context,
    config: LinkFortyConfig,
    attributionWindowHours: Int? = null,
    deviceId: String? = null
): InstallResponse
```

**Parameters:**
- `context`: Android Context (stored as `applicationContext`)
- `config`: SDK configuration (required)
- `attributionWindowHours`: Attribution window in hours (default: `config.attributionWindowHours`)
- `deviceId`: Optional device identifier for attribution (e.g., GAID)

**Returns:** `InstallResponse` with attribution data

**Throws:** `LinkFortyError` if initialization fails. If the install can't be reported (for example, offline on the first launch), the SDK is left uninitialized and keeps registered callbacks: call `initialize()` again.

**Example:**
```kotlin
val config = LinkFortyConfig(
    baseURL = "https://go.yourdomain.com",
    apiKey = "your-api-key",
    appToken = "at_a1b2c3d4..."   // recommended for Cloud — enables organic-install attribution
)
val response = LinkForty.initialize(context, config)
Log.d("LinkForty", "Install ID: ${response.installId}")
```

---

#### handleDeepLink(uri)

Handles a URI that opened the app (App Link or custom scheme). Pass every URI that opens the app; the SDK decides which are LinkForty links.

```kotlin
fun handleDeepLink(uri: Uri): Boolean
```

**Parameters:**
- `uri`: The deep link URI to handle

**Returns:** `true` if the URI is looked up as a LinkForty link, `false` otherwise.

**Which URIs are looked up:**
- A web (`http`/`https`) URI on one of the workspace's link domains, or on the host of `baseURL`, is resolved on the server and `onDeepLink` receives the link's data.
- With an `appToken`, the SDK fetches the workspace's link domains (custom domains and the workspace's link host) from the server and stores them on the device. Until it knows them, every web URI is looked up; with no `appToken`, it never learns them.
- Any other URI, including a custom scheme (`myapp://product/42`) or a web URI on another host, makes no request: `onDeepLink` receives the URI's own path and query parameters, with no `linkId`.
- A URI handed over before `initialize()` is held. On a launch with no stored domain list, a held URI waits up to 3 seconds for the domain fetch, then is decided against what is known. For a held URI, the return value is `true` for a web URI.

**Example:**
```kotlin
intent.data?.let { uri ->
    LinkForty.shared.handleDeepLink(uri)
}
```

---

#### onDeferredDeepLink(callback)

Registers a callback for deferred deep links (install attribution).

```kotlin
fun onDeferredDeepLink(callback: DeferredDeepLinkCallback)
```

**Parameters:**
- `callback`: Lambda invoked with deep link data, or null for an organic install. It is called once, on the launch that reports the install; a callback registered after `initialize()` returns still receives it.

**Example:**
```kotlin
LinkForty.shared.onDeferredDeepLink { deepLinkData ->
    if (deepLinkData != null) {
        Log.d("LinkForty", "Attributed: ${deepLinkData.shortCode}")
    } else {
        Log.d("LinkForty", "Organic install")
    }
}
```

---

#### onDeepLink(callback)

Registers a callback for direct deep links (when app opens from a link).

```kotlin
fun onDeepLink(callback: DeepLinkCallback)
```

**Parameters:**
- `callback`: Lambda invoked with URI and parsed deep link data

**Example:**
```kotlin
LinkForty.shared.onDeepLink { uri, deepLinkData ->
    Log.d("LinkForty", "Opened from: $uri")
    deepLinkData?.deepLinkPath?.let { navigateToPath(it) }
}
```

---

#### createLink(options)

Creates a short link programmatically.

```kotlin
suspend fun createLink(options: CreateLinkOptions): CreateLinkResult
```

**Parameters:**
- `options`: Link creation options (see [CreateLinkOptions](#createlinkoptions))

**Returns:** `CreateLinkResult` with the shareable URL, short code, and link ID

**Throws:**
- `LinkFortyError.NotInitialized` if SDK not initialized
- `LinkFortyError.MissingApiKey` if no API key configured

**Note:** Requires an API key. Posts to `POST /api/sdk/v1/links`, which uses `templateId` when given and the most recent template otherwise, and attaches `externalUserId` (per call, or from `setExternalUserId`). A server without that endpoint (404) gets `POST /api/links` instead; `deduplicated` is then `null`.

**Example:**
```kotlin
val result = LinkForty.shared.createLink(
    CreateLinkOptions(
        deepLinkParameters = mapOf("route" to "VIDEO_VIEWER"),
        title = "Check this out!",
        utmParameters = UTMParameters(source = "app", campaign = "share")
    )
)
Log.d("LinkForty", "Share: ${result.url}")
```

---

#### trackEvent(name, properties)

Tracks a custom event.

```kotlin
suspend fun trackEvent(
    name: String,
    properties: Map<String, Any>? = null
)
```

**Parameters:**
- `name`: Event name (e.g., "purchase", "signup")
- `properties`: Optional event properties (must be JSON-serializable)

**Throws:** `LinkFortyError` for an empty name, when not initialized, or when the server rejects the event (a 4xx). An event that can't be sent now (no connection, or a 5xx) is queued and the call returns normally; see [Offline Support](#offline-support).

**Example:**
```kotlin
LinkForty.shared.trackEvent("purchase", mapOf("product_id" to "123", "amount" to 29.99))
```

---

#### trackRevenue(amount, currency, properties)

Tracks a revenue event.

```kotlin
suspend fun trackRevenue(
    amount: BigDecimal,
    currency: String,
    properties: Map<String, Any>? = null
)
```

**Parameters:**
- `amount`: Revenue amount (must be non-negative)
- `currency`: Currency code (e.g., "USD", "EUR")
- `properties`: Optional additional properties

**Throws:** `LinkFortyError` for an empty name, when not initialized, or when the server rejects the event (a 4xx). An event that can't be sent now (no connection, or a 5xx) is queued and the call returns normally; see [Offline Support](#offline-support).

**Example:**
```kotlin
LinkForty.shared.trackRevenue(
    amount = BigDecimal("29.99"),
    currency = "USD",
    properties = mapOf("product_id" to "123")
)
```

---

#### trackScreenView(name, properties)

Reports a screen view. Emits a `screen_view` event carrying the screen name and the previously tracked screen, stamped with the active last-click attribution context, so the dashboard can build a per-link screen-flow funnel.

```kotlin
suspend fun trackScreenView(
    name: String,
    properties: Map<String, Any>? = null
)
```

**Parameters:**
- `name`: Screen name (e.g., "ProductDetail"). Must not be blank.
- `properties`: Optional additional properties

**Throws:** as `trackEvent` does (including for a blank screen name)

**Example:**
```kotlin
LinkForty.shared.trackScreenView("ProductDetail")
```

For automatic tracking with Jetpack Navigation, use [`LinkFortyNavObserver`](#linkfortynavobserver).

---

#### flushEvents()

Sends the queued events now, oldest first. The SDK also sends them on its own; see [Offline Support](#offline-support).

```kotlin
suspend fun flushEvents()
```

---

#### clearEventQueue()

Clears the event queue without sending events.

```kotlin
fun clearEventQueue()
```

---

### Properties

#### queuedEventCount

Returns the number of events currently queued.

```kotlin
val queuedEventCount: Int
```

---

### Attribution Data Methods

#### getInstallId()

Returns the install ID if available, null if not initialized.

```kotlin
fun getInstallId(): String?
```

---

#### getInstallData()

Returns the install attribution data if available.

```kotlin
fun getInstallData(): DeepLinkData?
```

---

#### isFirstLaunch()

Returns whether this is the first launch.

```kotlin
fun isFirstLaunch(): Boolean
```

---

### Data Management Methods

#### clearData()

Clears all stored SDK data: the install ID, install attribution, queued events and the last-click attribution context. The next launch reports a new install. Registered callbacks stay registered.

```kotlin
fun clearData()
```

---

#### reset()

Resets the SDK to uninitialized state. Does NOT clear stored data — call `clearData()` first if needed.

```kotlin
fun reset()
```

---

## LinkFortyConfig

Configuration for the LinkForty SDK.

### Constructor

```kotlin
data class LinkFortyConfig(
    val baseURL: String,
    val apiKey: String? = null,
    val appToken: String? = null,
    val debug: Boolean = false,
    val attributionWindowHours: Int = 168
)
```

**Parameters:**
- `baseURL`: Backend URL (must be HTTPS except localhost/127.0.0.1/10.0.2.2)
- `apiKey`: API key (optional for self-hosted)
- `appToken`: Public workspace token (LinkForty Cloud only). Recommended — required for organic installs (App Store discovery, social mentions, etc.) to be attributed to your workspace. It is sent with every request as `X-LinkForty-App-Token`, so the server answers with your workspace's links only, and lets the SDK learn your link domains (see `handleDeepLink`). Find it in the dashboard under Workspace Settings → App Token. Format: `at_<32 hex chars>`. Safe to ship in your app bundle.
- `debug`: Enable debug logging (default: false)
- `attributionWindowHours`: Attribution window in hours (default: 168 = 7 days, max: 2160 = 90 days)

### Methods

#### validate()

Validates the configuration. Throws `LinkFortyError.InvalidConfiguration` if validation fails.

```kotlin
fun validate()
```

---

## DeepLinkData

Deep link data model containing parsed link information.

### Properties

```kotlin
val shortCode: String             // Link short code (required)
val iosURL: String?               // iOS deep link URL
val androidURL: String?           // Android deep link URL
val webURL: String?               // Fallback web URL
val utmParameters: UTMParameters? // UTM tracking parameters
val customParameters: Map<String, String>? // Custom query parameters
val deepLinkPath: String?         // Deep link path for in-app routing
val appScheme: String?            // App URI scheme
val clickedAt: String?            // ISO 8601 click timestamp
val linkId: String?               // Link UUID from the backend
```

### Methods

#### clickedAtDate()

Parses `clickedAt` as a `java.time.Instant`.

```kotlin
fun clickedAtDate(): Instant?
```

---

## InstallResponse

Response from install attribution API.

### Properties

```kotlin
val installId: String             // Unique install ID
val attributed: Boolean           // Whether install was attributed
val confidenceScore: Double       // Confidence score (0-100)
val matchedFactors: List<String>  // Matched fingerprint factors
val deepLinkData: DeepLinkData?   // Deep link data if attributed
```

---

## CreateLinkOptions

Options for creating a short link programmatically.

### Constructor

```kotlin
data class CreateLinkOptions(
    val templateId: String? = null,
    val templateSlug: String? = null,
    val deepLinkParameters: Map<String, String>? = null,
    val title: String? = null,
    val description: String? = null,
    val customCode: String? = null,
    val utmParameters: UTMParameters? = null
)
```

---

## CreateLinkResult

Result of creating a short link.

### Properties

```kotlin
val url: String       // Full shareable URL
val shortCode: String // Generated short code
val linkId: String    // Link UUID
```

---

## UTMParameters

UTM parameters for campaign tracking.

### Properties

```kotlin
val source: String?   // Campaign source
val medium: String?   // Campaign medium
val campaign: String? // Campaign name
val term: String?     // Campaign term
val content: String?  // Campaign content
```

### Computed Properties

```kotlin
val hasAnyParameter: Boolean // True if any parameter is set
```

---

## LinkFortyError

Sealed class of errors thrown by the SDK.

### Subclasses

```kotlin
class NotInitialized : LinkFortyError
class AlreadyInitialized : LinkFortyError
class InvalidConfiguration(detail: String) : LinkFortyError
class NetworkError(cause: Throwable) : LinkFortyError
class InvalidResponse(statusCode: Int?, responseMessage: String?) : LinkFortyError
class DecodingError(cause: Throwable) : LinkFortyError
class EncodingError(cause: Throwable) : LinkFortyError
class InvalidEventData(detail: String) : LinkFortyError
class InvalidDeepLinkUrl(detail: String) : LinkFortyError
class MissingApiKey : LinkFortyError
```

### Example

```kotlin
try {
    LinkForty.shared.trackEvent("test")
} catch (e: LinkFortyError) {
    when (e) {
        is LinkFortyError.NotInitialized -> Log.e("LinkForty", "Not initialized")
        is LinkFortyError.NetworkError -> Log.e("LinkForty", "Network error", e.cause)
        is LinkFortyError.InvalidEventData -> Log.e("LinkForty", "Invalid event: ${e.message}")
        else -> Log.e("LinkForty", "Error: ${e.message}")
    }
}
```

---

## Type Aliases

### DeferredDeepLinkCallback

```kotlin
typealias DeferredDeepLinkCallback = (DeepLinkData?) -> Unit
```

### DeepLinkCallback

```kotlin
typealias DeepLinkCallback = (Uri, DeepLinkData?) -> Unit
```

---

## LinkFortyNavObserver

A `NavController.OnDestinationChangedListener` that automatically reports a `screen_view` (see [`trackScreenView`](#trackscreenviewname-properties)) whenever the Jetpack Navigation destination changes.

Requires `androidx.navigation`, which your app already provides if it uses Jetpack Navigation. The SDK depends on it only as `compileOnly`, so apps that don't use Navigation are unaffected.

```kotlin
class LinkFortyNavObserver(
    screenNameExtractor: (NavDestination) -> String? = ::defaultScreenName,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : NavController.OnDestinationChangedListener
```

By default a destination's `route` (Compose navigation) or `label` (XML nav graph) is used as the screen name; destinations with neither are skipped. Pass `screenNameExtractor` to customize.

**Example:**
```kotlin
import com.linkforty.sdk.navigation.LinkFortyNavObserver

navController.addOnDestinationChangedListener(LinkFortyNavObserver())
```

---

## Thread Safety

All SDK methods are thread-safe. Callbacks are executed on the main thread. Concurrent `initialize()` calls are serialized: one reports the install, the others throw `LinkFortyError.AlreadyInitialized`.

## Coroutines Support

The SDK uses Kotlin coroutines for all asynchronous operations:

```kotlin
lifecycleScope.launch {
    val response = LinkForty.initialize(context, config)
    LinkForty.shared.trackEvent("test")
    LinkForty.shared.flushEvents()
}
```

## Offline Support

Every tracked event is stored on the device before it is sent and removed once the server has it, so events survive the app being closed. An event that can't be sent (no connection, or a 5xx response) stays queued, up to 100 events, and `trackEvent` returns normally. Queued events are sent, oldest first:

- when `initialize()` completes on the next launch,
- when the app returns to the foreground,
- after the next event that sends, and
- when you call `flushEvents()`.

Each event carries a `clientEventId`, generated when it is tracked and kept through every retry, so the server stores an event it receives twice only once. An event the server refuses with a 4xx is not queued, and `trackEvent` throws.

The SDK doesn't watch the network connection and needs no permission beyond `INTERNET`.

---

For more information, see the [full documentation](README.md) or [LinkForty Docs](https://docs.linkforty.com).
