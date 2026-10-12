# Stage 4 technical notes (checked 2026-10-12)

## Local Bengali Text-to-Speech

- MDN documents `SpeechSynthesis` as the browser interface for speech and `getVoices()` as the list of voices currently available to that browser/device: https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis and https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis/getVoices
- `SpeechSynthesisVoice.localService` is a boolean: `true` means a local speech synthesizer service supplies the voice; `false` means a remote service. The app accepts only `localService === true` and a BCP 47 language tag beginning with `bn`, so it never falls back to a remote voice: https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesisVoice/localService
- `SpeechSynthesisUtterance` exposes text, voice, rate, pitch, volume, and language controls. `lang` is a BCP 47 tag; the app sets it from the discovered Bengali voice: https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesisUtterance and https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesisUtterance/lang
- The settings switch defaults off, is independent from notification permission, and becomes unavailable if the browser has no local Bengali voice. The optional test uses the same selected local voice. A reminder is attempted only while the app page is visible; browser/OS scheduling, sound policy, installed voice packs and pronunciation remain device-dependent.

## Stage 4 stock-estimate behavior

- Stock fields are optional and stored in the existing device-local medicine record. They do not change prescribed dose or schedule.
- The user can record a positive quantity as consumed, which subtracts it from the estimate, or set the current remaining quantity directly (including zero). Consumed quantity cannot exceed the current estimate. Adjustment history is bounded to 30 records per medicine; the card displays the latest entry.
- “খেয়েছি”/“পরে খাব”/“নিশ্চিত নই” status actions never adjust stock. A low-stock warning is a comparison of the user's own remaining estimate against their chosen threshold, not a verified or clinical inventory.

## Verification and limits

- `node --check` passed for `app.js`, `reminders.js`, and `sw.js`; manifest JSON and unique HTML IDs were validated.
- On separate preview origin `4177`, disposable QA confirmed stock entry, required-field behavior, rejection of usage greater than remaining quantity, manual subtraction, direct set mode, low-stock warning, and no automatic deduction after “খেয়েছি”. The QA medicine was deleted. The saved Stage 3 origin `4175` was not modified by these QA interactions.
- The current in-app browser exposed no local Bengali voice. The interface displayed its guidance and kept voice/test unavailable; actual speech output therefore remains unverified until a Bengali local TTS voice is installed and tested on the target Android phone.
- Browser console returned zero errors on the final QA and preserved-origin preview. No notification permission was requested, and no real system notification, background speech, closed-app alarm, reboot, or native Android exact alarm was tested. PWA timers are best-effort; no alarm guarantee is made.
- No paid voice service, backend, billing, credit card, remote font or voice API is used.
