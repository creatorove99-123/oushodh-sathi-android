# ঔষধসাথী Android source — সহজ বাংলা গাইড

**শান্তি মেডিকেয়ার দ্বারা পরিচালিত**

এই folder-এ ঔষধসাথীর native Android app source আছে। উদ্দেশ্য হলো browser/PWA-র best-effort timer-এর বদলে Android-এর `AlarmManager` ব্যবহার করে app process বন্ধ বা Recent apps থেকে সরানো থাকলেও নির্ধারিত সময়ে local notification চেষ্টা করা।

## এখন কী আছে

- Daily, নির্বাচিত weekday, start/end date এবং as-needed রুটিন।
- একবারে পরবর্তী dose-এর local alarm; reboot, app update, সময়/সময় অঞ্চল বদল এবং permission grant-এর পরে reschedule।
- Notification-এ ব্যবহারকারী চাপলে “খেয়েছি” অথবা “১০ মিনিট পরে”; app নিজে থেকে dose নেওয়া হয়েছে ধরে না।
- App-private medicine/history storage, manual stock estimate, low-stock limit, stock log এবং JSON backup import/export।
- PWA JSON backup importer পুরোনো browser-local medicines ও status history আনতে পারে। Import করলে native app-এর আগের list/history replace হবে; PWA browser-এর original data থাকবে।
- Native APK Android notification-channel sound ব্যবহার করে; স্বয়ংক্রিয় Bengali TTS এখনো নেই। Browser PWA-র optional Bengali voice আলাদা ও best-effort।
- Android manifest-এ `INTERNET` permission নেই।

**Gradle APK এখনও তৈরি/ইনস্টল/বাস্তব ফোনে QA করা হয়নি।** এই workspace-এ Android SDK/Gradle নেই। Pure Java alarm-date logic-এর ৮টি check pass করেছে এবং native Java source Android 15/API 35 framework types-এর বিরুদ্ধে compile/type-check হয়েছে; তবে এটি Gradle resource packaging, install বা বাস্তব alarm test-এর বিকল্প নয়।

## Android permission setup

1. App একবার খুলে Settings-এ যান।
2. **Notification** permission দিন; Android 13+‑এ system prompt আসবে। Deny করলে alarm notification দেখা যাবে না।
3. Android 12+‑এ **Alarms & reminders** special access দিন। Allow না থাকলে app inexact fallback বসায়—সময় দেরি হতে পারে।
4. ফোনের Battery settings-এ ঔষধসাথীর background/battery restriction কমানো প্রযোজ্য হলে বিবেচনা করুন। কিছু নির্মাতার policy তারপরও notification বিলম্বিত করতে পারে।
5. PWA data আনতে আগে PWA Settings থেকে JSON export করুন; native app Settings থেকে import করে স্পষ্ট replacement confirmation দিন।

## বন্ধ app, reboot ও অন্যান্য সীমা

`AlarmManager.setExactAndAllowWhileIdle()` Android Doze-এ exact reminder-এর জন্য ব্যবহৃত হয়, কিন্তু exact access user-granted এবং রিভোকযোগ্য। Notification permission আলাদা। `BOOT_COMPLETED` receiver reboot-এর পর user unlock হওয়ার পরে alarm ফের বসায়; Direct Boot storage নেই। Android Settings থেকে Force stop, permission revoke, DND/silent mode, low battery বা OEM battery policy reminder আটকাতে/দেরি করাতে পারে। তাই **কোনো ১০০% delivery promise নেই**। জরুরি ওষুধের একমাত্র reminder হিসেবে app ব্যবহার করবেন না; চিকিৎসকের নির্দেশ এবং অন্য backup reminder রাখুন।

## শূন্য-খরচের APK build

Root project-এ `.github/workflows/build-apk.yml` manual **Build OushodhSathi APK** workflow আছে। এটি debug APK build করে এবং artifact একদিন রাখে; Play Store production signing নয়। Private GitHub Actions quota যাচাই করা যায়নি। GitHub-hosted public repository build ব্যবহার করলে source এবং workflow artifact অন্যরাও দেখতে/নামাতে পারে। তাই public repo তৈরি ও build চালুর আগে প্রকল্প-মালিকের স্পষ্ট সম্মতি আবশ্যক। কোনো paid service, subscription বা credit card দরকার হওয়ার কথা নয়; over-quota private build ব্যবহার করা হবে না।

APK পাওয়ার পর Android-এ download file খুলে install করুন; প্রয়োজনে browser/Files app-এর “Install unknown apps” অনুমতি দিন। Unknown-source APK ঝুঁকিপূর্ণ হতে পারে—শুধু বিশ্বাসযোগ্য build artifact install করুন।

## Project details

- Package / application ID: `bd.shantimedicare.oushodhsathi`
- Minimum Android: API 23 (Android 6)
- Target / compile SDK: API 35
- Java: 17
- Data store: app-private `SharedPreferences`; backup user-এর নির্বাচিত local file-এ।
- Exact permission: `SCHEDULE_EXACT_ALARM`; notifications: `POST_NOTIFICATIONS`; reboot: `RECEIVE_BOOT_COMPLETED`।
- Alarm scheduling implementation এবং official reference: `ANDROID_ALARM_RESEARCH.md`।
- Pure scheduling tests: `tests/ScheduleCalculatorTest.java`।
