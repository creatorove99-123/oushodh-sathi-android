# ঔষধসাথী — নেটিভ Android alarm build

**শান্তি মেডিকেয়ার দ্বারা পরিচালিত** — বাংলা, mobile-first ওষুধের রুটিন, স্থানীয় notification, ব্যবহারকারীর বেছে নেওয়া dose status, ঐচ্ছিক মজুতের হিসাব এবং PWA backup স্থানান্তরের জন্য তৈরি।

## বর্তমান অবস্থা — গুরুত্বপূর্ণ

- আগের PWA preview-র same-origin link chat-এ দেওয়া হয়েছে; public source README-তে temporary preview URL রাখা হয়নি। আগের PWA-তে data থাকলে একই link, HTTPS origin ও browser profile ব্যবহার করুন; browser/site data মুছবেন না।
- **PWA নিজে বন্ধ থাকলে নির্ভরযোগ্য alarm দিতে পারে না।** তাই `android-native/`-এ Android `AlarmManager`-ভিত্তিক নেটিভ app source যোগ করা হয়েছে। স্বাভাবিকভাবে app বন্ধ/Recent apps থেকে সরানো থাকলেও Android-এর scheduled alarm notification দেখানোর জন্য এটি তৈরি।
- **GitHub Actions-এ debug APK build সফল হয়েছে** (2026-10-12; [public build run](https://github.com/creatorove99-123/oushodh-sathi-android/actions/runs/38257882948)) এবং APK ডাউনলোড ও ZIP-integrity যাচাই হয়েছে। APK এই handoff-এ দেওয়া হয়েছে। বাস্তব Android ফোনে install, notification delivery, app-বন্ধ/Doze/reboot পরীক্ষা এখনও করা হয়নি; এটি debug build, Play Store/production-signed নয়।
- [Public source repository](https://github.com/creatorove99-123/oushodh-sathi-android) অনুমতি নিয়ে তৈরি হয়েছে; source ও একদিনের workflow artifact public read-access-এ দেখা/নামানো যায়। Public GitHub runner-এ একবার free build চলেছে—subscription/card, paid API, patient-data upload বা private Actions quota ব্যবহার করা হয়নি। Repo-তে real patient record, backup, credential বা temporary preview URL নেই।

## PWA থেকে native app-এ data নেওয়া

1. Chat-এ দেওয়া PWA preview link আগে যে browser/origin-এ ব্যবহার করেছেন, সেখানেই খুলুন।
2. **Settings → ফোনে backup export** চাপুন। ওষুধ, status history এবং হাতে লেখা মজুতের হিসাবসহ JSON ফাইল ফোনে download হবে।
3. APK তৈরি/ইনস্টল হওয়ার পর native app-এর **Settings → PWA backup import** থেকে ওই JSON বাছুন। Confirm করলে native app-এর বর্তমান তালিকা/history replace হবে; PWA browser data বদলাবে না।
4. Native app-এ **Notification** permission এবং Android 12+‑এ **Alarms & reminders** special access দিন। Settings-এ permission status দেখা যাবে।
5. প্রথমে আসল রোগীর তথ্য নয়—একটি dummy medicine দিয়ে reminder পরীক্ষা করুন।

Backup-এ ব্যক্তিগত স্বাস্থ্যতথ্য থাকতে পারে। JSON ফাইল public repository, chat বা অন্য কারও সঙ্গে share করবেন না। নতুন app-এ import করা আর PWA-র মূল browser data আলাদা থাকে।

## Native Android-এ যা তৈরি হয়েছে

- প্রতিটি dose-time-এর জন্য একবারে পরবর্তী occurrence schedule করা; দৈনিক, সপ্তাহের নির্বাচিত দিন, start/end date এবং as-needed রুটিন সমর্থন।
- Android 6+‑এ `setExactAndAllowWhileIdle()`; Android 12+‑এ exact-alarm special access না থাকলে inexact `setAndAllowWhileIdle()` fallback ও স্পষ্ট warning।
- Alarm notification-এ **“খেয়েছি”** এবং **“১০ মিনিট পরে”** action। App কাউকে নিজে থেকে ওষুধ খেয়েছে বলে ধরে না; status action ব্যবহারকারী চাপলেই history বদলায়।
- Reboot-এর পর, app update, time/timezone change এবং exact-alarm access grant-এর পর alarm পুনরায় schedule করা। সংবেদনশীল medicine data Direct Boot storage-এ রাখা হয় না।
- ওষুধের তালিকা, status history, optional local stock estimate/low-stock threshold, manual stock adjustment এবং local JSON backup import/export।
- Native app-এ স্বয়ংক্রিয় Bengali TTS নেই; reminder Android notification channel-এর sound ব্যবহার করে। আগের optional Bengali voice শুধু browser PWA-তে best-effort হিসেবে থাকে, app বন্ধ হলে তা কাজ করে না।
- App manifest-এ **কোনো INTERNET permission নেই**। Sync/cloud/database/backend নেই।

## Android-এর বাস্তব সীমা

এই native app সাধারণভাবে app process বন্ধ বা Recent apps থেকে সরানো থাকলেও `AlarmManager` দিয়ে reminder দিতে পারে; তবে **১০০% delivery guarantee নয়**। Android/OEM battery policy, DND/silent mode, permission বন্ধ/রিভোক, low battery, exact-alarm quota এবং ফোনভেদে ব্যবস্থাপনা reminder দেরি/বন্ধ করতে পারে। Phone restart-এর পর user unlock হলে receiver alarm আবার বসায়; প্রথমবার app একবার খোলা প্রয়োজন। Settings থেকে **Force stop** করলে Android alarm বন্ধ করতে পারে; পরে app পুনরায় খুলে schedule তৈরি করুন। Permission বন্ধ থাকলে alarm notification দেখা যাবে না। Battery optimization settings-এ প্রযোজ্য হলে ঔষধসাথীকে unrestricted করা সহায়ক হতে পারে, কিন্তু এটিও নিশ্চয়তা নয়।

**জরুরি চিকিৎসা নির্দেশের একমাত্র reminder হিসেবে কোনো app ব্যবহার করবেন না।** ডাক্তার/ফার্মাসিস্টের নির্দেশ মেনে অন্য নির্ভরযোগ্য backup alarm রাখুন। App চিকিৎসা-পরামর্শ বা prescription নয়।

## বিনামূল্যের build ও install সীমা

Native source `android-native/`-এ; manual build workflow `.github/workflows/build-apk.yml`-এ। [প্রথম সফল build run](https://github.com/creatorove99-123/oushodh-sathi-android/actions/runs/38257882948) debug APK তৈরি করেছে। GitHub Actions artifact একদিন রাখা হয়, তাই artifact মেয়াদ শেষ হলেও এই handoff-এ দেওয়া APK copy থাকবে। এটি Play Store listing বা production signing নয়।

APK পেলে Android-এ ফাইলটি খুলে install করতে হতে পারে এবং browser/Files app-এর জন্য “Install unknown apps” অনুমতি দিতে হতে পারে। এটি Play Store থেকে আসবে না; কেবল trusted source থেকে নেওয়া APK install করুন।

## যাচাইয়ের অবস্থা

- Pure Java scheduler পরীক্ষা: ৮টি check পাস—daily same-day/next-day, weekly day, future start, end-date-এর শেষ দিন অন্তর্ভুক্তি ও পরের দিন বন্ধ, as-needed এবং weekday selection।
- সব native Java source Android 15/API 35 framework types-এর বিরুদ্ধে compile/type-check পাস করেছে; এটি Gradle resource packaging বা বাস্তব Android install test নয়।
- PWA medicine storage key ও history key যাচাই করে matching JSON export/import schema তৈরি হয়েছে। Existing PWA localStorage key বদলানো হয়নি।
- GitHub Actions Gradle build সফল; downloaded APK-তে binary manifest, DEX ও resources উপস্থিত এবং ZIP integrity pass করেছে। APK install, notification permission, exact-alarm delivery, screen-off/closed-app/Doze, reboot, force-stop এবং বাস্তব phone/OEM test **এখনও হয়নি**।
- Public repository ও Actions artifact-এ source প্রকাশিত; কোনো রোগীর তথ্য বা secret অন্তর্ভুক্ত হয়নি।

## Source layout

- `index.html`, `styles.css`, `app.js`, `reminders.js`: আগের Bengali PWA ও device-local data।
- `android-native/app/src/main/`: native Android app, local storage ও alarm receiver।
- `android-native/tests/ScheduleCalculatorTest.java`: pure Java time-scheduling checks।
- `.github/workflows/build-apk.yml`: manual, one-day artifact build workflow।
- `STAGE3_TECH_NOTES.md`, `STAGE4_TECH_NOTES.md`, `android-native/ANDROID_ALARM_RESEARCH.md`: API evidence ও সীমাবদ্ধতার নোট।


## Stage 5 — navigation redesign (local source)

PWA preview এবং Android source-এ glass-gradient surfaces ও নিচে পাঁচ-slot pill navigation যোগ হয়েছে: **হোম · ওষুধ · + যোগ · ইতিহাস · সেটিংস**। PWA-র browser-local medicine/history keys বদলানো হয়নি; preview আগের HTTPS origin-এই থাকে। Pinterest reference-এর visual language ব্যবহার করা হয়েছে, কিন্তু কোনো logo/brand mark নয়।

এই revision-এ Stage 5 source update অন্তর্ভুক্ত। এই redesign-সহ আগের APK এখনো নেই; সেই APK-তে Stage 5 navigation নেই। একবারের manual debug APK build এই source update-এর পর চালানো হবে; বাস্তব Android ফোনে install/alarm QA এখনও বাকি।
