# RiseUp — Category-Based Wake-Up Mission Alarm (Android MVP)

This is a real, working Android Studio project — not a mockup. It implements the Phase 1 MVP
from the feasibility plan: a reliable alarm engine, two mission types (Math and Shake),
three category presets (Student / Gym / General), and a basic alarm list that doubles as
your "calendar of events" (each entry is a titled event with a time, category, and mission).

This revision adds two things that were explicitly requested: local data security
hardening (encrypted database, no backup exposure, no network capability at all — see
**`SECURITY.md`** for the full writeup, including why "end-to-end encryption" specifically
doesn't apply to a backend-less app yet, and what to add when one exists) and a UI/UX pass
aimed at Gen Z/millennial retention patterns — Material 3 dynamic color, category-themed
gradients, and a streak/XP system (see **`DESIGN.md`**).

## Why there's no APK included

This was built in a sandboxed Linux environment with no access to the Android SDK or Google's
distribution servers, so it could not be compiled or wrapper-generated here. **You'll need to
open it in Android Studio to build and run it** — that's the normal workflow anyway (Android
Studio manages the SDK and Gradle for you automatically).

## How to open and run it

1. Install [Android Studio](https://developer.android.com/studio) (free) if you don't have it.
2. Open Android Studio → **Open** → select this `RiseUp` folder.
3. Android Studio will offer to generate the Gradle wrapper and sync the project automatically
   — accept it. First sync can take a few minutes as it downloads the Android SDK components.
4. If Android Studio prompts to **upgrade the Android Gradle Plugin**, accept that too — this
   project targets a recent-but-not-bleeding-edge AGP version (8.5.2) for broad compatibility,
   and Studio will happily bump it to whatever it currently ships with.
5. Connect a physical Android phone (with USB debugging enabled) or start an emulator, then
   press **Run**.
6. On first launch, Android will ask for notification permission and (on Android 12+) you'll
   need to grant "Alarms & reminders" — the app prompts you to do this the first time you save
   an alarm.

## What's implemented

- **Alarm engine:** `AlarmManager.setAlarmClock()` (the same reliability tier the stock Clock
  app uses — exempt from Doze/battery restrictions), a foreground service that rings/vibrates,
  a full-screen mission activity that shows over the lock screen, and a boot receiver that
  reschedules alarms after a restart.
- **Missions:** Math (arithmetic problem, difficulty-scaled), Shake (accelerometer-based
  shake counter, difficulty-scaled target), and Camera (opens the camera for a
  user-configurable duration — 1/5/10/15 minutes, set per-alarm — and watches for real
  movement via frame-brightness motion detection; confirms once enough active movement
  accumulates, or auto-falls-back to a Math mission if the whole duration elapses without
  enough confirmed movement, so the user is never stuck with a dead camera check). All
  three are implemented as clean, additive branches — see the comment in
  `AlarmActivity.kt` for how a further mission type slots in.
- **Categories:** Student / Gym / General — picking a category pre-selects a sensible default
  mission (Gym → Shake, others → Math), which the user can override.
- **Calendar-ish event list:** every alarm is really a titled event; the list is sorted by
  time and shows category + mission at a glance.
- **Encrypted local storage:** the database is SQLCipher-encrypted (AES-256), the
  passphrase is Keystore-protected, auto-backup is off, and screens with personal data
  are excluded from screenshots/recents. Details and rationale in `SECURITY.md`.
- **Streak + XP:** every completed mission is logged locally and turned into a day-streak
  and XP total shown at the top of the main screen. Details in `DESIGN.md`.
- **Material 3 dynamic color + category-themed gradients** on the mission screen.

## What's intentionally NOT in this MVP (see the feasibility doc for the roadmap)

- Rep-counted camera missions (verified push-ups/squats specifically) — the Camera mission
  now shipped confirms general movement, not a specific exercise; upgrading to real
  pose-based rep counting (like Early's push-up check) is a drop-in replacement for
  `CameraMotionAnalyzer.kt` alone, nothing else in the flow needs to change.
- Mic-based "read aloud" mission for students — Phase 2, needs speech matching.
- Age-tier presets beyond the three categories (e.g. a senior/medication-photo mission).
- iOS version — requires a Mac with Xcode, which wasn't available in the build environment.
- Two-way sync with Google/Apple Calendar.
- Any backend/cloud sync — everything is local to the device (Room database) for now.

## Project structure

```
app/src/main/java/com/buddy/riseup/
  AlarmEvent.kt        the data model (title, time, category, mission, difficulty)
  AlarmDao.kt           Room queries
  AppDatabase.kt        Room database singleton — now opened via SQLCipher (see SECURITY.md)
  Converters.kt         Room type converters for the enum fields
  SecureKeyManager.kt    generates/stores the DB passphrase via Android Keystore
  MissionLog.kt          one row per completed mission (powers streak/XP)
  MissionLogDao.kt        Room queries for mission logs
  StreakCalculator.kt     pure logic: logs -> streak days + total XP
  AlarmScheduler.kt     wraps AlarmManager scheduling/cancelling
  AlarmReceiver.kt       receives the AlarmManager broadcast, starts the ring service
  AlarmRingService.kt    foreground service: sound + vibration + full-screen notification
  BootReceiver.kt        reschedules alarms after a device reboot
  ShakeDetector.kt       accelerometer-based shake counter
  CameraMotionAnalyzer.kt frame-brightness motion detector for the Camera mission
  AlarmActivity.kt        the full-screen mission UI shown when the alarm fires
  MainActivity.kt         the alarm/event list + streak/XP header + "add alarm" flow
  AlarmAdapter.kt         RecyclerView adapter for the list
  RiseUpApp.kt            Application class, applies Material You dynamic color
```

## How this was actually verified (read this before assuming it's untested)

Neither this cloud environment nor the bridge into your Mac has network access to Google's
Android SDK servers, so a real Gradle build could not be run in either place — that part is
genuinely not done, and the first real signal will be Android Studio's sync/build on your
machine. What *was* done as a substitute:

- Every `binding.<name>` reference in the Kotlin files was manually cross-checked against
  the actual `android:id` in the corresponding layout XML, one by one.
- Room's lack of native enum support was caught before it would have caused a real build
  failure — `Converters.kt` and `@TypeConverters` were added specifically because `Category`
  and `MissionType` wouldn't otherwise be persistable.
- The SQLCipher + Room wiring follows the documented integration pattern
  (`SupportFactory` passed to `openHelperFactory`) rather than an improvised approach.
- All new/changed files were read back after editing to check imports match usage.

Treat the first Android Studio sync as the actual test, not this review — if it flags
anything (most likely candidate: a dependency version that's since been superseded, since
these were current as of this build but Android tooling moves fast), it's a quick fix, not
a sign the architecture is wrong.

## A known rough edge worth testing early

The "block back button until mission is solved" behavior in `AlarmActivity` is deliberate, but
volume-down can still silence the ringtone as an escape hatch (standard Android behavior for
any app, not something this code adds). Decide with real users whether that's acceptable or
whether you want to actively counter it — that's exactly the kind of thing worth learning from
your first 10-20 testers rather than guessing upfront.
