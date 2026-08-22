# RiseUp Design Notes — UI/UX pass

## What changed in this pass

**1. Material 3 + Material You dynamic color.**
The app now runs on `Theme.Material3.DayNight` instead of the older Material Components
theme, and `RiseUpApp.kt` applies Android's dynamic color system across every screen. On
Android 12+, that means the app's accent colors adapt to each user's actual wallpaper/
system theme — it stops looking like a fixed corporate palette and starts looking
personalized, which is closer to what this audience expects from a modern app (this is
the same system behind most apps that feel "native" to Android 12+ rather than dated).

**2. A distinct visual mood per category, not one generic alarm screen.**
The mission screen now shows a full gradient background that changes by category — warm
orange/red for Gym, cool blue/violet for Student, calm teal for General (see
`gradient_*.xml` in `res/drawable`). This is a small change with outsized effect: it
makes the "unique stopping style per category" idea from the original spec *visible*
immediately, not just mechanically different.

**3. Streaks and XP.**
This is the single highest-leverage retention pattern for this audience — Duolingo,
Snapchat, and most habit apps that succeed with Gen Z/millennial users lean hard on "don't
break the chain" and visible point totals. `MissionLog.kt` + `StreakCalculator.kt` track
every completed mission and compute a day-streak and total XP, shown at the top of the
main screen. It's intentionally simple (no leaderboards, no social features yet) — those
are natural next steps once there's a backend to support them, but they also reopen the
security questions in `SECURITY.md`, so they're deliberately out of scope for this pass.

**4. Motion.**
A subtle pulse animation on the alarm title while it's ringing (`AlarmActivity.kt`) —
small, but static full-screen alarm UIs read as dated. This is a first step, not a final
one; a proper pass would add a completion celebration animation (confetti/haptic burst)
on finishing a mission, which is a good next addition once you have a device to actually
watch it play.

## Second pass — replacing basic widgets with real Material 3 components

The first pass covered theming and motion; this pass replaces the visually plain default
widgets that were still doing the actual UI work underneath (plain `RadioGroup`, plain
`ProgressBar`, a bare `EditText`, a text link for delete) with the Material 3 components
built for this:

- **Category and mission pickers** are now `MaterialButtonToggleGroup` segmented buttons
  with an emoji + label per option (📚 Student, 💪 Gym, ⭐ General / ➕ Math, 📳 Shake,
  🎥 Camera), instead of plain radio buttons — the selected option now visually fills in,
  which is both clearer and reads as considerably more current.
- **The alarm title field** is a proper Material outlined `TextInputLayout`, not a bare
  underlined `EditText`.
- **The alarm list** replaces the small color dot with a soft tinted circular icon badge
  per category (same emoji system as the pickers, for consistency end to end), a rounded
  20dp card, and an icon-style delete button instead of a text link.
- **Progress bars** (Shake and Camera missions) are now `LinearProgressIndicator` with
  rounded track ends instead of the default square-edged system `ProgressBar`.
- **The main screen's streak/XP row** became two soft-colored pill cards instead of a
  plain text line, and the FAB became an extended, labeled FAB ("Add alarm") instead of a
  bare icon button — small, but labeled FABs measurably reduce "what does this button do"
  hesitation for new users.
- **The mission screen** now shows a large, low-opacity category emoji watermark at the
  top, so which kind of alarm this is registers before the user even reads the text.

None of this changed the underlying data model or mission logic — it's purely the
presentation layer, using Material Components APIs that ship in the `material` library
already in `build.gradle` (no new dependency was needed for any of it).

## On "attractive for Gen-Z/millennials in every continent" — an honest scope note

Different regions and generations genuinely do respond to different color meanings,
imagery, pacing, and even what "gamified" should feel like (a streak mechanic that reads
as fun in one market can read as stressful/predatory in another). That's real, and worth
taking seriously — but it's also not something to fake with a single design pass. What
*was* done here is the structural groundwork that makes real regional adaptation possible
later without a rewrite:

- All user-facing text lives in `res/values/strings.xml`, which is exactly the file
  Android's localization system expects — adding `values-hi/`, `values-pt/`, `values-ar/`,
  etc. with translated strings is additive, not a refactor, whenever you're ready to
  localize for a specific market.
- Category colors and gradients are centralized in `colors.xml` / the `gradient_*.xml`
  drawables, not hardcoded inline — so building alternate palettes per region (or even
  per-user selectable themes) is a matter of adding resource variants, not touching logic.
- Dynamic color (point 1 above) already gives every user *some* degree of personalization
  for free, without needing to guess at a single "universal" palette.

**What this pass did not do**, and what I'd treat as separate, deliberate follow-up work
rather than something to rush: actual translated strings for specific languages, region-
specific imagery/iconography research, or validating gamification mechanics with real
users in each target market. That's genuinely a research task (talking to actual Gen Z
users in, say, Brazil vs. Nigeria vs. India about what feels motivating vs. annoying) more
than a coding task, and it deserves real input rather than my guessing at cultural
preferences by region.
