# LiveChat XR Mobile

The phone companion to [LiveChat XR](https://github.com/DeliciousHouse/livechat-xr), for **standalone** VR headsets
such as Meta Quest played without a PC.

**Status: planning.** No code yet. The first milestone is a feasibility test (below).

## Why a phone app and not a headset app

On PC VR, LiveChat XR draws a head-locked chat banner by loading an OpenXR API layer inside the game. Standalone
headsets don't allow that: Horizon OS never lets one app draw on top of another app's immersive game, whether it's
from the Store or sideloaded. A sideloaded notifier was tested and its notifications only reached the headset's
notification tray, never a pop-up.

What Quest *does* show over games is the user's **phone notifications** (Meta Horizon app → phone notifications).
So the plan:

1. The phone app connects to your **Twitch** chat (official, anonymous IRC) and/or **TikTok LIVE** (unofficial)
   and receives comments, gifts, Bits and subs in real time.
2. It posts each batch as a phone notification. Gifts come first, and comments that arrive close together are
   merged, the same rules as the PC version.
3. Quest mirrors that notification into the headset as a pop-up during play.

Meta controls the pop-up's look, position and duration (about 5 s); the app can't change those.

## Milestone 0: feasibility (blocking)

- [ ] Confirm that mirrored phone notifications appear as pop-ups **inside an immersive game**, on Android and on iPhone.
- [ ] Measure delay and duration and check behaviour with many notifications in a row.

If mirrored pop-ups don't show in-game, a standalone version isn't possible today and this repo stays parked.

## Planned scope (after milestone 0)

- Android first (Kotlin), iOS second
- Twitch: chat, Bits, subs, gifted subs
- TikTok LIVE: comments and gifts via an unofficial client (may break when TikTok changes; app-store policy risk)
- Settings: platform, channel, batching window, gifts-only mode, quiet hours

## License

MIT. TikTok and Twitch are trademarks of their owners; not affiliated with TikTok, Twitch or Meta.
