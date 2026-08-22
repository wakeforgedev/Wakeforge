# RiseUp — Monetization Plan

## The honest data first

Before picking a model, the numbers are worth seeing plainly. RevenueCat's 2026 State of
Subscription Apps report (115,000+ apps) found a stark gap between two common approaches:

- **Hard paywall** (pay before you can really use the app, usually behind a free trial):
  median Day-35 trial-to-paid conversion of **10.7%**, and **$3.09 revenue-per-install** at
  day 60.
- **Freemium** (use it free indefinitely, pay to unlock more): median Day-35 conversion of
  just **2.1%** — about 5x lower — and **$0.38 revenue-per-install**, about 8x lower.
- Trial *length* also matters a lot: 17-32 day trials convert at 42.5%, versus 25.5% for
  trials under 4 days.

That data argues hard for paywalling early. But it's measuring apps that already know
people want them. RiseUp doesn't have that yet — nobody has used the streak mechanic, the
Camera mission, or any of it in the real world. Paywalling something unvalidated just
means fewer people ever find out whether it works. So the recommendation below is
deliberately staged rather than jumping straight to the highest-converting model.

## Recommended path: free first, paywall once retention is proven

**Stage 1 (now — first 4-6 weeks with real users):** Ship it fully free, exactly as it is.
The goal isn't revenue yet, it's answering the question from a few messages ago — does
anyone actually keep doing this unprompted. Watch day-7 retention and streak length. If
people aren't coming back on their own for free, a paywall won't fix that; it'll just
hide the problem behind a purchase screen.

**Stage 2 (once you see real retention):** Introduce a paywall using the data above —
specifically a short free trial (aim for 2+ weeks, since longer trials convert
substantially better) followed by a subscription, rather than an indefinite free tier.
This is where Google Play Billing gets built (see "What's not built yet" below).

## What's free vs. paid, once Stage 2 happens

**Free:** Math and Shake missions, one category, unlimited alarms, the streak/XP system
(the free tier needs to be good enough that people form the habit — that's what creates
the reason to eventually pay for more).

**Premium (subscription):** The Camera mission (it's the most engineering-intensive
feature and the clearest "wow" differentiator — Early charges specifically for this kind
of thing and it works), all three categories unlocked, every difficulty tier, and future
additions (cloud sync, age-tier presets, custom themes) land here by default.

## Pricing

Early (the comparable app from your original question) prices at **$9.99/month or
$29.99/year** in the US. Direct research on India-specific consumer subscription pricing
is thin, but the general guidance that does exist is consistent and clear: "domestic
[app] pricing often runs 30-70% below US price points," and ignoring that costs 20-40% of
the addressable market on one side or the other. Translating Early's anchor down for an
India-primary launch suggests roughly **₹99-149/month or ₹799-999/year** as a starting
point — cheap enough to be an impulse purchase, not so cheap it reads as low-value. Treat
this as a hypothesis to A/B test once you have paying-stage traffic, not a final number.

## The real math on what you keep

Google Play's subscription fee is effectively **15%** for any developer under $1M/year in
revenue — a 10% "service fee" plus a 5% "billing fee" for using Play's payment system,
with no separate small-business discount tier the way headlines sometimes suggest (and
importantly, no graduation cliff for subscriptions specifically — the rate doesn't jump
once you cross a revenue threshold, unlike some other Play program tiers). A ₹149/month
subscription nets you roughly ₹127/month per subscriber after Google's cut.

Worked example: 2,000 free users, 3% convert to paid (a reasonable freemium-adjacent
number given the guidance that "conversion to paid is 2-8%" makes freemium-style pricing
viable) at ₹149/month → 60 paying subscribers → roughly ₹7,600/month after Play's fee.
Small, but it's the shape of the curve that matters early — this scales with your user
base, and the point of Stage 1 is growing that base before worrying about the multiplier.

## Beyond consumer subscriptions — the bigger opportunity worth flagging

Two things specific to this app and this market are worth more than a footnote:

**Institutional / B2B for the Student category.** India's exam-coaching ecosystem (Kota
and equivalents nationwide) is enormous, and "wake up and actually study" is exactly the
kind of accountability tool a coaching institute would pay for in bulk — a white-labeled
or co-branded version sold per-institute is a fundamentally bigger check size than
consumer subscriptions, and it's a natural fit for exactly the Student category already
built. Worth a real conversation with one local coaching center once the consumer app has
any traction to point to.

**What to explicitly avoid: ads.** This is worth stating plainly rather than leaving
implicit — do not put ads on the alarm/mission screens. This is an app whose entire value
proposition is "gets you to do something focused while half-asleep or mid-workout";
serving ads on that exact screen directly undermines the streak/retention mechanic this
plan depends on, for very little revenue relative to what it costs in trust and churn.

## What's not built yet (be aware before promising a launch date)

The app currently has **zero payment code** — it's 100% free/local by design so far. Real
IAP requires integrating Google Play's Billing Library: product/subscription setup in
Play Console, a `BillingClient` implementation, purchase verification, entitlement
storage, and a restore-purchases flow. That's a genuine, moderate-sized feature — budget
something like 1-2 focused weeks for a solo developer, not a quick add-on — and it's
correctly sequenced *after* Stage 1 validation, not before.
