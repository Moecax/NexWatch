# Vendored FitCloudPro SDK

This directory is a hand-built, repo-local Maven repository holding exactly
two artifacts: `com.topstep.wearkit:sdk-base` and `com.topstep.wearkit:sdk-fitcloud`.
Only `:core:watch-fitcloud` may depend on them (see the module table in
`CLAUDE.md`).

## Why vendored instead of the vendor's Maven repo

Since v3.0.1 the SDK is published on the vendor's own Maven server, which is
plain HTTP and requires `allowInsecureProtocol = true`. We never add that
repository or that flag to this build (`docs/implementation-plan.md` §10.1).
Instead the two AAR files were taken directly from the **official GitHub
mirror's `/libs` folder**, which ships the same prebuilt binaries over HTTPS.

## Provenance

| Field | Value |
|---|---|
| Source repo | https://github.com/htangsmart/FitCloudPro-SDK-Android |
| Commit | `7409d0f65d9a786c56610e3734e9e1c51a5b5a29` (2026-09-11) |
| Source path | `libs/sdk-base-3.0.2.4.aar`, `libs/sdk-fitcloud-3.0.2.4.aar` |
| SDK version | 3.0.2.4 (2026-07-02 per the repo's changelog) |

| Artifact | SHA-256 |
|---|---|
| `sdk-base-3.0.2.4.aar` | `66550e71c74b0a91dbaf03616426aa37c9cdde50f72ab1e52fe136f54bc5653` |
| `sdk-fitcloud-3.0.2.4.aar` | `0d8191dd3da1c4b257e96cac8ee1cf0ce65e51dba03a7b2b2d18965363fb7a4` |

These checksums are also what `gradle/verification-metadata.xml` pins. If
either AAR is ever re-vendored — a newer SDK release, for instance —
recompute both the table above and the verification metadata together, in
the same change (`./gradlew --write-verification-metadata sha256 <build tasks>`
regenerates the whole file), and update the commit hash.

## POMs

`sdk-base-3.0.2.4.pom` and `sdk-fitcloud-3.0.2.4.pom` are hand-written, not
taken from the vendor. The vendor's real POMs pull in RxJava3, RxAndroidBLE,
OkHttp, Timber, and (for `sdk-fitcloud`) optional extension AARs like
`ext-realtek-dfu` transitively. Rather than guess those versions and exclude
rules, the vendored POMs here declare nothing beyond the
`sdk-base` → `sdk-fitcloud` relationship, and `:core:watch-fitcloud`'s
`build.gradle.kts` declares the required companion libraries explicitly
through `gradle/libs.versions.toml` (Phase 4). This also means no reconnect
extras (Realtek DFU, sensor games, AliAgent, etc.) are pulled in until a
later phase decides it actually needs them.

## Not vendored

`ext-realtek-dfu`, `ext-realtek-bbpro`, `ext-realtek-file`, `ext-sensorgame`,
`sdk-aliagent`, and `sdk-fitcloud-compat` (all also present in the source
repo's `/libs/ext`) are **not** copied here. Nothing in the plan currently
needs them; firmware update (Phase 10) is the first thing that might.

## Local repo wiring

`settings.gradle.kts` points at this directory as a local (non-HTTP) Maven
repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("third_party/maven") }
        // ...
    }
}
```
