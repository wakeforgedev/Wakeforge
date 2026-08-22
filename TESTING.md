# Getting Wakeforge onto a friend's phone (before the Play Store)

You don't need a Play Console account, closed testing, or Android Studio to let
friends try Wakeforge right now — sideloading a debug APK works today. This is
also the same build the automated GitHub Action below produces.

## One-time setup (you, ~10 minutes)

1. Create a free GitHub account if you don't have one: https://github.com/join
2. Create a new **public** repository (name it `Wakeforge` or anything you like).
   Public matters here — it's what makes the download link work for friends
   without them needing a GitHub login too.
3. From a terminal on your Mac, inside the `Wakeforge` project folder:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/Wakeforge.git
   git push -u origin main
   ```
4. That push triggers the `Build shareable APK` GitHub Action automatically
   (see `.github/workflows/build-apk.yml`). Go to the **Actions** tab on your
   repo page and watch it run — it takes a few minutes the first time.
5. Once it finishes, go to the **Releases** section of your repo (right-hand
   sidebar on the repo page, or `github.com/<you>/Wakeforge/releases`). You'll
   see a `.apk` file attached to the newest release. Right-click it and copy
   the link, or just share the Release page link directly.

Every time you push a change, a new build + Release is created automatically
— no need to repeat these steps, just `git add . && git commit -m "..." && git
push` and a fresh APK link shows up a few minutes later.

## What you send your friends

Just the link to the `.apk` file (or the Release page). WhatsApp, email, a
Drive link — any of them work, since it's a normal file.

## What your friends need to do to install it

Android blocks installing apps from outside the Play Store by default — this
is expected and not a bug:

1. Open the link on the phone and download the `.apk`.
2. Tap the downloaded file. Android will show a warning like *"For your
   security, your phone is not allowed to install unknown apps from this
   source"* — tap **Settings** on that prompt, then enable **Allow from this
   source** for whichever app they downloaded through (Chrome, Files, etc.).
3. Go back and tap the file again, then **Install**.
4. They may also see a Google Play Protect warning since the app isn't
   Play-verified yet — that's expected for a pre-launch test build. They can
   tap **Install anyway**. (This is exactly why the closed-testing step
   exists later — once Wakeforge is in Play Console's testing track, this
   warning goes away for testers.)

## What this build is (and isn't)

This is a **debug build**, signed with Android's default debug key, not a
Play-Store-ready release build. That's fine for friend testing — it's not
fine for an actual Play Store submission, which needs its own signing key
(Android Studio generates one, or Play App Signing can manage it — a separate
step from this workflow, needed only once you're ready to submit).
