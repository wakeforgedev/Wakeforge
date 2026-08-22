# Wakeforge Security Notes

## What "end-to-end encryption" actually means, and why it doesn't apply yet

End-to-end encryption (E2EE) is a *client-server* concept: it describes data encrypted on
one device so that only another intended device can decrypt it — critically, so that the
server relaying it in between can't read it either (this is how Signal/WhatsApp messages
work). **Wakeforge has no server.** There is nothing in the middle to encrypt data *against*.
Claiming "E2EE" for a fully local app would be a meaningless label, so instead this build
does the thing that's actually real for a local-only app: strong **encryption at rest**,
plus removing the network capability entirely so there's no transmission path to secure
in the first place.

If/when you add cloud sync (so alarms follow you across devices, or a caregiver-sharing
feature like the eldercare idea from earlier), that's the point to revisit this file and
implement real E2EE — see "When you add a backend" below.

## What is implemented now

**1. No network capability at all.**
The manifest declares no `INTERNET` permission. This isn't a policy promise, it's a
platform-enforced guarantee — Android will not let this app open a socket, full stop. Zero
attack surface for interception, zero possibility of a server-side leak, because there is
no server and no wire. This is the single strongest thing done here, and it's free.

**2. The local database is encrypted at rest (SQLCipher, AES-256).**
Previously `wakeforge.db` was a plain SQLite file — trivially readable by anyone with root
access or an unencrypted device backup. It's now opened through SQLCipher
(`AppDatabase.kt`), so the file on disk is encrypted. Even pulling the raw `.db` file off
the device gets you ciphertext, not alarm titles or routines.

**3. The database key never exists in plaintext outside memory.**
`SecureKeyManager.kt` generates a random 256-bit passphrase once, then stores it inside
`EncryptedSharedPreferences`, which is itself protected by a key held in the device's
Android Keystore (hardware-backed secure element on most modern phones). The passphrase
is decrypted into memory only for the moment it's needed to open the database — it's never
logged, never written to a plain file, and isn't something even a rooted-device
extraction can trivially recover.

**4. Auto-backup is disabled (`android:allowBackup="false"`).**
Previously the app allowed Android's automatic backup, which — even though it doesn't
usually include Keystore-protected data — is an unnecessary extra copy of your data
sitting in Google's backup infrastructure. Turned off entirely for this local-only MVP.

**5. Camera mission frames are never stored, copied, or transmitted.**
The Camera mission (see `CameraMotionAnalyzer.kt`) reads each frame's brightness in memory,
compares it to the previous frame, and immediately discards it (`image.close()` in a
`finally` block, guaranteed even on error) — no frame is ever written to disk, added to
the gallery, or sent anywhere. Combined with point 1 (no `INTERNET` permission at all),
there is no code path by which a camera frame could leave the device even in principle.

**6. Screens that show personal data are excluded from screenshots/recents.**
`FLAG_SECURE` is set on both `MainActivity` and `AlarmActivity`. This blocks screenshots,
screen recordings, and the thumbnail Android shows in the "Recent Apps" switcher — so a
routine titled with something personal (a medication name, a private goal) can't leak
through the OS-level app switcher or an accidental screen share.

## What was checked and found to already be fine

- **Permissions are minimal and each is justified** — exact alarms, notifications,
  vibration, wake lock, and boot-completed are all required for the alarm to actually work
  reliably; none of them expose data, they just let the app do its one job.
- **No third-party analytics/ad SDKs** are in the dependency list — nothing is phoning
  home usage data, because nothing can phone home at all (see point 1).
- **No PII is logged.** There's no `Log.d`/`Log.i` call anywhere that prints event titles,
  timestamps, or anything else user-entered.

## When you add a backend (real E2EE, done properly)

Whenever you build cloud sync, cross-device access, or caregiver sharing, this is the
checklist to actually deliver on "end-to-end encrypted":

1. **TLS everywhere, no exceptions** — enforce via a Network Security Config that
   disallows cleartext traffic entirely (don't rely on developers remembering to use
   `https://`).
2. **Encrypt sensitive fields client-side before they ever leave the device**, using a key
   derived from something only the user holds (e.g. a passphrase-derived key, or per-device
   keys exchanged directly between devices) — so your own backend, even if compromised,
   only ever sees ciphertext. This is the actual definition of E2EE; TLS alone only
   protects data *in transit* to your server, not *from* your server.
2. **Don't roll your own crypto protocol.** Use an established library/pattern (e.g. the
   Signal protocol's double-ratchet for anything resembling messaging, or a vetted library
   like libsodium/Tink for simpler field-level encryption).
3. **Certificate/public-key pinning** for the app's API calls, so a compromised CA or a
   MITM proxy on a public Wi-Fi network can't intercept traffic even over TLS.
4. **Minimize what's collected server-side at all** — if the server never needs to see a
   plaintext routine title to do its job (e.g. it just needs to route an encrypted blob
   to the right device), don't design an API that requires it to.
5. **Independent security review before launch** — this document is a solid baseline, not
   a substitute for an actual audit once real user data and a backend are involved.
