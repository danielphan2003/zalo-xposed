# zalo-xposed

An Xposed module for Zalo (`com.zing.zalo`), written to be embedded into the original Zalo APK
with [LSPatch](https://github.com/JingMatrix/LSPatch). It needs no root and no LSPosed Manager.

## Features

**Ads**

- Drops sponsored items from the Nhật ký feed while Zalo parses it, and hides any Zinstant ad card
  that gets through.
- Hides the Zinstant ad row in the Messages tab and skips loading it.
- Turns off the story and community ad slots at their server config flags.
- Removes ads from Zalo Video's ads responses and hides outstream, native and CTA ad overlays.
- Makes the bundled Adtima ad SDK's load calls do nothing, as if every ad zone were empty.

**Telemetry**

- Firebase Crashlytics: data collection reported off; its log, custom key, user ID and report
  calls do nothing.
- Firebase Analytics: collection reported as deactivated, with the manifest flags it reads turned
  off.
- Google advertising ID: every caller gets the all-zero ID with ad tracking limited.
- Zalo's analytics database (events, screens, sessions, views): inserts do nothing.
- Zalo's ad tracking: impression and click events are not recorded, queued or sent.

**Recalled messages**

- When someone else recalls (unsends) a message, it stays on this phone with a `[Đã thu hồi]`
  (or `[Recalled]`) label in front of its text, instead of Zalo's placeholder. The text is also
  saved in a file inside Zalo's data on this phone, so the label comes back after the chat or the
  app reloads. Your own recalls still go through.

## How it finds its targets

Zalo is obfuscated and changes every release, so the module looks for each hook target by
features that survive renaming: string constants, call shapes, Kotlin null-check messages that
carry a getter's source name, and class names that libraries, Room, layouts or the manifest force
Zalo to keep. A small pure-Kotlin dex reader in `dex/` does this on the phone on first launch
(well under a second on a desktop JVM for Zalo 26.10.02), then caches the result in
`no_backup/zalo-xposed/targets.txt` until Zalo or the module changes.

Every target is found and hooked on its own. If one goes missing in a new Zalo build, the module
logs it (tag `ZaloXposed`) and skips only the hooks that need it; Zalo keeps running.

## Xposed API

The module uses the modern [libxposed API](https://github.com/libxposed/api) 102
(`minApiVersion=101`, `targetApiVersion=102`). JingMatrix/LSPatch's embedded loader accepts modern
modules targeting API 101 or newer and legacy `assets/xposed_init` modules, and refuses API 100;
its framework core implements API 102.

## Build

```sh
./gradlew check :app:assembleRelease
```

The release APK is unsigned on purpose: LSPatch embeds module APKs byte for byte and does not
check their signature, and an unsigned build is reproducible, so a tag always yields the same
bytes. Tags `v*` publish `zalo-xposed-<version>.apk` and `SHA256SUMS` to GitHub Releases.

`ZaloApkTargetsTest` resolves every target against a real Zalo APK when `ZALO_APK` points at one;
CI has no Zalo APK and skips it.

## Status

v0.1.0 was built and checked off-device only: unit tests, Android lint, and a test that resolved
all 19 hook targets against Zalo 26.10.02 (versionCode 261002903). None of it has run on a phone
yet. To check there, after patching Zalo with LSPatch and this module:

- `adb logcat -s ZaloXposed` shows each feature as `installed`, with no `skipped` lines, and the
  first launch logs `targets: resolved 19`; later launches log `from cache`.
- Nhật ký feed, stories, the Messages tab and Zalo Video show no ads and no blank gaps; the story
  tray still advances past where an ad would be; videos still start.
- Ask a contact to send a text and recall it: it stays, labelled `[Đã thu hồi]` (or `[Recalled]`),
  after reopening the chat and after restarting Zalo. Recalling your own message still removes it.
- Zalo keeps working: sending, receiving, calls, notifications.

Known limits: the recall label goes on the message text only, so a recalled photo, sticker or file
stays visible but unlabelled; an open chat may only show the label after it is reopened.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
