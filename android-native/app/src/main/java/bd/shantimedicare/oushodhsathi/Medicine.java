package bd.shantimedicare.oushodhsathi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class Medicine {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String dose = "";
    public String instruction = "";
    public String frequency = "daily";
    public final ArrayList<String> times = new ArrayList<>();
    public final ArrayList<Integer> days = new ArrayList<>();
    public String start = "";
    public String end = "";
    public String notes = "";
    public boolean stockEnabled;
    public Double stockRemaining;
    public String stockUnit = "টি";
    public double stockLowAt = 5d;
    public JSONArray stockLog = new JSONArray();
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();

    public static Medicine fromJson(JSONObject raw) {
        if (raw == null) return null;
        Medicine m = new Medicine();
        m.id = raw.optString("id", m.id);
        m.name = raw.optString("name", "").trim();
        if (m.name.isEmpty()) return null;
        m.dose = raw.optString("dose", "");
        m.instruction = raw.optString("instruction", "");
        String freq = raw.optString("frequency", "daily");
        m.frequency = ("daily".equals(freq) || "specific-days".equals(freq) || "as-needed".equals(freq)) ? freq : "daily";
        JSONArray times = raw.optJSONArray("times");
        if (times != null) {
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (int i = 0; i < times.length(); i++) {
                String t = times.optString(i, "");
                if (t.matches("([01]\\d|2[0-3]):[0-5]\\d")) unique.add(t);
            }
            m.times.addAll(unique);
        }
        JSONArray days = raw.optJSONArray("days");
        if (days != null) {
            LinkedHashSet<Integer> unique = new LinkedHashSet<>();
            for (int i = 0; i < days.length(); i++) {
                int day = days.optInt(i, -1);
                if (day >= 0 && day <= 6) unique.add(day);
            }
            m.days.addAll(unique);
        }
        m.start = raw.optString("start", "");
        m.end = raw.optString("end", "");
        m.notes = raw.optString("notes", "");
        m.stockEnabled = raw.optBoolean("stockEnabled", false);
        if (!raw.isNull("stockRemaining")) {
            double remaining = raw.optDouble("stockRemaining", Double.NaN);
            if (!Double.isNaN(remaining) && !Double.isInfinite(remaining) && remaining >= 0) m.stockRemaining = remaining;
        }
        m.stockUnit = raw.optString("stockUnit", "টি");
        if (m.stockUnit.trim().isEmpty()) m.stockUnit = "টি";
        m.stockLowAt = Math.max(0, raw.optDouble("stockLowAt", 5));
        JSONArray log = raw.optJSONArray("stockLog");
        if (log != null) m.stockLog = log;
        m.createdAt = raw.optLong("createdAt", m.createdAt);
        m.updatedAt = raw.optLong("updatedAt", m.updatedAt);
        if ("as-needed".equals(m.frequency)) {
            m.times.clear();
            m.days.clear();
        } else if ("specific-days".equals(m.frequency)) {
            Collections.sort(m.days);
        } else {
            m.days.clear();
        }
        Collections.sort(m.times);
        return m;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("id", id);
        out.put("name", name);
        out.put("dose", dose);
        out.put("instruction", instruction);
        out.put("frequency", frequency);
        out.put("times", new JSONArray(times));
        out.put("days", new JSONArray(days));
        out.put("start", start);
        out.put("end", end);
        out.put("notes", notes);
        out.put("stockEnabled", stockEnabled);
        out.put("stockRemaining", stockRemaining == null ? JSONObject.NULL : stockRemaining);
        out.put("stockUnit", stockUnit);
        out.put("stockLowAt", stockLowAt);
        out.put("stockLog", stockLog);
        out.put("createdAt", createdAt);
        out.put("updatedAt", updatedAt);
        return out;
    }
}
