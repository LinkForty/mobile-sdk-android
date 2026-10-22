# Changelog

All notable changes to the LinkForty Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]
### Added
- For an install from Google Play, the SDK reads the Play install referrer (Play Install Referrer API) on the launch that reports the install and sends it as `installReferrer`. A LinkForty link puts its click id in the referrer of the Play listing it sends a visitor to, so a server on `@linkforty/core` 1.41.1 or later matches the install to that exact click: `attributionMethod` is `"install_referrer"`, `confidenceScore` 100 and `competingLinks` 0. The SDK waits at most 2 seconds for Play; without a referrer (another store, a sideload, no answer) the install is matched by fingerprint as before.

### Changed
- The SDK depends on `com.android.installreferrer:installreferrer` 2.2, which adds the `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` permission to the app's merged manifest. It is a normal permission: Android grants it without asking the user.

## [1.7.0] - 2026-10-21
### Added
- `InstallResponse.attributionMethod` (`"fingerprint"` or `"none"`) and `InstallResponse.competingLinks`, from a server on `@linkforty/core` that sends them. `competingLinks` is how many other links had a click from the install's network that also reached the confidence threshold: above 0, a tap on another device on the same network may be what led to this install, so an app acting on a sensitive link can treat the match as unverified. Both are null from a server that does not send them, and on launches after the first, which return the stored attribution ([#18](https://github.com/LinkForty/mobile-sdk-android/pull/18)).
- `DeepLinkData.confidenceScore`, `matchedFactors`, `attributionMethod` and `competingLinks`, set on the deferred deep link and stored with it, so `getInstallData()` returns them on later launches. They are null on a direct open, and in install data stored by an earlier SDK version ([#18](https://github.com/LinkForty/mobile-sdk-android/pull/18)).

### Changed
- `DeepLinkData` and `InstallResponse` have `@JvmOverloads` constructors with the new parameters at the end, so Kotlin and Java callers compile unchanged and Java code compiled against 1.6.x keeps working. Kotlin code compiled against 1.6.x that constructs either class with default arguments, or calls `copy()`, must be recompiled ([#18](https://github.com/LinkForty/mobile-sdk-android/pull/18)).

## [1.6.0] - 2026-10-21
### Added
- With an `appToken`, every request carries `X-LinkForty-App-Token`, so a server on `@linkforty/core` 1.37.0 or later answers with the app's own workspace's links only ([#16](https://github.com/LinkForty/mobile-sdk-android/pull/16)).
- With an `appToken`, the SDK learns the workspace's link domains: `initialize()` fetches them from `GET /api/sdk/v1/link-domains` in the background and stores them on the device, used from then on. A stored list is used at once on later launches ([#16](https://github.com/LinkForty/mobile-sdk-android/pull/16)).
- `handleDeepLink(uri)` returns `true` when it looks the URI up as a LinkForty link and `false` otherwise ([#16](https://github.com/LinkForty/mobile-sdk-android/pull/16)).

### Changed
- **Behavior change:** `handleDeepLink` looks up only LinkForty links: a web URI on one of the workspace's link domains or on the `baseURL` host, or any web URI while the domains are unknown. A custom-scheme URI, and a web URI on another host once the domains are known, make no request; `onDeepLink` receives the URI's own parameters, as it did when a lookup failed. Every URI was looked up before, so an app's own URLs could match another link's short code. `handleDeepLink`'s return type changed from `Unit` to `Boolean`; Kotlin and Java callers compile unchanged, and a library compiled against 1.5.x that calls it must be recompiled ([#16](https://github.com/LinkForty/mobile-sdk-android/pull/16)).

## [1.5.0] - 2026-10-21
### Added
- Every event carries a `clientEventId`, generated when it is tracked and kept through every retry, so a server on `@linkforty/core` 1.36.0 or later stores an event it receives twice only once; an older server ignores it ([#14](https://github.com/LinkForty/mobile-sdk-android/pull/14)).
- The event queue is stored on the device. Each event is stored before it is sent and removed once the server has it, so events not yet sent survive the app being closed. `clearData()` and `clearEventQueue()` clear the stored queue ([#14](https://github.com/LinkForty/mobile-sdk-android/pull/14)).
- Queued events are sent when `initialize()` completes and when the app returns to the foreground, as well as after the next event that sends and on `flushEvents()`. No permission is added: the foreground is detected from activity lifecycle callbacks, and the network connection is not watched ([#14](https://github.com/LinkForty/mobile-sdk-android/pull/14)).

### Changed
- **Behavior change:** `trackEvent`, `trackRevenue` and `trackScreenView` return normally when the event can't be sent now (no connection, or a 5xx response) and is queued. They threw `NetworkError` or `InvalidResponse` while queueing the event. They still throw for invalid input, when the SDK is not initialized, and when the server rejects the event (a 4xx) ([#14](https://github.com/LinkForty/mobile-sdk-android/pull/14)).

## [1.4.0] - 2026-10-20
### Fixed
- The install is reported once per installation. `initialize()` previously reported an install on every launch, so each launch within the attribution window recorded a new install matched to the same click, and the deferred deep link was delivered again. Once an install ID is stored, `initialize()` now returns the cached attribution without a request, and `onDeferredDeepLink` fires only on the launch that reported the install. The attribution stays available from `getInstallData()` ([#9](https://github.com/LinkForty/mobile-sdk-android/pull/9)).
- `LinkForty.isFirstLaunch(context)` can be called before `initialize()`; `LinkForty.shared.isFirstLaunch()` stays true for the whole first launch. It previously returned `true` before initialization whatever the stored state, and `false` as soon as `initialize()` had reported the install ([#9](https://github.com/LinkForty/mobile-sdk-android/pull/9)).
- `trackEvent`, `trackRevenue` and `trackScreenView` succeed on the event endpoint's response, `{"eventId", "acknowledged"}`. They threw `DecodingError` on every call while the server stored the event, queued it, and sent it again on each flush, so events were stored more than once ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- A failed event is queued only when it may succeed later (no connection, or a 5xx response). An event the server refuses with a 4xx is reported and not queued, and a queued event that fails again keeps its place at the head of the queue ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- Connection failures and timeouts are retried with the same backoff as 5xx responses (3 attempts). Coroutine cancellation is passed through rather than reported as `NetworkError` ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- If `initialize()` can't report the install, the SDK is left uninitialized and `initialize()` can be called again in the same session. Previously a retry threw `AlreadyInitialized` and events threw `NotInitialized` until the app restarted ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- `initialize()` uses `LinkFortyConfig.attributionWindowHours` unless an `attributionWindowHours` argument is passed. It previously sent 168 hours whatever the config said ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- Link parameters that are not strings (`42`, `true`, objects) reach the app as text in `customParameters` (`"42"`, `"true"`, JSON). Such a parameter previously dropped the whole deep link: an attributed install delivered no deferred deep link, and a direct open fell back to the local parse without `linkId` or `deepLinkPath` ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- `clearData()` keeps the app's registered `onDeepLink` and `onDeferredDeepLink` callbacks, so deep links keep working after it ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- `createLink()` with a `templateId` returns the short URL the server reports for the link, which carries the link's own domain, when the server provides one ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- Concurrent `initialize()` calls are serialized, so only one reports the install ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- `LinkForty.shared` is available before `initialize()`. `onDeferredDeepLink` and `onDeepLink` callbacks registered before then are kept, and a link passed to `handleDeepLink` before then, as when an App Link launches the app, is held and resolved once `initialize()` has configured the SDK. `shared` previously threw `NotInitialized`, so an Activity handling its launch intent before initialization finished crashed. A failed `initialize()` keeps registered callbacks ([#11](https://github.com/LinkForty/mobile-sdk-android/pull/11)).
- `createLink()` always posts to `POST /api/sdk/v1/links`, with the `templateId` when one is given, so `externalUserId` reaches the server and repeat links for the same user and parameters are deduplicated. A `templateId` previously sent the request to `POST /api/links`, which ignores `externalUserId`. A server without the SDK link endpoint (404) gets `POST /api/links` instead ([#11](https://github.com/LinkForty/mobile-sdk-android/pull/11)).

### Changed
- `initialize()`'s `attributionWindowHours` parameter is `Int?` and defaults to `null`, meaning the config's value. Kotlin callers compile unchanged; a library compiled against 1.3.x that calls `initialize()` must be recompiled ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).
- An organic install calls `onDeferredDeepLink(null)` on the launch that reports the install, as documented. Code in an app's organic-install branch now runs; it is not called on later launches, or on installs reported before this version ([#10](https://github.com/LinkForty/mobile-sdk-android/pull/10)).

## [1.3.3] - 2026-10-19
### Fixed
- The fingerprint reports the device language as `language` or `language-REGION` (`en-ES`, `zh-CN`, `es-419`), on the install request and as `fp_lang` on link resolves. `Locale.toLanguageTag()` also carries script, variant and Unicode extension subtags — `en-ES-u-fw-mon-mu-celsius` when the user sets a first day of week or temperature unit — which are now dropped, so the value matches what a browser reports for the same device ([#6](https://github.com/LinkForty/mobile-sdk-android/pull/6)).

## [1.3.2] - 2026-09-09
### Fixed
- `InstallResponse` no longer fails to decode when the backend returns `deepLinkData: {}` for an organic (unattributed) install, which surfaced as a `LinkFortyError.DecodingError` out of `initialize()`. An empty or otherwise unusable `deepLinkData` object is now treated as "no deep link" (`null`), the same as `null`.

## [1.3.1] - 2026-09-09
### Fixed
- URL parameters appended to a link are now delivered on a **direct open** (app already installed), not just after a deferred install. A link shared as `?slug=titanic` previously returned only the link's stored configuration on a direct open, because `resolveUrl` discarded the local parse of the tapped URL. `customParameters` now carries both, with URL values winning on a collision — the same precedence the server applies on the deferred path. `linkId`, `deepLinkPath`, `appScheme`, the store URLs and `utmParameters` remain server-provided ([#5](https://github.com/LinkForty/mobile-sdk-android/pull/5)).
- The reserved-parameter filter now excludes `fp_*` and `lf_click` in addition to the UTM keys, matching the server. Fingerprint signals and the click-correlation id could previously reach an app inside `customParameters` as if the customer had set them. The predicate matches on prefix rather than an exact set, so `utm_*` and `fp_*` are covered whatever the suffix ([#5](https://github.com/LinkForty/mobile-sdk-android/pull/5)).

## [1.3.0] - 2026-06-11
### Added
- The SDK now identifies itself on every request: a `sdkName` (`"android"`) and `sdkVersion` field is included on the install and event payloads, and an `X-LinkForty-SDK: android/<version>` header is sent on all requests. This lets the backend report which SDKs and versions are in use and flag outdated integrations. The reported version is sourced from `BuildConfig` so it always matches the published artifact. No API or integration changes are required.
- **Last-click attribution for in-app events.** Every tracked event is now stamped with the deep link that most recently opened the app (deferred install *or* direct re-engagement) plus an app-open `sessionId`, so the backend can credit in-app activity to the originating link. The newest deep-link open supersedes the previous one, and the active link is persisted across app restarts; events with no preceding deep-link open stay organic (session only). Fully automatic.
- **Screen-view tracking** for per-link screen-flow funnels. New `LinkForty.shared.trackScreenView(name)` emits a `screen_view` event (carrying the screen name, the previous screen, and the active attribution stamp). For automatic tracking with Jetpack Navigation, attach `LinkFortyNavObserver` to your `NavController` (`androidx.navigation` is a `compileOnly` dependency, so apps that don't use Navigation are unaffected).

## [1.2.0] - 2026-05-04
### Added
- `appToken` parameter on `LinkFortyConfig` for LinkForty Cloud organic-install attribution. The token is a public, workspace-scoped identifier (format: `at_<32 hex>`) safe to ship in your app bundle. When provided, it's sent on the install request so Cloud can scope organic installs (Play Store discovery, social mentions, etc.) to your workspace. Self-hosted Core ignores the field. Find your token in the Cloud dashboard under Workspace Settings → App Token.
- `setExternalUserId(_:)` and `getExternalUserId()` on `LinkForty` for SDK-level user attribution. The set value is automatically attached to all `createLink()` calls (unless overridden per-call via `CreateLinkOptions.externalUserId`), enabling per-user deduplication and share attribution on the dashboard. Pass `null` to clear. The value is stored in memory only and is cleared by `clearData()` and `reset()`.

## [1.1.0] - 2026-03-03

### Added
- `externalUserId` parameter to `CreateLinkOptions` for per-user deduplication and share attribution
- `deduplicated` field to `CreateLinkResult` indicating when an existing link was returned
- `llms.txt` — LLM-optimized integration reference for AI coding assistants

## [1.0.0] - 2026-02-16

### Added
- Initial release
- Deferred deep linking with probabilistic fingerprinting
- Android App Links support
- Custom URL scheme support
- Event tracking with offline queueing (max 100 events)
- Revenue tracking with BigDecimal precision
- Programmatic link creation (SDK and dashboard endpoints)
- Server-side URL resolution with fingerprint query parameters
- Attribution data access (install ID, install data, first launch status)
- Data management (clear data, reset SDK)
- Configuration validation (HTTPS enforcement, attribution window bounds)
- Privacy-first design (no GAID by default)
- Kotlin coroutines for all async operations
- OkHttp for HTTP networking
- Moshi with codegen for JSON serialization
- JUnit 5 + MockK test suite
- GitHub Actions CI (unit tests, lint, build)
- Maven Central publishing configuration
- ProGuard/R8 consumer rules

### Security
- HTTPS enforcement for API endpoints (except localhost/127.0.0.1/10.0.2.2)
- Bearer token authentication
- No persistent device identifiers collected by default
