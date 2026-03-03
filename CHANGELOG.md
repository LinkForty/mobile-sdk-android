# Changelog

All notable changes to the LinkForty Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
