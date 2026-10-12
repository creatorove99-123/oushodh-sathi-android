package bd.shantimedicare.oushodhsathi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 701;
    private static final int REQ_IMPORT = 702;
    private static final int REQ_EXPORT = 703;
    private static final int REQ_EXACT_ALARM = 704;
    private static final String[] DAY_NAMES = {"রবি", "সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি"};
    private static final int INK = Color.rgb(39, 50, 46);
    private static final int MUTED = Color.rgb(101, 113, 106);
    private static final int GREEN = Color.rgb(49, 107, 77);
    private static final int GOLD = Color.rgb(213, 166, 41);
    private static final int PAGE = Color.rgb(247, 248, 244);
    private static final int CARD = Color.WHITE;

    private LinearLayout root;
    private LinearLayout content;
    private String activeTab = "home";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(CARD);
        if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        AlarmScheduler.createChannel(this);
        buildShell();
        AlarmScheduler.scheduleAll(this);
        showTab("home");
    }

    @Override protected void onResume() {
        super.onResume();
        AlarmScheduler.createChannel(this);
        AlarmScheduler.scheduleAll(this);
        if (content != null) showTab(activeTab);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(17), dp(10), dp(17), dp(10));
        header.setBackgroundColor(CARD);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_app);
        header.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);
        TextView title = text("ঔষধসাথী", 19, INK, true);
        TextView subtitle = text("শান্তি মেডিকেয়ার দ্বারা পরিচালিত", 10, MUTED, false);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button add = button("＋", GOLD, INK);
        add.setContentDescription("নতুন ওষুধ যোগ করুন");
        add.setOnClickListener(v -> showMedicineDialog(null));
        header.addView(add, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17), dp(18), dp(17), dp(22));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(CARD);
        nav.setPadding(dp(5), dp(7), dp(5), dp(7));
        String[][] tabs = {{"home", "⌂\nহোম"}, {"medicines", "▱\nওষুধ"}, {"history", "◷\nইতিহাস"}, {"settings", "⚙\nসেটিংস"}};
        for (String[] tab : tabs) {
            Button b = button(tab[1], CARD, GREEN);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            b.setMinHeight(dp(50));
            b.setPadding(dp(3), 0, dp(3), 0);
            b.setOnClickListener(v -> showTab(tab[0]));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        setContentView(root);
    }

    private void showTab(String tab) {
        activeTab = tab;
        if (content == null) return;
        content.removeAllViews();
        if ("medicines".equals(tab)) renderMedicines();
        else if ("history".equals(tab)) renderHistory();
        else if ("settings".equals(tab)) renderSettings();
        else renderHome();
    }

    private void renderHome() {
        addText(content, "আজকের সঙ্গী", 25, INK, true, 0, 0, 0, 2);
        addText(content, todayLabel(), 13, MUTED, false, 0, 0, 0, 12);
        LinearLayout notice = card();
        notice.setBackground(round(PAGE, dp(17), Color.rgb(229, 239, 227)));
        addText(notice, "বন্ধ PWA-এর বদলে native alarm", 14, GREEN, true, 0, 0, 0, 4);
        addText(notice, "ওষুধের সময় ফোনে exact local alarm schedule হয়। Notifications ও Alarms & reminders অনুমতি দিন; app বন্ধ থাকলেও কাজ করার জন্য এটি তৈরি।", 12, MUTED, false, 0, 0, 0, 0);
        content.addView(notice, margin(0, 0, 0, 12));
        if (!AlarmScheduler.hasExactAccess(this) || !AlarmScheduler.notificationsAllowed(this)) {
            LinearLayout perm = card();
            addText(perm, "রিমাইন্ডার চালু করতে অনুমতি বাকি", 14, Color.rgb(144, 73, 25), true, 0, 0, 0, 5);
            String detail = !AlarmScheduler.hasExactAccess(this) ? "Exact alarm-এর Alarms & reminders access এখনো দেওয়া হয়নি। অনুমতি ছাড়া সময় দেরি হতে পারে।" : "Android notification অনুমতি বন্ধ; alarm হলেও notification দেখা যাবে না।";
            addText(perm, detail, 12, MUTED, false, 0, 0, 0, 8);
            Button settings = button("সেটিংসে অনুমতি দিন", GREEN, Color.WHITE);
            settings.setOnClickListener(v -> showTab("settings"));
            perm.addView(settings);
            content.addView(perm, margin(0, 0, 0, 12));
        }

        LinearLayout hero = card();
        hero.setBackground(round(Color.rgb(255, 244, 208), dp(22), Color.rgb(250, 224, 156)));
        addText(hero, "আপনার সুস্থতার সঙ্গী", 12, Color.rgb(127, 94, 15), true, 0, 0, 0, 6);
        addText(hero, "এক ধাপ করে\nভালো থাকার পথে", 23, INK, true, 0, 0, 0, 6);
        ArrayList<Medicine> meds = MedicineStore.medicines(this);
        addText(hero, "ওষুধের সময় মনে রাখুন—খাওয়ার status নিজে বেছে নিন।", 12, MUTED, false, 0, 0, 0, 10);
        addText(hero, "আজকের তালিকা: " + bn(meds.size()) + "টি ওষুধ", 12, GREEN, true, 0, 0, 0, 0);
        content.addView(hero, margin(0, 0, 0, 18));

        addText(content, "আজকের সময়সূচি", 19, INK, true, 0, 0, 0, 9);
        ArrayList<DoseSlot> slots = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        for (Medicine m : meds) {
            if (!ScheduleCalculator.occursOn(m.frequency, m.days, m.start, m.end, today)) continue;
            for (String time : m.times) if (time.matches("([01]\\d|2[0-3]):[0-5]\\d")) slots.add(new DoseSlot(m, time));
        }
        Collections.sort(slots, new Comparator<DoseSlot>() {
            @Override public int compare(DoseSlot a, DoseSlot b) { int time = a.time.compareTo(b.time); return time != 0 ? time : a.medicine.name.compareToIgnoreCase(b.medicine.name); }
        });
        if (slots.isEmpty()) {
            LinearLayout empty = card();
            addText(empty, meds.isEmpty() ? "এখনো কোনো ওষুধ যোগ হয়নি" : "আজ কোনো নির্ধারিত সময় নেই", 16, INK, true, 0, 0, 0, 5);
            addText(empty, meds.isEmpty() ? "শুরু করতে নিচের বোতামে ওষুধ ও সময় যোগ করুন।" : "সপ্তাহের দিন ও তারিখের সীমা দেখে নিন।", 12, MUTED, false, 0, 0, 0, 8);
            Button addButton = button("＋  ওষুধ যোগ করুন", GREEN, Color.WHITE);
            addButton.setOnClickListener(v -> showMedicineDialog(null));
            empty.addView(addButton);
            content.addView(empty, margin(0, 0, 0, 12));
        } else {
            for (DoseSlot slot : slots) renderDoseCard(slot);
        }
        LinearLayout safety = card();
        safety.setBackground(round(Color.rgb(239, 245, 237), dp(16), Color.rgb(228, 237, 223)));
        addText(safety, "স্বাস্থ্য-সুরক্ষা", 13, GREEN, true, 0, 0, 0, 4);
        addText(safety, "Reminder চিকিৎসকের পরামর্শের বিকল্প নয়। জরুরি চিকিৎসা নির্দেশে app-এর alarm-কে একমাত্র ব্যবস্থা হিসেবে ধরবেন না।", 11, MUTED, false, 0, 0, 0, 0);
        content.addView(safety, margin(0, dp(8), 0, 0));
    }

    private void renderDoseCard(DoseSlot slot) {
        LinearLayout item = card();
        addText(item, displayTime(slot.time) + "  ·  " + slot.medicine.name, 16, INK, true, 0, 0, 0, 4);
        String detail = slot.medicine.dose;
        if (!slot.medicine.instruction.isEmpty()) detail = detail.isEmpty() ? slot.medicine.instruction : detail + " • " + slot.medicine.instruction;
        addText(item, detail.isEmpty() ? "নির্দেশনা যোগ করা হয়নি" : detail, 12, MUTED, false, 0, 0, 0, 6);
        String date = todayIso();
        String status = MedicineStore.doseStatus(this, date, slot.medicine.id, slot.time);
        addText(item, statusLabel(status), 12, "taken".equals(status) ? GREEN : MUTED, true, 0, 0, 0, 8);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {"taken", "later", "unsure"};
        String[] labels = {"খেয়েছি", "পরে খাব", "নিশ্চিত নই"};
        for (int i = 0; i < values.length; i++) {
            final String chosen = values[i];
            Button b = button(labels[i], "taken".equals(chosen) ? GREEN : PAGE, "taken".equals(chosen) ? Color.WHITE : INK);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            b.setOnClickListener(v -> {
                MedicineStore.setDoseStatus(this, slot.medicine.id, date, slot.time, chosen);
                if ("later".equals(chosen)) AlarmScheduler.scheduleSnooze(this, slot.medicine.id, date, slot.time);
                showTab("home");
                Toast.makeText(this, "আপনার নির্বাচিত status সংরক্ষণ হয়েছে।", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) lp.leftMargin = dp(5);
            row.addView(b, lp);
        }
        item.addView(row);
        content.addView(item, margin(0, 0, 0, 10));
    }

    private void renderMedicines() {
        addText(content, "আপনার ওষুধ", 25, INK, true, 0, 0, 0, 4);
        addText(content, "নাম, মাত্রা ও সময়সূচি আপনার ফোনেই রাখা হয়।", 12, MUTED, false, 0, 0, 0, 12);
        Button add = button("＋  নতুন ওষুধ যোগ করুন", GREEN, Color.WHITE);
        add.setOnClickListener(v -> showMedicineDialog(null));
        content.addView(add, margin(0, 0, 0, 13));
        ArrayList<Medicine> meds = MedicineStore.medicines(this);
        if (meds.isEmpty()) {
            LinearLayout empty = card();
            addText(empty, "ওষুধের তালিকা খালি", 16, INK, true, 0, 0, 0, 4);
            addText(empty, "প্রথম ওষুধ যোগ করলে তার নির্ধারিত সময়ে notification alarm বসবে।", 12, MUTED, false, 0, 0, 0, 0);
            content.addView(empty);
            return;
        }
        for (Medicine m : meds) {
            LinearLayout item = card();
            addText(item, m.name, 17, INK, true, 0, 0, 0, 3);
            String details = m.dose;
            if (!m.instruction.isEmpty()) details = details.isEmpty() ? m.instruction : details + " • " + m.instruction;
            addText(item, details, 12, MUTED, false, 0, 0, 0, 4);
            addText(item, scheduleText(m), 12, GREEN, true, 0, 0, 0, 5);
            if (m.stockEnabled) {
                String stock = m.stockRemaining == null ? "মজুত যোগ করা হয়নি" : "আনুমানিক বাকি: " + bnDecimal(m.stockRemaining) + " " + m.stockUnit;
                boolean low = m.stockRemaining != null && m.stockRemaining <= m.stockLowAt;
                addText(item, (low ? "কম মজুত · " : "") + stock, 12, low ? Color.rgb(155, 56, 42) : MUTED, true, 0, 0, 0, 5);
                Button stockButton = button("মজুত আপডেট", PAGE, INK);
                stockButton.setOnClickListener(v -> showStockDialog(m));
                item.addView(stockButton, margin(0, 0, 0, 7));
            }
            LinearLayout row = new LinearLayout(this);
            Button edit = button("সম্পাদনা", PAGE, GREEN);
            edit.setOnClickListener(v -> showMedicineDialog(m));
            Button delete = button("মুছুন", Color.rgb(255, 240, 236), Color.rgb(155, 56, 42));
            delete.setOnClickListener(v -> confirmDelete(m));
            row.addView(edit, new LinearLayout.LayoutParams(0, dp(46), 1));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, dp(46), 1);
            delLp.leftMargin = dp(8);
            row.addView(delete, delLp);
            item.addView(row);
            content.addView(item, margin(0, 0, 0, 11));
        }
    }

    private void renderHistory() {
        addText(content, "দৈনিক ইতিহাস", 25, INK, true, 0, 0, 0, 4);
        addText(content, "ওষুধের status আপনি বাছলে তবেই এখানে থাকে।", 12, MUTED, false, 0, 0, 0, 12);
        ArrayList<JSONObject> items = MedicineStore.history(this);
        Collections.reverse(items);
        if (items.isEmpty()) {
            LinearLayout empty = card();
            addText(empty, "ইতিহাস এখনো নেই", 16, INK, true, 0, 0, 0, 5);
            addText(empty, "Notification বা status নিজের থেকে “খেয়েছি” ধরে নেয় না।", 12, MUTED, false, 0, 0, 0, 0);
            content.addView(empty);
            return;
        }
        int shown = 0;
        for (JSONObject record : items) {
            if (shown++ >= 100) break;
            LinearLayout item = card();
            addText(item, record.optString("medicineName", "ওষুধ") + "  ·  " + displayTime(record.optString("time", "")), 14, INK, true, 0, 0, 0, 3);
            addText(item, formatIsoDate(record.optString("date", "")) + "  ·  " + statusLabel(record.optString("status", "pending")), 11, MUTED, false, 0, 0, 0, 0);
            content.addView(item, margin(0, 0, 0, 8));
        }
    }

    private void renderSettings() {
        addText(content, "সেটিংস", 25, INK, true, 0, 0, 0, 4);
        addText(content, "বন্ধ অবস্থায় reminder পাওয়ার জন্য Android অনুমতিগুলো একবার দিন।", 12, MUTED, false, 0, 0, 0, 12);

        LinearLayout alarm = card();
        addText(alarm, "সঠিক সময়ের alarm", 16, INK, true, 0, 0, 0, 4);
        boolean exact = AlarmScheduler.hasExactAccess(this);
        addText(alarm, exact ? "Alarms & reminders access: চালু" : "Alarms & reminders access: এখনো চালু নয়", 12, exact ? GREEN : Color.rgb(155, 56, 42), true, 0, 0, 5, 5);
        addText(alarm, "Android 12+‑এ বিশেষ অনুমতি লাগে। পরের screen-এ ঔষধসাথীকে allow করুন। অনুমতি না দিলে কিছু ফোনে reminder দেরি হতে পারে।", 11, MUTED, false, 0, 0, 0, 8);
        Button exactButton = button(exact ? "অনুমতি যাচাই করুন" : "Alarms & reminders চালু করুন", GOLD, INK);
        exactButton.setOnClickListener(v -> requestExactAccess());
        alarm.addView(exactButton);
        content.addView(alarm, margin(0, 0, 0, 10));

        LinearLayout notif = card();
        addText(notif, "Notification", 16, INK, true, 0, 0, 0, 4);
        boolean allowed = AlarmScheduler.notificationsAllowed(this);
        addText(notif, allowed ? "Notification permission: চালু" : "Notification permission: বন্ধ", 12, allowed ? GREEN : Color.rgb(155, 56, 42), true, 0, 0, 5, 5);
        addText(notif, "Alarm fire হলে ওষুধের নাম, সময় এবং “খেয়েছি”/“১০ মিনিট পরে” action notification-এ দেখায়।", 11, MUTED, false, 0, 0, 0, 8);
        Button notifButton = button(allowed ? "Notification সেটিংস দেখুন" : "Notification অনুমতি দিন", GREEN, Color.WHITE);
        notifButton.setOnClickListener(v -> requestNotifications());
        notif.addView(notifButton);
        content.addView(notif, margin(0, 0, 0, 10));

        LinearLayout battery = card();
        addText(battery, "Battery ও ফোন বন্ধ থাকার সীমা", 15, INK, true, 0, 0, 0, 4);
        addText(battery, "setExactAndAllowWhileIdle Doze-এ কাজের জন্য ব্যবহৃত হয়। কিছু ফোনে manufacturer-এর battery saver/autostart control তবু reminder আটকে দিতে পারে। প্রয়োজন হলে নিচের Settings-এ ঔষধসাথীকে battery optimization থেকে ছাড় দিন।", 11, MUTED, false, 0, 0, 0, 8);
        Button batteryButton = button("Battery optimization settings", PAGE, GREEN);
        batteryButton.setOnClickListener(v -> openBatterySettings());
        battery.addView(batteryButton);
        content.addView(battery, margin(0, 0, 0, 10));

        LinearLayout data = card();
        addText(data, "ডাটা backup ও স্থানান্তর", 15, INK, true, 0, 0, 0, 4);
        addText(data, "PWA-র Settings → ফোনে backup export থেকে JSON ফাইল download করুন। তারপর এখানে import করলে ওষুধ, মজুত ও status history এই app-এ আসবে। তথ্য cloud-এ যায় না; import করলে এই app-এর বর্তমান তালিকা replace হবে।", 11, MUTED, false, 0, 0, 0, 8);
        Button importButton = button("PWA backup import করুন", PAGE, GREEN);
        importButton.setOnClickListener(v -> startImport());
        data.addView(importButton, margin(0, 0, 0, 7));
        Button exportButton = button("এই app-এর backup export", PAGE, GREEN);
        exportButton.setOnClickListener(v -> startExport());
        data.addView(exportButton);
        content.addView(data, margin(0, 0, 0, 10));

        LinearLayout privacy = card();
        addText(privacy, "গোপনীয়তা ও জরুরি সীমা", 15, INK, true, 0, 0, 0, 4);
        addText(privacy, "ওষুধ ও history app-private phone storage-এ থাকে; এই app-এ internet permission নেই। Android-এর app data clear/uninstall করলে তথ্য মুছে যাবে—backup নিরাপদে রাখুন।", 11, MUTED, false, 0, 0, 0, 5);
        addText(privacy, "ফোন restart হলে unlock-এর পর alarm আবার schedule হয়। Android Settings থেকে Force stop করলে বা notification/exact-alarm permission বন্ধ করলে alarm থামতে পারে। DND, silent mode, low battery বা কিছু নির্মাতার system policy-তেও sound প্রভাবিত হতে পারে।", 11, MUTED, false, 0, 0, 0, 5);
        addText(privacy, "Reminder চিকিৎসা-পরামর্শ নয়। জরুরি ওষুধের জন্য আপনার ডাক্তার/ফার্মাসিস্টের নির্দেশ ও অন্য নির্ভরযোগ্য backup alarm রাখুন।", 11, Color.rgb(155, 56, 42), true, 0, 0, 0, 0);
        content.addView(privacy);
        addText(content, "ঔষধসাথী  •  শান্তি মেডিকেয়ার দ্বারা পরিচালিত", 10, MUTED, false, 0, dp(18), 0, 0);
    }

    private void showMedicineDialog(Medicine existing) {
        final Medicine m = existing == null ? new Medicine() : existing;
        final ArrayList<String> originalTimes = new ArrayList<>(m.times);
        if (existing == null) {
            m.start = todayIso();
            m.times.add("08:00");
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(17), dp(6), dp(17), dp(10));
        EditText name = field("ওষুধের নাম *", m.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText dose = field("মাত্রা (যেমন: ১টি) *", m.dose, InputType.TYPE_CLASS_TEXT);
        EditText instruction = field("নির্দেশনা (ঐচ্ছিক)", m.instruction, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(name); form.addView(dose); form.addView(instruction);

        Spinner frequency = new Spinner(this);
        String[] frequencyLabels = {"প্রতিদিন", "নির্দিষ্ট সপ্তাহের দিন", "প্রয়োজনমতো (সময়-নির্ধারিত নয়)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, frequencyLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequency.setAdapter(adapter);
        frequency.setSelection("specific-days".equals(m.frequency) ? 1 : "as-needed".equals(m.frequency) ? 2 : 0);
        form.addView(label("কতবার?")); form.addView(frequency, margin(0, 0, 0, 9));

        EditText times = field("সময় (২৪ ঘণ্টা, কমা দিয়ে আলাদা; যেমন 08:00, 20:00)",
                m.times.isEmpty() ? "" : join(m.times, ", "), InputType.TYPE_CLASS_TEXT);
        form.addView(times);
        LinearLayout daysBox = new LinearLayout(this);
        daysBox.setOrientation(LinearLayout.VERTICAL);
        daysBox.addView(label("সপ্তাহের দিন বাছুন"));
        CheckBox[] dayChecks = new CheckBox[7];
        for (int i = 0; i < DAY_NAMES.length; i++) {
            dayChecks[i] = new CheckBox(this);
            dayChecks[i].setText(DAY_NAMES[i]);
            dayChecks[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            dayChecks[i].setChecked(m.days.contains(i));
            daysBox.addView(dayChecks[i]);
        }
        form.addView(daysBox);

        EditText start = field("শুরুর তারিখ (YYYY-MM-DD)", m.start.isEmpty() ? todayIso() : m.start, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText end = field("শেষ তারিখ (ঐচ্ছিক, YYYY-MM-DD)", m.end, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        form.addView(start); form.addView(end);
        EditText notes = field("ব্যক্তিগত নোট (ঐচ্ছিক)", m.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setMinLines(2); notes.setGravity(Gravity.TOP | Gravity.START);
        form.addView(notes);

        CheckBox stockOn = new CheckBox(this);
        stockOn.setText("ঐচ্ছিক মজুতের হিসাব রাখুন");
        stockOn.setChecked(m.stockEnabled);
        form.addView(stockOn);
        LinearLayout stockBox = new LinearLayout(this);
        stockBox.setOrientation(LinearLayout.VERTICAL);
        EditText stockRemaining = field("এখন আনুমানিক কত বাকি?", m.stockRemaining == null ? "" : String.valueOf(m.stockRemaining), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText stockUnit = field("একক (যেমন: টি, বোতল)", m.stockUnit, InputType.TYPE_CLASS_TEXT);
        EditText stockLow = field("কম মজুতের সীমা", String.valueOf(m.stockLowAt), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        stockBox.addView(stockRemaining); stockBox.addView(stockUnit); stockBox.addView(stockLow);
        form.addView(stockBox);
        stockBox.setVisibility(m.stockEnabled ? View.VISIBLE : View.GONE);
        stockOn.setOnCheckedChangeListener((button, checked) -> stockBox.setVisibility(checked ? View.VISIBLE : View.GONE));
        frequency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                daysBox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                times.setVisibility(position == 2 ? View.GONE : View.VISIBLE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "ওষুধ যোগ করুন" : "ওষুধ সম্পাদনা")
                .setView(scroll)
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("সংরক্ষণ", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            String ds = dose.getText().toString().trim();
            String startDate = start.getText().toString().trim();
            String endDate = end.getText().toString().trim();
            if (nm.isEmpty() || ds.isEmpty()) { toast("ওষুধের নাম ও মাত্রা লিখুন।"); return; }
            if (!validIso(startDate) || (!endDate.isEmpty() && !validIso(endDate)) || (!endDate.isEmpty() && endDate.compareTo(startDate) < 0)) { toast("তারিখ YYYY-MM-DD আকারে সঠিকভাবে লিখুন।"); return; }
            int freqPosition = frequency.getSelectedItemPosition();
            String freq = freqPosition == 1 ? "specific-days" : freqPosition == 2 ? "as-needed" : "daily";
            ArrayList<String> parsedTimes = new ArrayList<>();
            if (!"as-needed".equals(freq)) {
                String[] rawTimes = times.getText().toString().split(",");
                for (String rawTime : rawTimes) {
                    String value = rawTime.trim();
                    if (value.isEmpty()) continue;
                    if (!value.matches("([01]\\d|2[0-3]):[0-5]\\d")) { toast("সময় ২৪ ঘণ্টার HH:mm আকারে লিখুন, যেমন 08:00।"); return; }
                    if (!parsedTimes.contains(value)) parsedTimes.add(value);
                }
                Collections.sort(parsedTimes);
                if (parsedTimes.isEmpty()) { toast("কমপক্ষে একটি ওষুধের সময় দিন।"); return; }
            }
            ArrayList<Integer> selectedDays = new ArrayList<>();
            if ("specific-days".equals(freq)) {
                for (int i = 0; i < dayChecks.length; i++) if (dayChecks[i].isChecked()) selectedDays.add(i);
                if (selectedDays.isEmpty()) { toast("অন্তত একটি সপ্তাহের দিন বাছুন।"); return; }
            }
            Double remaining = null;
            double lowAt = 5d;
            String unit = stockUnit.getText().toString().trim();
            if (stockOn.isChecked()) {
                try { remaining = Double.parseDouble(stockRemaining.getText().toString().trim()); }
                catch (Exception ex) { toast("মজুতের বাকি পরিমাণ লিখুন।"); return; }
                try { lowAt = Double.parseDouble(stockLow.getText().toString().trim()); }
                catch (Exception ignored2) { lowAt = 5d; }
                if (remaining < 0 || lowAt < 0) { toast("মজুতের সংখ্যা ঋণাত্মক হতে পারে না।"); return; }
                if (unit.isEmpty()) unit = "টি";
            }
            m.name = nm; m.dose = ds; m.instruction = instruction.getText().toString().trim();
            m.frequency = freq; m.times.clear(); m.times.addAll(parsedTimes); m.days.clear(); m.days.addAll(selectedDays);
            m.start = startDate; m.end = endDate; m.notes = notes.getText().toString().trim();
            m.stockEnabled = stockOn.isChecked(); m.stockRemaining = remaining; m.stockUnit = unit; m.stockLowAt = lowAt;
            m.updatedAt = System.currentTimeMillis();
            ArrayList<Medicine> values = MedicineStore.medicines(this);
            if (existing == null) values.add(m);
            else {
                for (int i = 0; i < values.size(); i++) {
                    if (m.id.equals(values.get(i).id)) { values.set(i, m); break; }
                }
            }
            try {
                MedicineStore.saveMedicines(this, values);
                if (existing != null) AlarmScheduler.cancelMedicine(this, existing.id, originalTimes);
                AlarmScheduler.scheduleAll(this);
                dialog.dismiss();
                showTab("medicines");
                toast("ওষুধ ফোনে সংরক্ষিত হয়েছে।");
            } catch (JSONException error) { toast("সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।"); }
        }));
        dialog.show();
    }

    private void showStockDialog(Medicine medicine) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(17), dp(7), dp(17), dp(8));
        Spinner mode = new Spinner(this);
        String[] modes = {"ব্যবহৃত পরিমাণ কমান", "বর্তমানে বাকি পরিমাণ সেট করুন"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(adapter);
        EditText amount = field("পরিমাণ", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(mode); form.addView(amount);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("মজুত আপডেট • " + medicine.name)
                .setView(form).setNegativeButton("বাতিল", null).setPositiveButton("সংরক্ষণ", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double value;
            try { value = Double.parseDouble(amount.getText().toString().trim()); }
            catch (Exception ex) { toast("একটি সঠিক সংখ্যা লিখুন।"); return; }
            if (value < 0) { toast("ঋণাত্মক পরিমাণ গ্রহণযোগ্য নয়।"); return; }
            double remaining;
            String type;
            double recordedAmount;
            if (mode.getSelectedItemPosition() == 0) {
                if (medicine.stockRemaining == null) { toast("আগে সম্পাদনা করে বর্তমান মজুত লিখুন।"); return; }
                if (value > medicine.stockRemaining) { toast("মজুতের চেয়ে বেশি কমানো যাবে না।"); return; }
                remaining = medicine.stockRemaining - value; type = "used"; recordedAmount = value;
            } else { remaining = value; type = "set"; recordedAmount = value; }
            medicine.stockRemaining = remaining; medicine.updatedAt = System.currentTimeMillis();
            try {
                JSONArray log = new JSONArray();
                int start = Math.max(0, medicine.stockLog.length() - 29);
                for (int i = start; i < medicine.stockLog.length(); i++) log.put(medicine.stockLog.opt(i));
                JSONObject entry = new JSONObject();
                entry.put("type", type); entry.put("amount", recordedAmount); entry.put("remaining", remaining); entry.put("at", medicine.updatedAt);
                log.put(entry); medicine.stockLog = log;
                ArrayList<Medicine> values = MedicineStore.medicines(this);
                for (int i = 0; i < values.size(); i++) {
                    if (medicine.id.equals(values.get(i).id)) { values.set(i, medicine); break; }
                }
                MedicineStore.saveMedicines(this, values);
                dialog.dismiss(); showTab("medicines");
                if (medicine.stockEnabled && remaining <= medicine.stockLowAt) toast("মজুত কমে গেছে—পুনরায় সংগ্রহের কথা ভাবুন।");
            } catch (JSONException ex) { toast("মজুত সংরক্ষণ করা যায়নি।"); }
        }));
        dialog.show();
    }

    private void confirmDelete(Medicine medicine) {
        new AlertDialog.Builder(this).setTitle("ওষুধ মুছবেন?")
                .setMessage(medicine.name + " তালিকা থেকে মুছে যাবে। আগের ইতিহাস থাকবে; reminder বন্ধ হবে।")
                .setNegativeButton("বাতিল", null).setPositiveButton("মুছুন", (dialog, which) -> {
                    AlarmScheduler.cancelMedicine(this, medicine.id, medicine.times);
                    ArrayList<Medicine> medicines = MedicineStore.medicines(this);
                    for (int i = medicines.size() - 1; i >= 0; i--) if (medicine.id.equals(medicines.get(i).id)) medicines.remove(i);
                    try { MedicineStore.saveMedicines(this, medicines); AlarmScheduler.scheduleAll(this); showTab("medicines"); }
                    catch (JSONException ex) { toast("ওষুধ মুছতে সমস্যা হয়েছে।"); }
                }).show();
    }

    private void requestExactAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || AlarmScheduler.hasExactAccess(this)) {
            toast("এই ফোনে exact alarm access চালু আছে।"); return;
        }
        new AlertDialog.Builder(this).setTitle("সঠিক সময়ের reminder")
                .setMessage("ওষুধের নির্দিষ্ট সময়ে notification দিতে ঔষধসাথীর Alarms & reminders access দরকার। পরের system screen-এ app-টিকে allow করুন; অনুমতি না দিলে alarm দেরি হতে পারে।")
                .setNegativeButton("এখন নয়", null)
                .setPositiveButton("Settings খুলুন", (d, w) -> {
                    try {
                        Intent request = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(request, REQ_EXACT_ALARM);
                    } catch (Exception ex) { toast("Settings → Apps → Special app access → Alarms & reminders খুলুন।"); }
                }).show();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
            catch (Exception ex) { toast("ফোনের Settings → Apps → ঔষধসাথী → Notifications খুলুন।"); }
        } else toast("এই Android সংস্করণে Notification চালু আছে কি না App settings-এ দেখুন।");
    }

    private void openBatterySettings() {
        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (Exception ex) { toast("Settings → Battery → App battery management থেকে ঔষধসাথীকে unrestricted করুন, যদি অপশন থাকে।"); }
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQ_IMPORT);
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "oushodhsathi-backup-" + todayIso() + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { if (requestCode == REQ_EXACT_ALARM) showTab(activeTab); return; }
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) readImportFile(uri);
        else if (requestCode == REQ_EXPORT) writeExportFile(uri);
        else if (requestCode == REQ_EXACT_ALARM) { AlarmScheduler.scheduleAll(this); showTab(activeTab); }
    }

    private void readImportFile(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Could not open selected file");
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            JSONObject backup = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            JSONArray meds = backup.optJSONArray("medicines");
            if (meds == null) throw new JSONException("Backup has no medicine list");
            int valid = 0;
            for (int i = 0; i < meds.length(); i++) if (Medicine.fromJson(meds.optJSONObject(i)) != null) valid++;
            int count = valid;
            new AlertDialog.Builder(this).setTitle("PWA backup import")
                    .setMessage(bn(count) + "টি ওষুধ ও status history import হবে। এটি এই native app-এর বর্তমান ওষুধ ও history replace করবে; PWA browser-এর মূল ফাইল বদলাবে না।")
                    .setNegativeButton("বাতিল", null)
                    .setPositiveButton("Import ও replace", (d, w) -> {
                        try {
                            MedicineStore.importBackup(this, backup);
                            AlarmScheduler.scheduleAll(this);
                            showTab("home");
                            toast("Backup import হয়েছে। Permission status Settings-এ যাচাই করুন।");
                        } catch (JSONException ex) { toast("এই backup পড়া যায়নি।"); }
                    }).show();
        } catch (Exception ex) { toast("Backup ফাইল পড়া যায়নি। PWA থেকে নতুন JSON export করে আবার চেষ্টা করুন।"); }
    }

    private void writeExportFile(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Could not write file");
            output.write(MedicineStore.backup(this).toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("Backup ফোনে save হয়েছে। ব্যক্তিগত স্বাস্থ্যতথ্য—সাবধানে রাখুন।");
        } catch (Exception ex) { toast("Backup save করা যায়নি।"); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) toast("Notification অনুমতি চালু হয়েছে।");
            else toast("Notification বন্ধ আছে; Android Settings থেকে পরে চালু করতে পারেন।");
            showTab(activeTab);
        }
    }

    private void addText(LinearLayout parent, String value, int sp, int color, boolean bold, int left, int top, int right, int bottom) {
        TextView view = text(value, sp, color, bold);
        parent.addView(view, margin(left, top, right, bottom));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(foreground);
        view.setMinHeight(dp(46));
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        view.setBackground(round(background, dp(14), background == CARD || background == PAGE ? Color.rgb(232, 237, 231) : background));
        return view;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(round(CARD, dp(19), Color.rgb(238, 241, 236)));
        return box;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(radius); d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(left, top, right, bottom);
        return lp;
    }

    private EditText field(String hint, String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        edit.setTextColor(INK);
        edit.setInputType(inputType);
        edit.setPadding(dp(12), dp(7), dp(12), dp(7));
        edit.setBackground(round(Color.WHITE, dp(12), Color.rgb(222, 228, 220)));
        LinearLayout.LayoutParams lp = margin(0, dp(4), 0, dp(8));
        edit.setLayoutParams(lp);
        return edit;
    }

    private TextView label(String value) {
        TextView tv = text(value, 12, MUTED, true);
        tv.setPadding(0, dp(5), 0, dp(3));
        return tv;
    }

    private String scheduleText(Medicine m) {
        if ("as-needed".equals(m.frequency)) return "প্রয়োজনমতো • নির্দিষ্ট alarm নেই";
        StringBuilder out = new StringBuilder("সময়: ");
        for (int i = 0; i < m.times.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(displayTime(m.times.get(i)));
        }
        if ("specific-days".equals(m.frequency)) {
            out.append(" • ");
            for (int i = 0; i < m.days.size(); i++) { if (i > 0) out.append(", "); out.append(DAY_NAMES[m.days.get(i)]); }
        } else out.append(" • প্রতিদিন");
        return out.toString();
    }

    private String statusLabel(String status) {
        if ("taken".equals(status)) return "আপনি “খেয়েছি” বেছে নিয়েছেন";
        if ("later".equals(status)) return "আপনি “পরে খাব” বেছে নিয়েছেন";
        if ("unsure".equals(status)) return "আপনি “নিশ্চিত নই” বেছে নিয়েছেন";
        return "অপেক্ষায় • status নিজে বেছে নিন";
    }

    private String todayIso() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String todayLabel() { return new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("bn", "BD")).format(new Date()); }
    private String formatIsoDate(String value) {
        try { SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US); in.setLenient(false); Date d = in.parse(value); return new SimpleDateFormat("d MMM yyyy", new Locale("bn", "BD")).format(d); }
        catch (Exception ex) { return value; }
    }
    private String displayTime(String value) {
        try {
            String[] parts = value.split(":");
            Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0])); c.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            return new SimpleDateFormat("h:mm a", new Locale("bn", "BD")).format(c.getTime());
        } catch (Exception ex) { return value; }
    }
    private String bn(int value) { return java.text.NumberFormat.getIntegerInstance(new Locale("bn", "BD")).format(value); }
    private String bnDecimal(double value) { return java.text.NumberFormat.getNumberInstance(new Locale("bn", "BD")).format(value); }
    private String join(ArrayList<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) { if (out.length() > 0) out.append(separator); out.append(value); }
        return out.toString();
    }
    private boolean validIso(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try { SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US); format.setLenient(false); Date parsed = format.parse(value); return parsed != null && format.format(parsed).equals(value); }
        catch (Exception ex) { return false; }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static final class DoseSlot {
        final Medicine medicine; final String time;
        DoseSlot(Medicine medicine, String time) { this.medicine = medicine; this.time = time; }
    }
}
