# Puck Launcher

A personal Android launcher for the NHL section of onhockey.tv: today's games with big touch targets, start times in Japan time (JST), and one or two taps to a stream. Built for a Samsung Galaxy S24 Ultra, both on the phone and in Samsung DeX. Personal use only; not on the Play Store.

## Install on the phone
1. Download the APK on the phone and tap it.
2. If asked, allow that app (My Files or the browser) to install unknown apps.
3. If Play Protect warns, tap **More details → Install anyway**.

Updates install over the existing app only if they are signed with the same debug key.

## Requirements
JDK 17 and Android SDK 35.

Tonight's NHL games from onhockey.tv, big touch targets, 1–2 taps to a stream. Phone + Samsung DeX.

## Build
```
./gradlew testDebugUnitTest            # parser / rollover / NHL-merge / screenshot tests
LIVE_CHECK=1 ./gradlew testDebugUnitTest --tests '*LiveFetchTest*' --rerun   # hits the real sites
./gradlew recordPaparazziDebug         # re-render screenshots -> app/src/test/snapshots/images
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk (signed with ~/.android/debug.keystore)
```
Keep `/home/box/.android/debug.keystore`: later builds only install over v1 if they use the same key.

## Layout
- `data/OnHockeyParser.kt`: Jsoup parser for `schedule_table.php` (NHL tbody, Away - Home, UTC HH:MM → Instant with rollover, feed groups).
- `data/NhlApi.kt`: `api-web.nhle.com/v1/score/{ET date}` parse + merge (status, score, period, authoritative start time).
- `data/ScheduleRepository.kt`: OkHttp, `Referer: https://www.onhockey.tv/`, windows-1251 decode; NHL API failure is non-fatal.
- `data/Teams.kt`: 32-team alias table (onhockey names are inconsistent).
- `ui/PuckApp.kt`: Compose UI. Compact = 1 column + full-screen feeds; ≥760dp wide (S24U landscape, DeX) = adaptive 320dp grid + side feeds pane.
- `PlayerActivity.kt`: beta WebView player (popup/redirect/ad blocking, fullscreen video, Open in browser).
- `Browser.kt`: Custom Tabs → default browser (Samsung Internet / Chrome), fallback ACTION_VIEW.
