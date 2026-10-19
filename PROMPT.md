# Prompt: build a custom Android TV home screen

Copy everything below this line and paste it into your coding agent (Claude Code, Codex, or similar), with the agent opened in an empty folder on your computer.

---

I want a custom home screen (launcher) for my Android TV, built the way the example project at <REPO_URL> is built. Use that repository as a **reference to learn from and adapt**, not as something to copy blindly. Reply in my language.

## Step 0: learn from the example

1. Clone <REPO_URL> into a temporary folder outside my project. Read `README.md`, then read every file in `src/` (start with `MainActivity.java`), `AndroidManifest.xml`, `build.sh`, `install.sh`, `undo.sh` and `ci/smoke-test.sh`.
2. Before writing code, summarize back to me in a few lines what you learned and how you plan to adapt it.
3. Ask me, in one message, everything you need:
   - My TV brand and model, and its Android version if I know it.
   - Which apps I want on the home screen, in order. If I'm not sure of a package name, find it on the TV with `adb shell pm list packages`.
   - My language, and the city for the weather (otherwise it comes from my public IP).
   - Whether I want a "trending movies and series" row (needs a free TMDB API key from themoviedb.org) or not.
   - What I want to look different from the example (colors, layout, no weather, and so on).

## What the app must do

A single-screen Android TV launcher, written in plain Java, with no Gradle and no libraries.

**Manifest**
- One activity with an intent filter for `android.intent.action.MAIN` + `android.intent.category.HOME` + `android.intent.category.DEFAULT`, `launchMode="singleTask"`, landscape, excluded from recents.
- `uses-feature` for `android.software.leanback` and for touchscreen, both `required="false"`.
- `minSdk 21`, `targetSdk 34`. Permissions: `INTERNET` and `ACCESS_NETWORK_STATE` only.
- A `<queries>` block for the `MAIN` + `LEANBACK_LAUNCHER` and `MAIN` + `LAUNCHER` intents, otherwise Android 11+ hides the other apps from the launcher.

**Home screen**
- Designed at 1920x1080 and scaled to the real screen with one `px()` helper.
- A grid of app tiles. Each tile is a focusable card with the app icon and name, and a visible focus ring that lifts the card slightly. Navigation uses the remote's arrows and OK only. No touch.
- Holding OK, or pressing Menu, on a tile opens a small menu: move (arrows slide it, OK confirms), replace, remove from the home screen. Removing never uninstalls anything.
- An "add app" button that lists every launchable app not on the screen yet.
- The app list is saved in `SharedPreferences`. If nothing is saved, it comes from a list built into the app at build time from a plain text file (`apps.txt`, one package name per line), topped up with other TV apps installed on the TV, skipping the stock launchers and system tools. Apps that aren't installed are skipped.
- A row of pill buttons: the TV's HDMI inputs (found with `TvInputManager`, opened through the Live TV app), Settings, and "add app".
- Reacts to apps being installed or removed.
- Clock and date, refreshed on the minute.
- Weather card (optional): current temperature and the next three days. City and coordinates from the public IP (`ipapi.co`, with `ipwho.is` as a fallback), forecast from Open-Meteo (no key). The raw JSON is cached in `SharedPreferences` and parsed on load. If the network fails, keep showing the cache.
- Trending row (optional): TMDB `trending/{movie|tv}/day`, posters downloaded once into the cache folder and pruned to the ones on screen, with a rating badge. The TMDB key arrives from the install command, never from the source code.
- Visual style: a "night" look from 19:00 to 06:00 and a "morning" look otherwise, translucent glass cards, a background drawn once into an RGB_565 bitmap and then only blitted, outline icons drawn as vector paths in code, and one bundled font in `assets/`.
- It must stay light on slow TVs: no images in the layout, no animation except the focus lift, no libraries, a single background thread for network work, and all network calls fail silently (return `null`).

**Configuration from the computer, no editing on the TV**
- `adb shell am start -n <package>/.MainActivity --es apps "pkg1,pkg2" --es tmdb <key> --es stamp <unique>`. `--es apps auto` goes back to the built-in list. The `stamp` makes the app apply the extras only once, because Android can restart the home screen with the same old intent and that would undo changes made on the TV.

## Build and install (plain scripts)

- `build.sh` (macOS/Linux) and `build.ps1` (Windows): generate `Apps.java` from the app list, then `aapt2 link` (manifest and assets only), `javac -source 1.8 -target 1.8` with the Android platform jar and `core-lambda-stubs.jar`, `d8`, add `classes.dex` to the APK, `zipalign`, `apksigner`. Needs JDK 17 and Android SDK platform 34 and build-tools 34.0.0.
- The signing key is created on the first build **outside the repository** (`~/.tvhome`), with a random password. Updates must be signed with the same key. Never commit a keystore or a password.
- `install.sh <TV_IP> ["pkg1,pkg2"]`: `adb connect`, install, set the app as the default home (`cmd role add-role-holder --user 0 android.app.role.HOME <package>` on Android 10+, and `cmd package set-home-activity`). If the TV's own launcher still wins, **ask me first**, then turn it off with `pm disable-user` (never uninstall) and record what was turned off in `/data/local/tmp/` on the TV.
- `undo.sh <TV_IP>`: turn back on everything the installer turned off, uninstall the app, go Home.

## Rules for you, the agent

1. Ask me before every change you make to the TV, and before installing anything on my computer.
2. Never uninstall system apps. Turn things off with `pm disable-user` and keep a list of how to undo each change.
3. Never turn off Google Play, the keyboard, Bluetooth or the HDMI inputs.
4. Never put API keys, passwords, IP addresses, my city, or keystores in the repository or in committed files. Use a `.gitignore` for the keystore, the build output and any local app list.
5. Do not copy the example's package name, name or look. Give my app its own package name, name and visuals.
6. If something fails, show me the real error and what you will try next. Do not hide failures.

## Check your work

- Build it and install it on my TV. Press Home and take a screenshot with `adb exec-out screencap -p`, and look at it.
- Check that Home opens the app, that the arrows move focus, that OK opens an app, and that holding OK opens the edit menu.
- Add `ci/smoke-test.sh`-style checks for what you built, and run them.
- Finish with: the code, the build and install scripts, a short README for my app that explains how to build it, install it and undo it, and the exact commands you ran on my TV so I can reverse them.
