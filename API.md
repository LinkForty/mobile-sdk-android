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

---

## LinkForty

Main singleton class providing the SDK interface.

### Singleton Access

```kotlin
LinkForty.shared
```

Throws `LinkFortyError.NotInitialized` if `initialize()` has not been called.

### Methods

#### initialize(context, config, attributionWindowHours, deviceId)

Initializes the SDK with configuration and reports the install.

```kotlin
suspend fun initialize(
    context: Context,
    config: LinkFortyConfig,
    attributionWindowHours: Int = 168,
    deviceId: String? = null
): InstallResponse
```

**Parameters:**
- `context`: Android Context (stored as `applicationContext`)
- `config`: SDK configuration (required)
- `attributionWindowHours`: Attribution window in hours (default: 168 = 7 days)
- `deviceId`: Optional device identifier for attribution (e.g., GAID)

**Returns:** `InstallResponse` with attribution data

**Throws:** `LinkFortyError` if initialization fails

**Example:**
```kotlin
val config = LinkFortyConfig(
    baseURL = "https://go.yourdomain.com",
    apiKey = "your-api-key"
)
val response = LinkForty.initialize(context, config)
Log.d("LinkForty", "Install ID: ${response.installId}")
```

---

#### handleDeepLink(uri)

Handles a deep link URI (App Link or custom scheme).

```kotlin
fun handleDeepLink(uri: Uri)
```

**Parameters:**
- `uri`: The deep link URI to handle

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
- `callback`: Lambda invoked with deep link data (or null for organic installs)

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

**Note:** Requires an API key. If `templateId` is provided, uses `POST /api/links`. Otherwise, uses `POST /api/sdk/v1/links`.

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

**Throws:** `LinkFortyError` if tracking fails

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

**Throws:** `LinkFortyError` if tracking fails

**Example:**
```kotlin
LinkForty.shared.trackRevenue(
    amount = BigDecimal("29.99"),
    currency = "USD",
    properties = mapOf("product_id" to "123")
)
```

---

#### flushEvents()

Flushes the event queue, attempting to send all queued events.

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

Clears all stored SDK data.

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
    val debug: Boolean = false,
    val attributionWindowHours: Int = 168
)
```

**Parameters:**
- `baseURL`: Backend URL (must be HTTPS except localhost/127.0.0.1/10.0.2.2)
- `apiKey`: API key (optional for self-hosted)
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

## Thread Safety

All SDK methods are thread-safe. Callbacks are executed on the main thread. Internal state is protected by Kotlin `Mutex`.

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

Events are automatically queued when offline and sent when connectivity is restored. The queue has a maximum size of 100 events.

---

For more information, see the [full documentation](README.md) or [LinkForty Docs](https://docs.linkforty.com).
