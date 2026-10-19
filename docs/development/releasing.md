# Production APKs and signing identity

The production entry point is `scripts/build-release.ps1`. It reuses the Android
matrix with Release variants, permanent signing and `org.axioma.calculator`.
Debug retains `org.axioma.calculator.debug` and `0.1.0-dev`. Version 0.1.0 keeps
versionCode 4. Production signing does not certify mathematical stability;
the five [known defect groups](../user/known-issues.md) remain open.

## Initialize once and build

```powershell
.\scripts\build-release.ps1 -InitializeSigningKey -TestConnectedDevices -EmulatorApis @(33,34,35,36)
```

Initialization creates a 4096-bit RSA PKCS12 private key, a SHA-256 certificate
and a cryptographically random password. The certificate is valid for 10000 days.
Keys and DPAPI-encrypted credentials live only under ignored `.dev/signing/`,
whose ACL grants the current Windows account and SYSTEM access. Passwords are
passed to keytool/Gradle through temporary process environment variables, never
command-line password arguments or source files. Those variables are restored
after the build. An existing or partial identity is never silently overwritten.

Further builds reuse the same identity, while a completed versioned folder is
preserved rather than overwritten:

```powershell
.\scripts\build-release.ps1 -TestConnectedDevices -EmulatorApis @(33,34,35,36)
```

The wrapper requires Windows/current-user access to the private credential record.
Plain `assembleRelease` without credentials produces no signed distribution;
the matrix rejects release requests without the expected certificate and signing
environment. Private material never belongs in the source archive or release folder.

## Preserve a portable backup

DPAPI recovery depends on the original Windows account/profile and machine.
An ordinary copy of the encrypted credential JSON is not a portable password backup.
Before publishing, run this interactively in your own terminal:

```powershell
.\scripts\export-signing-backup.ps1
```

It requests and confirms a password of at least 16 characters without displaying
it, then exports the same identity into a separately password-encrypted PKCS12
under `.dev/signing/backups/`. Store that backup off this computer and retain its
password separately in a password manager. The interactive backup operation is
not performed automatically by the build. Never generate a replacement identity
for an existing published app. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Public folder

The canonical production assets are under `artifacts/android/release/0.1.0/`:

```text
Axioma-0.1.0-arm64-v8a.apk
Axioma-0.1.0-armeabi-v7a.apk
Axioma-0.1.0-x86_64.apk
Axioma-0.1.0-x86.apk
Axioma-0.1.0-universal.apk
Axioma-0.1.0-source.zip
SHA256SUMS.txt
README.md
COMPATIBILITY.md
KNOWN-ISSUES.md
LICENSE
THIRD-PARTY-NOTICES.txt
```

The directory is an explicit upload selection, not a Git commit. The source ZIP
contains actual Git candidate files, including untracked maintained sources,
tests, build scripts, wrapper, documentation and notices, without staging them.
It excludes `.dev`, SDKs, caches, credentials and APKs. It supplies corresponding
source even if the remote repository still contains only its initial license.

The instrumentation APK stays outside this public folder. Raw logs and the single
central build manifest also remain outside it. The packager verifies all five
application hashes and the permanent certificate before writing notes/checksums.
Completed versioned packages are frozen outside disposable matrix cleanup. Their
file hashes remain in central release metadata, and later debug runs preserve
the folder. A second release invocation refuses to overwrite a completed 0.1.0
package; archive it explicitly before replacing an unpublished attempt, or prepare
a new version. Private signing material is always reused and never archived away
as disposable build output.

## Version and ABI evidence

One APK can run on multiple Android versions; do not upload renamed identical
copies for Android 13, 14, 15 and 16. Names identify version and ABI, while
COMPATIBILITY.md separates the minSdk 26/Android 8.0 installation floor from
actual successful installations and functional tests. Universal verification
does not imply its 32-bit libraries ran. Debug results do not establish that a
production APK passed; release instrumentation targets the release application
and uses the same permanent certificate.

No script commits, tags, creates a GitHub release or uploads any asset.
