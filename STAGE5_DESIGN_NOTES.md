# ঔষধসাথী Stage 5 — local navigation redesign

## Reference checked

- User-supplied Pinterest short link: https://pin.it/NTIVy7kel
- Public redirect metadata identifies the pin as **“Neo-Apple UI Design — Glassy Gradients & Organic Forms for SaaS”**; the description mentions neo-glassmorphism, soft/translucent shadows, iridescent gradients and organic shapes. Pinterest’s page itself showed a sign-in shell when fetched: https://www.pinterest.com/pin/94575660922056663/
- User-supplied navigation screenshot: a floating dark capsule with five destinations, a prominent Create action and a curved red outline around the active item. The supplied image was inspected from the prompt and is not re-opened from disk.

## OushodhSathi adaptation

Use the pin for softly layered glass surfaces and organic gradient accents, and the supplied screenshot for a floating pill-shaped five-slot bottom bar. Adapt the destinations to Bengali: **হোম, ওষুধ, + যোগ, ইতিহাস, সেটিংস**. Use deep forest green and warm gold instead of Pinterest black/red, and never reuse the Pinterest logo or brand marks. Keep touch targets, Bengali labels, visible focus, `aria-current`, reduced-motion support and safe-area spacing. Preserve all existing browser-local storage keys and same-origin PWA data.

## Scope/privacy

The user explicitly approved publishing this Stage 5 source update to the existing public repository and running one manual debug APK build. No real patient records or private browser data are included. The previous APK does not contain the redesign; the new artifact is a debug build with one-day retention, not a production-signed release. Install, closed-app alarm, reboot, Doze and OEM-device QA remain separate checks.
