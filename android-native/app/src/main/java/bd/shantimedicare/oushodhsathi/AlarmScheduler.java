package bd.shantimedicare.oushodhsathi;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class AlarmScheduler {
    public static final String CHANNEL_ID = "medicine_reminders_v1";
    public static final String ACTION_DUE = "bd.shantimedicare.oushodhsathi.DUE";
    public static final String ACTION_SNOOZE_DUE = "bd.shantimedicare.oushodhsathi.SNOOZE_DUE";
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    private AlarmScheduler() {}

    public static boolean hasExactAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.areNotificationsEnabled();
        }
        return true;
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.channel_description));
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    public static void scheduleAll(Context context) {
        long now = System.currentTimeMillis();
        for (Medicine m : MedicineStore.medicines(context)) {
            if ("as-needed".equals(m.frequency)) continue;
            for (String time : m.times) scheduleNextForSlot(context, m, time, now);
        }
    }

    public static void scheduleNextForSlot(Context context, Medicine medicine, String time, long afterMillis) {
        if (medicine == null || time == null || "as-needed".equals(medicine.frequency)) return;
        TimeZone zone = TimeZone.getDefault();
        long next = ScheduleCalculator.nextOccurrence(medicine.frequency, medicine.days,
                medicine.start, medicine.end, time, afterMillis, zone);
        if (next <= 0) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pi = reminderPendingIntent(context, medicine.id, time, next);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasExactAccess(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (SecurityException ignored) {
            try { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi); }
            catch (RuntimeException ignoredAgain) { }
        } catch (RuntimeException ignored) { }
    }

    public static void cancelMedicine(Context context, String medicineId, List<String> times) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null || times == null) return;
        for (String time : times) {
            Intent intent = reminderIntent(context, medicineId, time, 0L);
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(medicineId + "|" + time), intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                manager.cancel(pi);
                pi.cancel();
            }
        }
    }

    public static void scheduleSnooze(Context context, String medicineId, String date, String time) {
        long at = System.currentTimeMillis() + 10L * 60L * 1000L;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_SNOOZE_DUE)
                .setData(Uri.parse("oushodhsathi://snooze/" + Uri.encode(medicineId) + "/" + date + "/" + Uri.encode(time)))
                .putExtra("medicineId", medicineId).putExtra("date", date).putExtra("time", time).putExtra("scheduledAt", at);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode("snooze|" + medicineId + "|" + date + "|" + time), intent, FLAGS);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasExactAccess(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else manager.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (RuntimeException ignored) {
            try { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); } catch (RuntimeException ignoredAgain) { }
        }
    }

    private static PendingIntent reminderPendingIntent(Context context, String id, String time, long at) {
        Intent intent = reminderIntent(context, id, time, at);
        return PendingIntent.getBroadcast(context, requestCode(id + "|" + time), intent, FLAGS);
    }

    private static Intent reminderIntent(Context context, String id, String time, long at) {
        return new Intent(context, ReminderReceiver.class).setAction(ACTION_DUE)
                .setData(Uri.parse("oushodhsathi://reminder/" + Uri.encode(id) + "/" + Uri.encode(time)))
                .putExtra("medicineId", id).putExtra("time", time).putExtra("scheduledAt", at);
    }

    private static int requestCode(String value) { return value.hashCode() & 0x7fffffff; }

    public static int notificationId(String key) { return key.hashCode() & 0x7fffffff; }

    public static void showDoseNotification(Context context, Medicine medicine, String date, String time) {
        if (medicine == null || !notificationsAllowed(context)) return;
        createChannel(context);
        String key = MedicineStore.doseKey(date, medicine.id, time);
        int id = notificationId(key);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        String title = "ঔষধসাথী • " + medicine.name;
        String detail = medicine.dose.isEmpty() ? "এখন ওষুধের সময়" : medicine.dose;
        if (!medicine.instruction.isEmpty()) detail += " • " + medicine.instruction;
        detail += " • " + displayTime(time);
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, requestCode("open|" + key), open, FLAGS);
        builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail + "\nনিজে status বেছে নিন; app নিজে থেকে খেয়েছেন বলে চিহ্নিত করে না।"))
                .setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true).setContentIntent(openPi).setVisibility(Notification.VISIBILITY_PRIVATE);
        builder.addAction(0, "খেয়েছি", actionIntent(context, "taken", medicine.id, date, time, id));
        builder.addAction(0, "১০ মিনিট পরে", actionIntent(context, "later", medicine.id, date, time, id));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(id, builder.build());
    }

    private static PendingIntent actionIntent(Context context, String action, String medicineId, String date, String time, int notificationId) {
        Intent intent = new Intent(context, DoseActionReceiver.class).setAction("bd.shantimedicare.oushodhsathi." + action.toUpperCase(Locale.ROOT))
                .setData(Uri.parse("oushodhsathi://action/" + action + "/" + Uri.encode(medicineId) + "/" + date + "/" + Uri.encode(time)))
                .putExtra("medicineId", medicineId).putExtra("date", date).putExtra("time", time)
                .putExtra("notificationId", notificationId).putExtra("status", action);
        return PendingIntent.getBroadcast(context, requestCode(action + "|" + medicineId + "|" + date + "|" + time), intent, FLAGS);
    }

    private static String displayTime(String time) {
        try {
            String[] parts = time.split(":");
            CalendarHolder holder = new CalendarHolder(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", new Locale("bn", "BD"));
            return fmt.format(new Date(holder.millis));
        } catch (Exception ignored) { return time; }
    }

    private static final class CalendarHolder {
        final long millis;
        CalendarHolder(int hour, int minute) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.HOUR_OF_DAY, hour); c.set(java.util.Calendar.MINUTE, minute);
            c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
            millis = c.getTimeInMillis();
        }
    }
}
