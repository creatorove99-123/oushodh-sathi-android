package bd.shantimedicare.oushodhsathi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        // AlarmManager clears alarms on shutdown. This receiver recreates future alarms
        // after BOOT_COMPLETED (after the user has launched the app once), app updates,
        // local-clock changes, and exact-alarm access grants.
        AlarmScheduler.createChannel(context);
        AlarmScheduler.scheduleAll(context);
    }
}
