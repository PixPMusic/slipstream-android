<img src="assets/icon.svg" width="64" height="64" alt="">

# Slipstream for Android

A native Kotlin and Jetpack Compose Floatplane client, following [Slipstream for Swift](https://github.com/PixPMusic/slipstream-swift). Thumbnail feeds, creator and channel navigation, and compact playback details use Material 3 Expressive components and the system's dynamic light/dark colors. Android versions before 12 use Material's default light/dark schemes.

## Run

Requires JDK 17 or newer and Android SDK 37. Set `ANDROID_HOME` or put your SDK path in an untracked `local.properties` (`sdk.dir=/path/to/sdk`).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Open in Android Studio, or install `app/build/outputs/apk/debug/app-debug.apk` on Android 8 or later. Package: `cc.pixp.slipstream`.

## Current functionality

- Floatplane web sign-in, API session verification, Keystore-encrypted session storage, and restoration on launch. Passwords stay in the website; website cookies are cleared when the sign-in view closes. Sign-out removes this app's session.
- Library combines subscribed creators, sorted by release date. The navigation drawer exposes creators and their channels, with profile images and channel covers when returned by the API. Channel covers extend across the viewport behind the toolbar; the inside screen also draws behind the status bar, while phones and the folded front screen respect the top safe area. Scrolling reveals the channel title.
- Responsive thumbnail grid with titles, channel names, dates, durations, and locked-content indicators. The search FAB opens search in the top app bar and filters the selected feed. Pull down to refresh; pagination loads more posts.
- Media3 on-demand playback with host-scoped authentication cookies. Post titles, creator information, vote controls, and descriptions appear beneath the player. The fullscreen control enters immersive landscape playback and Back restores the inline layout without replacing the player. A quality picker switches delivery renditions while preserving position and play/pause state; attachment selection handles posts with multiple video/audio files. Playback runs in a Media3 session service with background audio and system notification/lock-screen controls. Leaving during active video automatically enters Picture-in-Picture on supported devices. Paused media and audio-only attachments do not auto-enter PiP. Closing the player explicitly stops playback; returning from PiP retains the player and position.
- Post and comment likes/dislikes use Floatplane’s returned interaction state. Comments include posting, pagination, and expandable replies. Demo actions are disabled.
- Downloads offer available file qualities and run as foreground WorkManager jobs with progress and cancellation. Android 10+ saves to `Downloads/Slipstream`; Android 8–9 uses the app’s external download folder. Notification permission is requested when starting a download.
- Demo uses the same browsing interface, the Swift app's fictional creators, and its bundled silent, one-minute timecode video. No account or network is needed.

Live playback/chat, authored replies, comment editing/deletion, bookmarks are not implemented. The Floatplane API is unofficial and may change.

## Structure

One app module. `data/` contains models, API access, encrypted session storage, and demo fixtures. `ui/` contains sign-in, the shared library/drawer, theme, icons, and player. Material 3 is pinned to `1.5.0-beta01` for its public Expressive APIs; other Compose components use BOM `2026.06.01`.

Created in [T3 Code](https://t3.codes). Not affiliated with Floatplane Media.
