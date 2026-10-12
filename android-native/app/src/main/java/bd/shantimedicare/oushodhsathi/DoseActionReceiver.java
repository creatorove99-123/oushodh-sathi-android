package bd.shantimedicare.oushodhsathi;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DoseActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String medicineId = intent.getStringExtra("medicineId");
        String date = intent.getStringExtra("date");
        String time = intent.getStringExtra("time");
        String status = intent.getStringExtra("status");
        if (medicineId == null || date == null || time == null) return;
        if (!("taken".equals(status) || "later".equals(status))) return;
        MedicineStore.setDoseStatus(context, medicineId, date, time, status);
        int id = intent.getIntExtra("notificationId", -1);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && id >= 0) manager.cancel(id);
        if ("later".equals(status)) AlarmScheduler.scheduleSnooze(context, medicineId, date, time);
    }
}
