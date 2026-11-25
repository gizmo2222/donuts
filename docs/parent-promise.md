# The promise to parents

What the game promises the grown-ups, in the words to use wherever a parent reads about it: the Play
listing, the privacy policy and the tester notes. `PrivacyPromiseTest` guards the parts code can break.

## The promise

**No ads. No purchases. No timers. Nothing leaves this phone.**

Donuts for Steven is a free match-3 game made as a gift. There are no ads, nothing to buy, no lives, no
timers and no way to lose. It never asks for internet access, so nothing your child does is collected or
sent anywhere. Progress stays on the phone.

## Play listing text

Short description (80 characters at most):

> Drag a chain of matching donuts and pop them. No ads, no purchases, no timers.

Paragraph for the end of the full description:

> Made for parents too: Donuts for Steven has no ads, no in-app purchases, no accounts and no chat. There
> are no lives, timers or streaks, so your child can stop at any time and pick up right where they left
> off. The game does not ask for internet access; nothing is collected or sent, and progress stays on the
> phone.

These are kept here, not under `app\src\main\play\listings`, on purpose: a listings folder there would make
Gradle Play Publisher's listing tasks overwrite the live listing. Paste them into Play Console by hand.

## Data safety form answers

- Does the app collect or share any of the required user data types? **No.**
- Is all data encrypted in transit? Nothing is transmitted.
- Do you provide a way for users to request that their data be deleted? Nothing is collected; uninstalling
  or clearing the app's storage deletes what is saved on the phone.

Data that stays on the device is not counted as collected, and the Android system backup is the family's
own, so "no data collected or shared" is the honest answer.

## Privacy policy

The text is in `docs\privacy-policy.md`, ready to host as one page (for example on GitHub Pages) and link
from the Play listing and the Data safety form. Hosting it and filling in the forms are Play Console work
(backlog row B41).

## What would break the promise

Any of these needs the policy and the Data safety answers rewritten first, and `PrivacyPromiseTest` will
fail until it is updated:

- a new permission in the merged manifest, above all `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID` or a
  location permission;
- a new library dependency (ads, analytics, crash reports, cloud save and sign-in SDKs all bring network
  access);
- a link out of the app, or anything that opens a browser or the Play Store.

Dropping a dependency needs no policy change; remove it from the allow list in the test too. Backlog row B14
dropped appcompat and material and named `androidx.activity`, which they had pulled in and which sends nothing.
