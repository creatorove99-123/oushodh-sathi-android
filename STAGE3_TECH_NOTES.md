# Stage 3 technical evidence (checked 2026-10-12)

- MDN says `ServiceWorkerRegistration.showNotification()` requires a secure context (HTTPS) and a permission grant; it supports `actions`, `data`, tags, icons and notification text. Action availability is browser-dependent: https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerRegistration/showNotification
- MDN documents `notificationclick` and `event.action` handling in service workers, but labels support as limited availability: https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/notificationclick_event
- MDN says `Notification.requestPermission()` asks for permission for the current origin and should be invoked in response to user interaction: https://developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission_static
- Chrome documents chained timers in hidden tabs can be checked only once per minute under intensive throttling conditions: https://developer.chrome.com/blog/timer-throttling-in-chrome-88
- MDN notes background/inactive page timers are throttled and browser policies vary: https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API
- MDN explains service workers can perform background work when woken by supported events, but periodic background sync frequency is decided by the browser and may be infrequent or absent: https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Offline_and_background_operation

Implementation implication: a PWA polling timer can provide best-effort reminders while the page/runtime remains available, but is not an exact alarm service and does not guarantee delivery after the app is closed, killed, suspended, or the device restarts. A real Android AlarmManager/native implementation must be built and tested on a device for those guarantees.
