# FamCal

A shared **family calendar** for Android. Everyone in the family uses the app;
events live in a shared cloud store and sync in real time across members' phones.

- **App name:** FamCal
- **Package / applicationId:** `com.famcal.app`
- **Min Android:** 8.0 (API 26) · **Target:** API 35

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, single-activity, Navigation Compose |
| DI | Hilt |
| Async | Coroutines + Flow |
| Backend | Firebase Auth + Cloud Firestore + FCM *(wired in M1)* |
| Calendar grid | Kizitonwose Calendar for Compose *(used in M2)* |

## Roadmap

- **M0 — Project skeleton** ✅ runnable Compose app, Hilt, theming, navigation, launcher icon.
- **M1 — Auth + family** ✅ *(code complete — needs your Firebase project)* email/Google sign-in, create/join family via invite code, Firestore security rules.
- **M2 — Core calendar** ✅ month view (Kizitonwose), create/edit/delete events, real-time sync, per-member colors, agenda for the selected day.
- **M3 — Polish** ✅ reminders (local notifications), recurring events (daily/weekly/monthly), settings screen.
- **Device calendar sync** ✅ Settings → *Sync to this phone's calendar* mirrors family events into a Google/Outlook account already on the phone (via `CalendarContract`), which the OS then syncs up. Automatic, one-way, per-device, no server. The preferred "auto-add to Google/Outlook" path.
- **Calendar feed** ✅ *(code complete — needs Blaze plan + deploy)* read-only iCal feed via a Cloud Function so the calendar can be subscribed to from Google/Outlook; subscribe URL appears in Settings. (Alternative to device sync, e.g. for desktop-only accounts.)
- **Family management** ✅ switch between families, leave a family, create/join more (Settings → Manage families).
- **M4 — Push** ✅ *(code complete — needs Blaze + deploy)* FCM; the `notifyOnEventWrite` Cloud Function pushes to other members when an event is added/changed. Deployed together with the feed via `firebase deploy --only functions`.
- **M5 — Release prep** 🚧 *(engineering done)* release signing, Crashlytics, reboot-safe reminders, hardened security rules, in-app account deletion, privacy policy draft. Remaining is account/listing setup (below).
- **M6 — Outlook/Gmail two-way import** — optional sync via Microsoft Graph + Google Calendar API.

## Building a release

Release signing reads `keystore.properties` (gitignored) which points at `app/upload-keystore.jks`.
**Back up both files** — losing them means you can't publish updates under the same upload key.

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
./gradlew :app:bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab (upload this)
```

## Publishing to Google Play (the parts only you can do)

1. **Re-publish the security rules** (they were tightened) — paste `firestore.rules` into the
   Firebase console → Firestore → Rules, or `firebase deploy --only firestore:rules`.
2. **Host the privacy policy** (`PRIVACY_POLICY.md`) at a public URL (e.g. GitHub Pages) and
   have it reviewed.
3. **Create a Google Play Console account** ($25 one-time) and a new app.
4. **Store listing:** app icon, screenshots (phone), short + full description, feature graphic.
5. **Data safety form** (declare: name, email, calendar events, push token) and **content rating**.
6. Add the **privacy policy URL** and the in-app account-deletion path (Settings → Delete account).
7. Upload `app-release.aab` to the **internal testing** track, add yourself as a tester, install,
   and use it for a while before promoting to production.
8. To actually charge for it: set up a **payments/merchant profile** and (for subscriptions/IAP)
   integrate **Google Play Billing** — a separate piece of work.

## Add FamCal to Google Calendar / Outlook (calendar feed)

FamCal can publish a private read-only iCal feed that Google Calendar and Outlook can
subscribe to. It's served by a Cloud Function, which requires the Firebase **Blaze**
(pay-as-you-go) plan — usage for a family is well within the free allowances (~$0/mo).

**One-time deploy:**
1. Install [Node.js](https://nodejs.org/) (LTS) and the Firebase CLI: `npm install -g firebase-tools`.
2. `firebase login` (opens a browser).
3. In the project root: `firebase use --add` and pick your FamCal project.
4. Upgrade the project to **Blaze** in the Firebase console (Billing) — required for functions.
5. Deploy: `firebase deploy --only functions`.
6. (Optional) deploy rules too: `firebase deploy --only firestore:rules`.

**Subscribe:**
- Open the app → **Settings** → copy the **subscription URL**.
- **Google Calendar** (web): Other calendars → **＋** → **From URL** → paste → Add.
- **Outlook** (web): **Add calendar** → **Subscribe from web** → paste → import.

The feed refreshes on Google/Outlook's own schedule (often several hours) — that's a
limitation of calendar subscriptions, not FamCal.

## Getting started

### 1. Install Android Studio
Download the latest **Android Studio** (https://developer.android.com/studio) and
install it. On first launch let it install the **Android SDK** (API 35) and create
an **emulator** (Pixel device, recent system image), or enable USB debugging on a
physical phone.

> Android Studio bundles its own JDK and Gradle, so you don't need to install Java
> separately to build from the IDE.

### 2. Open the project
`File → Open` and select this folder. Let Gradle sync finish.

### 3. Run
Pick a device/emulator and press **Run** ▶. You should see the FamCal placeholder
calendar screen. (Firebase is not required to run M0.)

## Firebase setup (required to run M1)

The Firebase dependencies and the `google-services` plugin are already enabled, so the
app **won't build until `app/google-services.json` exists.** One-time setup:

1. Go to the [Firebase console](https://console.firebase.google.com/) and create a
   project named **FamCal** (Google Analytics optional).
2. Add an **Android app** with package name **`com.famcal.app`**.
3. Download the generated **`google-services.json`** and drop it into the **`app/`** folder.
4. **Authentication → Get started →** enable the **Email/Password** provider. *(Optional:
   also enable **Google** for the "Continue with Google" button — this auto-creates the
   web client the app needs; without it that button just shows a friendly error.)*
5. **Firestore Database → Create database** (Production mode, pick a region).
6. **Firestore → Rules:** paste the contents of [`firestore.rules`](firestore.rules) and
   **Publish**.
7. Rebuild/run from Android Studio. Sign up, create a family, and you'll see your invite code.

`google-services.json` contains no secret keys, but it's project-specific — don't commit
it to a public repo.

## Project layout

```
app/src/main/java/com/famcal/app/
  FamCalApplication.kt        # Hilt application entry point
  MainActivity.kt             # single activity, hosts Compose
  ui/theme/                   # Material 3 theme (colors, type)
  ui/navigation/              # NavHost + route definitions
  ui/calendar/                # calendar screen (placeholder in M0)
```

## Building from the command line (optional)

The repo includes a Gradle wrapper. A debug build:

```
./gradlew :app:assembleDebug
```

This requires a JDK 17–21 and the Android SDK (set `sdk.dir` in `local.properties`,
which Android Studio creates automatically).
