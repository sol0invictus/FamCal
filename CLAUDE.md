# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FamCal — a publishable Android **family calendar** app. Everyone in a family uses the app;
events live in the app's own cloud (Firebase/Firestore) and sync in real time between members.
App name **FamCal**, applicationId **`com.famcal.app`** (permanent — chosen for the store).
Android-only, minSdk 26, targetSdk/compileSdk 35.

## Build & run

The normal workflow is **Android Studio** (bundles its own JDK 17): Open the folder → Sync → Run ▶.

Command-line builds need two env vars (Homebrew's default `openjdk` is JDK 26, which AGP 8.7
rejects — use `openjdk@21`):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
./gradlew :app:assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:compileDebugKotlin # fast compile check
```

There is no test suite yet. Gradle uses the Kotlin DSL with a version catalog
(`gradle/libs.versions.toml`) — add/adjust dependency versions there, not inline.

**`app/google-services.json` is required and gitignored.** The `google-services` plugin is
enabled, so the build fails without it. It's created in the Firebase console (see README).

## Architecture

**Stack:** Kotlin, Jetpack Compose + Material 3, single-activity, MVVM, Hilt DI,
Coroutines/Flow, Firebase Auth + Firestore + Messaging. Calendar grid via Kizitonwose
Calendar Compose. Uses `java.time` directly (minSdk 26).

**State-driven navigation (no global nav graph).** `MainActivity` → `FamCalRoot` observes
`AppViewModel.uiState` and switches the whole screen by app state:
`Loading → SignedOut (AuthScreen) → NeedsFamily (FamilySetupScreen) → Ready (MainNavHost)`.
`AppViewModel` derives this by `flatMapLatest` over `AuthRepository.authState`, then (when
signed in) observing the user's families. Sign-up/in/out and creating/joining a family have
**no explicit navigation** — they change Firebase/Firestore state, the observed flows update,
and `FamCalRoot` re-routes automatically.

**`MainNavHost(familyId)`** is the only `NavController`-based graph (calendar ↔ event editor ↔
settings). `familyId` is baked into every route, and each screen's `@HiltViewModel` reads it
from `SavedStateHandle` — that's how view models get the active family. The event route also
carries optional `eventId` (edit vs. create) and `dateMillis` (default date for a new event).

**Data layer = repositories wrapping Firestore, exposing `Flow`s via `callbackFlow` +
snapshot listeners.** This gives real-time cross-device sync for free. Suspend writes use
`kotlinx-coroutines-play-services` `await()`. Repositories: `AuthRepository`,
`FamilyRepository` (families, members, user docs, invites, feed token), `EventRepository`
(events). `FirebaseModule` (Hilt) provides the `FirebaseAuth`/`FirebaseFirestore` singletons.

### Firestore data model

```
users/{uid}                         displayName, email, photoUrl, familyIds[]
families/{familyId}                 name, ownerUid, memberUids[], inviteCode, feedToken
  members/{uid}                     displayName, email, role, colorIndex
  events/{eventId}                  title, notes, location, startAt, endAt, allDay,
                                    createdBy, reminderMinutes, recurrence, updatedAt
invites/{code}                      familyId, familyName, ownerUid
```

`firestore.rules` (root) is the source of truth for access; it must be published in the
Firebase console (or `firebase deploy --only firestore:rules`) — it is **not** auto-applied by
the app build. Key design: family/event data is **members-only**. Joining is the hard case —
a non-member can't read families, so a separate **`invites/{code}`** doc (fetched by id, `list`
denied) maps an invite code → familyId. `FamilyRepository.joinFamily` reads that, then does a
**blind** `arrayUnion` write adding only itself to `memberUids`; the update rule allows exactly
that (not a member before, is after, no existing member removed, size grows by one).

### Calendar specifics

- **Per-member colors:** an event is colored by its `createdBy` member's `colorIndex` into
  `MemberColors` (`ui/theme/Color.kt`). The UI builds a `uid → Member` map from the members
  subcollection; nothing about color is denormalized onto events.
- **Recurrence is expanded client-side.** Stored events carry `recurrence`
  (NONE/DAILY/WEEKLY/MONTHLY); `RecurrenceExpander.expand` turns them into `EventOccurrence`s
  over a window (today −2mo … +12mo). The calendar renders **occurrences, not raw events**.
  Editing/deleting affects the whole series (no per-instance exceptions).
- **Reminders** are local notifications. `ReminderScheduler` (AlarmManager
  `setAndAllowWhileIdle`, 7-day horizon) is driven from `CalendarViewModel` collecting its own
  `uiState`; `AlarmReceiver` posts the notification. Scheduling only happens while the app is
  open and does not survive reboot — a deliberate MVP limitation.
- Firebase/Firestore data classes need a no-arg constructor with defaults (all model classes
  follow this) and must not be obfuscated (relevant once R8/`proguard-rules.pro` matters).

### Cloud Functions (`functions/`)

Two functions, both needing the Firebase **Blaze** plan + `firebase deploy --only functions`:
- `calendarFeed` (HTTPS) serves a read-only **iCal feed** for a family, gated by the family's
  `feedToken` query param, so Google/Outlook can subscribe. Settings builds the URL from
  `R.string.project_id` (generated by the google-services plugin) + region + familyId +
  feedToken. Region default `us-central1` is duplicated in `SettingsViewModel.FUNCTION_REGION`.
- `notifyOnEventWrite` (Firestore trigger) sends FCM to other members' `fcmTokens` when an
  event is created/updated (M4 push).

## Other subsystems worth knowing

- **Multi-family:** a user can belong to several families. `ActiveFamilyStore` (SharedPreferences
  + StateFlow) holds the active one; `AppViewModel` emits `Ready(families, activeFamilyId)` and
  `FamCalRoot` wraps `MainNavHost` in `key(activeFamilyId)` so switching rebuilds the graph.
  Manage via Settings → Manage families (`FamiliesScreen`: switch/leave/add).
- **Device calendar mirror** (`data/calendar/`): the "auto-add to Google/Outlook" feature.
  `CalendarMirror` writes the active family's events into a chosen on-device calendar via
  `CalendarContract` (which the OS syncs up to that account). One-way; recurrence as RRULE;
  add/update/delete tracked by a content-hash mapping in `CalendarSyncStore` keyed
  `familyId:eventId`. Driven from `CalendarViewModel` (raw events, not occurrences). Needs
  READ/WRITE_CALENDAR (requested in Settings).
- **Push** (`notifications/`): `PushTokenRegistrar` saves the FCM token after sign-in;
  `FamcalMessagingService` (manifest-registered) handles refresh + incoming messages.

## Milestone status

**Code-complete and verified building:** M0 skeleton, M1 auth+family, M2 calendar+events,
M3 reminders/recurrence/settings, family management (switch/leave/join more), M4 push,
the iCal feed, device-calendar mirror, and M5 release-prep engineering (release signing,
Crashlytics, reboot-safe reminders via WorkManager, hardened Firestore rules, in-app account
deletion, privacy-policy draft). The signed release AAB builds via `./gradlew :app:bundleRelease`
(signing reads gitignored `keystore.properties` → `app/upload-keystore.jks` — back these up).
M1+M2 confirmed on-device by the user; push + feed need the functions deployed; device mirror
not yet device-tested.

**Not yet done:** M5 account/listing tasks that only the owner can do (Play Console account,
host the privacy policy, store listing, data-safety form, upload to internal testing),
monetization (Play Billing), and M6 (full two-way Outlook/Gmail sync via Graph/Calendar APIs,
deferred due to Google verification/CASA cost). README has the full roadmap and release steps.

After changing `firestore.rules`, the owner must re-publish them (console or
`firebase deploy --only firestore:rules`) — they are not applied by the app build.
