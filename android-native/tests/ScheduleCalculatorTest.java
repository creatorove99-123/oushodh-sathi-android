package bd.shantimedicare.oushodhsathi;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class ScheduleCalculatorTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static long at(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(UTC);
        return format.parse(value).getTime();
    }

    private static void eq(String label, long expected, long actual) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) throws Exception {
        eq("daily next same day", at("2026-10-12 20:00"),
                ScheduleCalculator.nextOccurrence("daily", null, "2026-10-12", "", "20:00", at("2026-10-12 08:00"), UTC));
        eq("daily next day", at("2026-10-13 08:00"),
                ScheduleCalculator.nextOccurrence("daily", null, "2026-10-12", "", "08:00", at("2026-10-12 08:00"), UTC));
        eq("weekly next Monday", at("2026-10-12 09:00"),
                ScheduleCalculator.nextOccurrence("specific-days", Arrays.asList(1, 3), "2026-10-12", "", "09:00", at("2026-10-11 10:00"), UTC));
        eq("future start date", at("2026-10-14 07:30"),
                ScheduleCalculator.nextOccurrence("daily", null, "2026-10-14", "", "07:30", at("2026-10-12 10:00"), UTC));
        eq("end date includes its day", at("2026-10-12 20:00"),
                ScheduleCalculator.nextOccurrence("daily", null, "2026-10-10", "2026-10-12", "20:00", at("2026-10-12 08:00"), UTC));
        eq("end date excludes later", -1L,
                ScheduleCalculator.nextOccurrence("daily", null, "2026-10-10", "2026-10-12", "07:30", at("2026-10-12 08:00"), UTC));
        eq("as needed has no alarm", -1L,
                ScheduleCalculator.nextOccurrence("as-needed", null, "2026-10-12", "", "07:30", at("2026-10-12 08:00"), UTC));
        Calendar monday = Calendar.getInstance(UTC);
        monday.setTimeInMillis(at("2026-10-12 12:00"));
        if (!ScheduleCalculator.occursOn("specific-days", Arrays.asList(1), "2026-10-12", "", monday)) throw new AssertionError("Monday should be scheduled");
        System.out.println("ScheduleCalculator tests passed: 8 checks");
    }
}
