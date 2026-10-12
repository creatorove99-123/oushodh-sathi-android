package bd.shantimedicare.oushodhsathi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.TimeZone;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String medicineId = intent.getStringExtra("medicineId");
        String time = intent.getStringExtra("time");
        String date = intent.getStringExtra("date");
        if (medicineId == null || time == null) return;
        Medicine medicine = MedicineStore.find(context, medicineId);
        if (medicine == null) return;
        if (!medicine.times.contains(time)) return;

        if (AlarmScheduler.ACTION_SNOOZE_DUE.equals(intent.getAction())) {
            if (date != null && "later".equals(MedicineStore.doseStatus(context, date, medicineId, time))) {
                AlarmScheduler.showDoseNotification(context, medicine, date, time);
            }
            return;
        }

        long scheduledAt = intent.getLongExtra("scheduledAt", System.currentTimeMillis());
        String doseDate = ScheduleCalculator.dateString(scheduledAt, TimeZone.getDefault());
        AlarmScheduler.scheduleNextForSlot(context, medicine, time, System.currentTimeMillis());
        if (System.currentTimeMillis() - scheduledAt > 6L * 60L * 60L * 1000L) return;
        String status = MedicineStore.doseStatus(context, doseDate, medicineId, time);
        if (!"pending".equals(status)) return;
        MedicineStore.markPending(context, medicine, doseDate, time);
        AlarmScheduler.showDoseNotification(context, medicine, doseDate, time);
    }
}
