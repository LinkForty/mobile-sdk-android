# Releasing

## Version

The version lives in **one place**: `VERSION_NAME` in [`gradle.properties`](gradle.properties).

It flows automatically to:
- the published Maven artifact (`version = property("VERSION_NAME")` in `sdk/build.gradle.kts`), and
- the version the SDK reports to the backend at runtime — `sdkVersion` field on install/event payloads and the `X-LinkForty-SDK` header — via `BuildConfig.SDK_VERSION` (a `buildConfigField` sourced from `VERSION_NAME`, exposed through `com.linkforty.sdk.SdkInfo`).

So there is **no separate version constant to bump** — update `VERSION_NAME` and everything stays in sync.

## Steps

1. Bump `VERSION_NAME` in `gradle.properties`.
2. Update `CHANGELOG.md` (move `[Unreleased]` to the new version heading).
3. Commit, then create and push the git tag (matching `VERSION_NAME`).
4. Publish to Maven Central: `./gradlew publishToMavenCentral`.

   There is no release workflow — this repo has only `test.yml`, so publishing is
   done by hand from a machine with the credentials in `~/.gradle/gradle.properties`.

   `automaticRelease` is `false`, so the upload lands in a **staging deployment** at
   <https://central.sonatype.com/publishing/deployments> rather than going public.
   Check the POM, the signatures and the artifact contents there, then click
   *Publish* to promote it. Maven Central is immutable: a released version can never
   be replaced, only superseded by a new one. Dropping a bad staging deployment is
   free; unpublishing a released version is impossible.
