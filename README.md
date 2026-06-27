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
- **Calendar feed** ✅ *(code complete — needs Blaze plan + deploy)* read-only iCal feed via a Cloud Function so the calendar can be subscribed to from Google/Outlook; subscribe URL appears in Settings.
- **M4 — Push** — FCM + Cloud Function notify members of changes.
- **M5 — Release** — icon, Play listing, privacy policy, signing, internal testing track.
- **M6 — Outlook/Gmail two-way import** — optional sync via Microsoft Graph + Google Calendar API.

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
