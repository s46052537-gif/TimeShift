# TimeShift

An Android app that opens any website in a built-in browser and changes the page's clock:

- **Speed 1x to 100x**: the page's Date, performance.now, setTimeout, setInterval and requestAnimationFrame all run faster.
- **Jump forward N hours**: the page's Date.now() moves ahead instantly.
- **Page time** shows what the page believes the time is. **Reset** restores the real clock.

## Limits (important)
- It changes the clock only inside the page, on your device. If the site's server tracks time (crops ready, rewards, balances), the server ignores it and nothing changes. It only works on sites that count time in the browser.
- Settings survive reloads, but speed-accumulated time restarts at real time plus your total jumps after a reload.
- Not compiled or tested yet. Treat the first build as untested.

## Build the APK without coding (GitHub, free)
1. Create a GitHub account and a new empty repository.
2. Upload this entire folder (including the hidden `.github` folder) to the repository.
3. Open the Actions tab, pick "Build APK", press Run workflow.
4. When it finishes, download the `TimeShift-debug-apk` artifact, unzip it, and install `app-debug.apk` on your phone (allow installs from unknown sources).

## Or with Android Studio
Open this folder, wait for Gradle sync, then Build > Build APK(s).

Versions used (not verified in this session): Android Gradle Plugin 8.5.2, Kotlin 1.9.24, Gradle 8.7, androidx.webkit 1.11.0, compileSdk 34.
