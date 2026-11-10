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

Compose Desktop signs the app, its bundled runtime, and the native libraries
inside its jars with the hardened runtime, a secure timestamp, and the JVM
entitlements it needs (`allow-jit`, `allow-unsigned-executable-memory`, and
`disable-library-validation`). `notarizeDmg` then signs the DMG container with
the same identity, so Gatekeeper can assess the stapled DMG offline.

Compose Desktop's own notarization task only accepts Apple ID credentials, so
Mesh replaces its action with a `notarytool --keychain-profile` flow that keeps
credentials in the Keychain, matching Compose Pi.

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
each machine. The examples use `apple-notary`, but developers can use a
different local profile name if they configure it as described below.

On a machine without an interactive Apple ID login, such as a CI runner, a
profile can store an App Store Connect API key instead:
`xcrun notarytool store-credentials apple-notary --key AuthKey_<KEYID>.p8
--key-id <KEYID> --issuer <ISSUER-ID> --keychain <path>`. Delete the `.p8`
file afterwards and set `compose.desktop.mac.notarization.keychainPath` to that
keychain.

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

`notarizeDmg` refuses to start unless signing, the signing identity, and the
keychain profile are configured. It then runs `packageDmg`, signs the DMG,
submits it with the configured `--keychain-profile`, and waits for Apple's
response. The task fails, printing Apple's notary log, unless the submission
status is `Accepted`. After acceptance it staples the ticket and verifies the
result:

1. `xcrun stapler validate` on the DMG.
2. `spctl --assess --type open --context context:primary-signature` on the
   stapled DMG, which must report `source=Notarized Developer ID`.
3. `codesign --verify --deep --strict` on the `Mesh.app` inside the mounted DMG.
4. `spctl --assess --type execute` on that app, which must also report
   `source=Notarized Developer ID`.

A successful `notarizeDmg` means the DMG was accepted, stapled, and passed
Gatekeeper on the release machine. If any step after packaging fails, the task
renames the DMG to `Mesh-<version>.dmg.unverified` so a stapled but unverified
DMG never sits at the release path. Do not distribute an `.unverified` file.

The equivalent manual commands are:

```bash
DMG=composeApp/build/compose/binaries/main/dmg/Mesh-1.1.0.dmg
./gradlew :composeApp:packageDmg
codesign --force --timestamp \
  --sign "Developer ID Application: Example Developer (TEAMID)" "$DMG"
xcrun notarytool submit "$DMG" \
  --keychain-profile apple-notary \
  --wait \
  --timeout 30m
xcrun stapler staple "$DMG"
xcrun stapler validate "$DMG"
spctl --assess --type open --context context:primary-signature --verbose=4 "$DMG"

# The DMG shows a license agreement, so accept it on stdin when mounting.
mkdir -p /tmp/mesh-dmg-mount
echo Y | hdiutil attach "$DMG" -readonly -nobrowse -mountpoint /tmp/mesh-dmg-mount
codesign --verify --deep --strict --verbose=2 /tmp/mesh-dmg-mount/Mesh.app
spctl --assess --type execute --verbose=4 /tmp/mesh-dmg-mount/Mesh.app
hdiutil detach /tmp/mesh-dmg-mount
```

Check `notarytool submit` output for `status: Accepted`; do not rely on its
exit code alone.

If Apple's notary queue leaves a submission in progress, keep the submission id
and query it later with the same profile name used for submission:

```bash
xcrun notarytool info <submission-id> --keychain-profile apple-notary
```

## Optional: Friendly DMG Filename

Compose Desktop names the packaged file `Mesh-<version>.dmg`. To also produce a
release-named copy, `mesh-<version>-mac-<arch>.dmg`:

```bash
./gradlew :composeApp:renameDmg
```

`renameDmg` depends on `notarizeDmg`, so it needs the same signing and
notarization setup and always copies the notarized, stapled DMG. Signing and
stapling change `packageDmg`'s output, so any later build that runs
`packageDmg` repackages an unsigned DMG over it. Copy the notarized DMG out of
`build/` before running other packaging tasks.

## Failure Modes

If `notarytool` reports that the configured profile is missing, create the local
profile with `xcrun notarytool store-credentials` as shown above, or set
`compose.desktop.mac.notarization.keychainProfile` to the correct profile name.

If notarization returns `Invalid`, the task prints Apple's log with the
rejected files. Fix the signing problem and rerun `notarizeDmg`; do not
distribute the DMG.

`notarizeDmg` ejects any mounted copy of the DMG before it signs, forcing the
eject if a process is using the volume. If it still reports that it could not
eject a mount, eject it manually and rerun the task.

If the task reports that the DMG could not be moved off the release path,
delete `Mesh-<version>.dmg` by hand; it failed notarization or verification.

If `codesign` cannot find the identity, confirm the Developer ID certificate and
private key are installed on the release machine:

```bash
security find-identity -v -p codesigning
```
