# Releasing Mesh

This document covers local release builds for macOS Developer ID distribution.
It is intentionally contributor-facing; end users should not need these steps.

## What The Release Build Produces

Mesh uses the Compose Desktop Gradle plugin to create a signed macOS DMG:

```bash
./gradlew :composeApp:notarizeDmg
```

For a Developer ID release, the build must satisfy two independent checks:

1. The Mesh `.app` is signed with a `Developer ID Application` certificate.
2. The DMG is submitted to Apple's notary service with a local `notarytool`
   keychain profile and stapled after acceptance.

Compose Desktop 1.10 models `notarytool --keychain-profile` directly, so Mesh
uses the same packaging, signing, notarization, and stapling flow as Compose Pi.

## One-Time Machine Setup

Install Xcode or Xcode Command Line Tools so `codesign`, `xcrun notarytool`,
and `xcrun stapler` are available.

Create and install a Developer ID Application certificate from Apple Developer:

1. Create a certificate signing request.
2. In Apple Developer, create a `Developer ID Application` certificate.
3. Download and install the certificate on the release machine.
4. Confirm that the identity is visible:

```bash
security find-identity -v -p codesigning
```

The identity should look like:

```text
Developer ID Application: Example Developer (TEAMID)
```

Create a notarytool keychain profile on each release machine. The conventional
profile name is `apple-notary`:

```bash
xcrun notarytool store-credentials apple-notary \
  --apple-id developer@example.com \
  --team-id TEAMID
```

Do not pass `--password` to this command. When the option is omitted,
`notarytool` prompts securely for the app-specific password and stores the
credential in the developer's local Keychain. The profile itself is local to
each machine. The helper defaults to `apple-notary`, but developers can use a
different local profile name if they configure it as described below.

## Local Gradle Properties

Put only the signing switch and Developer ID identity in the user Gradle
properties file, plus the local notarytool keychain profile name. Do not commit
these values.

```properties
# ~/.gradle/gradle.properties
compose.desktop.mac.sign=true
compose.desktop.mac.signing.identity=Developer ID Application: Example Developer (TEAMID)
compose.desktop.mac.notarization.keychainProfile=apple-notary
```

`local.properties` is gitignored and is also appropriate for per-checkout local
release settings:

```properties
compose.desktop.mac.notarization.keychainProfile=apple-notary
```

If the profile lives outside the default login keychain, also set:

```properties
compose.desktop.mac.notarization.keychainPath=/path/to/keychain.keychain-db
```

Do not configure the Apple ID mode properties for the keychain-profile flow:
`compose.desktop.mac.notarization.appleID`,
`compose.desktop.mac.notarization.teamID`, or
`compose.desktop.mac.notarization.password`. Compose Desktop treats Apple ID
mode and keychain-profile mode as mutually exclusive.

## Release Build Flow

Start from a clean checkout on the release branch:

```bash
git status --short
git pull --ff-only
./gradlew :composeApp:check
./gradlew :composeApp:notarizeDmg
```

The signed and notarized DMG is written under:

```text
composeApp/build/compose/binaries/main/dmg/
```

`notarizeDmg` runs `packageDmg` first, submits the DMG with the configured
`--keychain-profile`, waits for Apple's response, and staples the ticket when
notarization succeeds.

The equivalent manual commands are:

```bash
./gradlew :composeApp:packageDmg
xcrun notarytool submit composeApp/build/compose/binaries/main/dmg/Mesh-1.0.0.dmg \
  --keychain-profile apple-notary \
  --wait \
  --timeout 30m
xcrun stapler staple composeApp/build/compose/binaries/main/dmg/Mesh-1.0.0.dmg
xcrun stapler validate composeApp/build/compose/binaries/main/dmg/Mesh-1.0.0.dmg
spctl --assess --type open --context context:primary-signature --verbose \
  composeApp/build/compose/binaries/main/dmg/Mesh-1.0.0.dmg
```

If DMG container assessment reports `source=no usable signature`, validate the
mounted app payload instead:

```bash
mkdir -p /tmp/mesh-dmg-mount
hdiutil attach composeApp/build/compose/binaries/main/dmg/Mesh-1.0.0.dmg \
  -readonly \
  -nobrowse \
  -mountpoint /tmp/mesh-dmg-mount
spctl --assess --type execute --verbose /tmp/mesh-dmg-mount/Mesh.app
codesign --verify --deep --strict --verbose=2 /tmp/mesh-dmg-mount/Mesh.app
hdiutil detach /tmp/mesh-dmg-mount
```

If Apple's notary queue leaves a submission in progress, keep the submission id
and query it later with the same profile name used for submission:

```bash
xcrun notarytool info <submission-id> --keychain-profile apple-notary
```

## Optional: Friendly DMG Filename

Compose Desktop names the packaged file `Mesh-<version>.dmg`. To also produce a
copy named `mesh-<version>-mac-<arch>.dmg`:

```bash
./gradlew :composeApp:renameDmg
```

Run this after `packageDmg` or `notarizeDmg` if you want the renamed copy of the
stapled DMG.

## Failure Modes

If `notarytool` reports that the configured profile is missing, create the local
profile with `xcrun notarytool store-credentials` as shown above, or set
`compose.desktop.mac.notarization.keychainProfile` to the correct profile name.

If `codesign` cannot find the identity, confirm the Developer ID certificate and
private key are installed on the release machine:

```bash
security find-identity -v -p codesigning
```
