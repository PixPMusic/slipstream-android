# GitHub Actions

## Pull requests and main

`ci.yml` runs on Ubuntu with Temurin JDK 21. It runs the JVM unit tests and
debug lint, assembles a debug APK, and builds an unsigned release bundle to catch
release-only failures before the Play workflow needs credentials. It boots no
emulator and needs no Google account.

The workflow has only `contents: read`, does not persist Git credentials, and
receives no signing secrets. `setup-gradle` validates the wrapper JAR checksum
and writes its dependency cache only from `main`. There is no privileged
`pull_request_target` or `workflow_run` path, and no artifact handoff into
signing. Fork changes cannot reach the release job through a cache or a PR
artifact.

## Play release

`play-release.yml` is a manual workflow for an exact `main` commit. It builds a
signed Android App Bundle with the upload key and sends it to a Play testing
track, open testing (`beta`) by default. It never signs PR code and never uploads
the bundle or key as a GitHub artifact.

### One-time Play Console setup

1. Create the app in [Play Console](https://play.google.com/console) with
   package name `cc.pixp.slipstream`. The package name is permanent.
2. Use **Play App Signing** (the default). Google holds the app signing key; CI
   only holds the upload key, which Google can reset if it is lost or leaked.
3. In Google Cloud, create a service account for this app and a JSON key. In Play
   Console **Users and permissions**, invite the service account's email with
   access to this app only, limited to **Release to testing tracks** (and
   **View app information**). Do not grant production release or admin rights.
4. Complete the store listing, content rating, data safety form, and privacy
   policy URL ([PRIVACY.md](PRIVACY.md) once published), and select countries
   for open testing. Play reviews testing releases outside internal testing.

The workflow can make the app's first upload. Run it with `draft` status, then
review and roll out the draft in Play Console.

Open testing (`beta`) lists the app as an early-access title in the Play Store
that anyone can join. Each open testing release is reviewed by Google before it
reaches testers. Internal (`internal`) and closed (`alpha`) testing remain
available for smaller groups. Personal developer accounts created after November
2023 must also run a closed test with at least 12 opted-in testers for 14 days
before applying for production access.

### GitHub environment

Create a GitHub environment named **play**. Set deployment branch rules to
**selected branches**, allowing only **main**, and disallow tag deployment.
Keep these secrets in that environment, not in repository-wide secrets:

| Secret | Value |
| --- | --- |
| `PLAY_UPLOAD_KEYSTORE_BASE64` | Base64-encoded PKCS#12 upload keystore containing the key alias `upload` |
| `PLAY_UPLOAD_KEYSTORE_PASSWORD` | Password for the keystore and key |
| `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON key with testing-track release permission for this app |

Generate a dedicated upload key outside the repository:

```sh
keytool -genkeypair -storetype PKCS12 -keystore /private/path/slipstream-upload.p12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Slipstream upload'
base64 -i /private/path/slipstream-upload.p12 | gh secret set PLAY_UPLOAD_KEYSTORE_BASE64 --env play
gh secret set PLAY_UPLOAD_KEYSTORE_PASSWORD --env play
gh secret set PLAY_SERVICE_ACCOUNT_JSON --env play < /private/path/service-account.json
```

Keep an offline backup of the upload key and password. Never commit keystores
or service-account files; `.gitignore` excludes common names as a safeguard.

### Running a release

From Actions, dispatch **Play release** on **main** with a version name
(`X.Y.Z`), a new version code, a track (`beta` open testing by default, or
`alpha` closed and `internal` testing), and a status. Version codes must increase with every
upload, including failed attempts that reached Play.

Use `draft` until the app has completed its first Play review; Play rejects
completed releases for apps that have never been published. Drafts appear in
the selected track for you to review and roll out in Play Console. After the
first review, `completed` rolls out to that track's testers directly.

The job checks out that exact commit, validates inputs, runs unit tests, decodes
the upload key into the runner's temporary directory, builds and verifies the
signed bundle, removes the key, and uploads with the service account. Gradle
signs release builds only when `SLIPSTREAM_UPLOAD_KEYSTORE` and
`SLIPSTREAM_UPLOAD_PASSWORD` are set. The workflow never targets the production
track.

### Building locally

The same environment variables produce a signed bundle on your machine, to inspect
a release or upload one manually:

```sh
SLIPSTREAM_UPLOAD_KEYSTORE=/private/path/slipstream-upload.p12 \
SLIPSTREAM_UPLOAD_PASSWORD='…' \
  ./gradlew :app:bundleRelease -Pslipstream.versionName=0.1.0 -Pslipstream.versionCode=1
```

The bundle is written to `app/build/outputs/bundle/release/app-release.aab`.
Without the variables, release builds are unsigned.

## Updating dependencies

Actions are pinned to full commit SHAs and updated manually. Dependabot updates
and CodeRabbit automatic reviews and replies are disabled. Review workflow and
dependency changes before merging them into the release branch. The Play upload
uses [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play),
pinned like the others, because it receives the service-account key.

References: [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use),
[deployment environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments),
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756),
[testing tracks](https://support.google.com/googleplay/android-developer/answer/9845334),
and [Play Developer API access](https://developers.google.com/android-publisher/getting_started).
