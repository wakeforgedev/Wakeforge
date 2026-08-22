# Wakeforge — Locked-in pricing & plan structure (v1)

This replaces the pricing *range* in `MONETIZATION.md` with an actual decision to launch
with. Treat it as the starting number to test with real testers, not something carved in
stone — but you need one number to put in Play Console, and guessing forever isn't a
strategy either. Here it is, with the reasoning, so you can defend it or change it later.

## The plan: Free vs. Wakeforge Pro

**Free — forever, not a trial:**
- Unlimited alarms, all three categories (Student / Gym / General)
- Math and Shake missions
- Full streak + XP tracking

This has to be genuinely good on its own. The entire premium pitch depends on people
already having formed the daily habit before you ever ask them to pay — see
`MONETIZATION.md`'s Stage 1 reasoning. A stingy free tier kills that before it starts.

**Wakeforge Pro — the paid tier:**
- The Camera mission (all duration options, 1 min–1 hour)
- All difficulty tiers
- First access to anything added later — content-aware Student/Gym missions, adaptive
  difficulty, age-tier presets (see the roadmap discussion) all land here by default

Camera is the right thing to gate: it's the most engineering-intensive feature, the one
that took real work to build, and the one that actually differentiates Wakeforge from a
generic alarm app. Gating Math/Shake instead would just make the free app annoying,
which drives uninstalls, not conversions.

## Price

**₹99/month, or ₹699/year (≈₹58/month — a 41% discount for paying annually).**

Reasoning: Alarmy's own premium unlock anchors around $9.99/mo in the US. The general
guidance on India-specific app pricing (not Alarmy-specific data, general market
guidance) is 30–70% below US price points for a domestic launch — ₹99 lands solidly in
that band and sits just under the ₹100 mark, which matters more than it sounds for an
impulse purchase. It's also in the same neighborhood as prices Indian users already
know from Spotify/Hotstar mobile tiers, so it won't feel like an unfamiliar number.

The annual price isn't just "12x with a discount" — it's priced to make annual the
obviously better deal (41% off) because annual subscribers churn far less than monthly
ones, and Google's cut doesn't change either way.

## Free trial

**30 days, card required upfront, auto-converts to paid unless canceled** — this is a
built-in Google Play Billing "offer phase," not custom code you write. This matches what
you originally asked for, and it's also what the data supports: RevenueCat's numbers
showed 17–32 day trials converting at 42.5%, roughly double the rate of trials under 4
days. Shorter "try it free, no card" trials feel lower-friction but convert much worse,
because there's no default action pulling people toward paying — with a card-on-file
trial, staying subscribed is the default and canceling is the deliberate act, which is
exactly what drives that higher number.

## Getting paid — what your PAN + savings account actually covers

You do **not** need a company or GST number to start. Concretely:

- **Play Console developer account:** $25 one-time fee + identity verification using your
  PAN and a proof-of-address document (bank statement works) — you already have what's
  needed for this.
- **Getting paid out:** Google Play's payment profile supports an "Individual" seller
  type using personal KYC (PAN), paid into a regular savings account. No GST or company
  registration required to set this up.
- **GST specifically:** India's GST registration threshold for a *services* business
  (which is what selling an app subscription is) is ₹20 lakh/year turnover (₹10 lakh in a
  few special-category states) — not the ₹40 lakh figure you may see quoted, which is for
  goods sellers. Below that threshold, GST registration generally isn't mandatory. Given
  Wakeforge has zero paying users yet, you're nowhere near that line, so this isn't a
  blocker to launching.
- **One real caveat, not a guess:** there's a separate, less common rule that can make GST
  registration mandatory *regardless of turnover* for certain sales made through an
  "e-commerce operator," and reasonable people could debate whether Play Store counts for
  a subscription app like this. Google's own help page is deliberately vague on this and
  tells developers to check with a tax advisor rather than asserting an answer — so before
  you actually flip on real paid subscriptions (not before this point), a one-time,
  cheap consultation with a CA to confirm your specific setup is genuinely worth the
  ₹1,000–2,000 it costs, rather than guessing on a tax-law edge case. This is exactly the
  kind of thing where "probably fine" isn't good enough to build a business on.

Practical order: launch free, run the closed test, get real users — *then*, before
flipping Play Billing on for real money, spend the one CA conversation confirming GST
isn't required yet for your specific numbers. That conversation costs less than a month
of a subscription tool and settles it for good instead of leaving it as a guess.

## What this is not

This is a starting hypothesis backed by industry benchmarks and one direct competitor's
pricing, not a tested number — you have zero users to validate it against yet. Once
you're through the closed-testing phase and have even a handful of real free users,
watch what people actually say about the price when Pro eventually goes live, and be
willing to move it. ₹99 is a reasonable place to start, not a permanent commitment.
