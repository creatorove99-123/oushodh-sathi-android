package bd.shantimedicare.oushodhsathi;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Pure Java calendar logic; each result is scheduled as an individual exact alarm. */
public final class ScheduleCalculator {
    private ScheduleCalculator() {}

    public static long nextOccurrence(String frequency, List<Integer> days, String start,
                                      String end, String hhmm, long afterMillis, TimeZone zone) {
        if ("as-needed".equals(frequency) || hhmm == null || !hhmm.matches("([01]\\d|2[0-3]):[0-5]\\d")) return -1L;
        String[] parts = hhmm.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        Calendar cursor = Calendar.getInstance(zone);
        cursor.setTimeInMillis(afterMillis);
        cursor.set(Calendar.HOUR_OF_DAY, 0);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);
        Calendar startDate = parseDate(start, zone);
        Calendar endDate = parseDate(end, zone);

        for (int offset = 0; offset <= 370; offset++) {
            Calendar day = (Calendar) cursor.clone();
            day.add(Calendar.DAY_OF_MONTH, offset);
            if (startDate != null && day.before(startDate)) continue;
            if (endDate != null && day.after(endDate)) return -1L;
            int sundayZeroDay = day.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            if ("specific-days".equals(frequency) && (days == null || !days.contains(sundayZeroDay))) continue;
            Calendar candidate = (Calendar) day.clone();
            candidate.set(Calendar.HOUR_OF_DAY, hour);
            candidate.set(Calendar.MINUTE, minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            if (candidate.getTimeInMillis() > afterMillis) return candidate.getTimeInMillis();
        }
        return -1L;
    }

    public static boolean occursOn(String frequency, List<Integer> days, String start,
                                   String end, Calendar day) {
        if ("as-needed".equals(frequency)) return false;
        TimeZone zone = day.getTimeZone();
        Calendar date = (Calendar) day.clone();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Calendar startDate = parseDate(start, zone);
        Calendar endDate = parseDate(end, zone);
        if (startDate != null && date.before(startDate)) return false;
        if (endDate != null && date.after(endDate)) return false;
        if ("specific-days".equals(frequency)) {
            int sundayZeroDay = date.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            return days != null && days.contains(sundayZeroDay);
        }
        return true;
    }

    public static String dateString(long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(zone);
        return format.format(millis);
    }

    public static String localTime(long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);
        format.setTimeZone(zone);
        return format.format(millis);
    }

    private static Calendar parseDate(String value, TimeZone zone) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(zone);
        ParsePosition position = new ParsePosition(0);
        java.util.Date parsed = format.parse(value, position);
        if (parsed == null || position.getIndex() != value.length()) return null;
        Calendar result = Calendar.getInstance(zone);
        result.setTime(parsed);
        result.set(Calendar.HOUR_OF_DAY, 0);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }
}
