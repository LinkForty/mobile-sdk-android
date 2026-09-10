# Changelog

All notable changes to the LinkForty Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]
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
