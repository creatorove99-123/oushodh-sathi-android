package bd.shantimedicare.oushodhsathi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** App-private, offline-only storage. No network permission or cloud sync is used. */
public final class MedicineStore {
    private static final String PREFS = "oushodhsathi_private_v1";
    private static final String MEDICINES = "medicines_json";
    private static final String HISTORY = "history_json";
    private static final int MAX_HISTORY = 1500;
    private MedicineStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ArrayList<Medicine> medicines(Context c) {
        ArrayList<Medicine> result = new ArrayList<>();
        JSONArray raw;
        try { raw = new JSONArray(prefs(c).getString(MEDICINES, "[]")); }
        catch (JSONException error) { return result; }
        for (int i = 0; i < raw.length(); i++) {
            Medicine m = Medicine.fromJson(raw.optJSONObject(i));
            if (m != null) result.add(m);
        }
        Collections.sort(result, new Comparator<Medicine>() {
            @Override public int compare(Medicine a, Medicine b) {
                return a.name.toLowerCase(java.util.Locale.ROOT).compareTo(b.name.toLowerCase(java.util.Locale.ROOT));
            }
        });
        return result;
    }

    public static Medicine find(Context c, String id) {
        if (id == null) return null;
        for (Medicine m : medicines(c)) if (id.equals(m.id)) return m;
        return null;
    }

    public static void saveMedicines(Context c, List<Medicine> values) throws JSONException {
        JSONArray out = new JSONArray();
        for (Medicine m : values) out.put(m.toJson());
        prefs(c).edit().putString(MEDICINES, out.toString()).apply();
    }

    public static ArrayList<JSONObject> history(Context c) {
        ArrayList<JSONObject> result = new ArrayList<>();
        JSONArray raw;
        try { raw = new JSONArray(prefs(c).getString(HISTORY, "[]")); }
        catch (JSONException error) { return result; }
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item != null && item.optString("key", "").contains("|")) result.add(item);
        }
        Collections.sort(result, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int date = a.optString("date", "").compareTo(b.optString("date", ""));
                return date != 0 ? date : a.optString("time", "").compareTo(b.optString("time", ""));
            }
        });
        return result;
    }

    private static void saveHistory(Context c, List<JSONObject> records) {
        int from = Math.max(0, records.size() - MAX_HISTORY);
        JSONArray out = new JSONArray();
        for (int i = from; i < records.size(); i++) out.put(records.get(i));
        prefs(c).edit().putString(HISTORY, out.toString()).apply();
    }

    public static String doseKey(String date, String medicineId, String time) {
        return date + "|" + medicineId + "|" + time;
    }

    public static String doseStatus(Context c, String date, String medicineId, String time) {
        String key = doseKey(date, medicineId, time);
        for (JSONObject item : history(c)) if (key.equals(item.optString("key"))) return item.optString("status", "pending");
        return "pending";
    }

    public static void markPending(Context c, Medicine m, String date, String time) {
        String key = doseKey(date, m.id, time);
        ArrayList<JSONObject> records = history(c);
        for (JSONObject item : records) if (key.equals(item.optString("key"))) return;
        try {
            JSONObject item = new JSONObject();
            item.put("key", key);
            item.put("medicineId", m.id);
            item.put("medicineName", m.name);
            item.put("dose", m.dose);
            item.put("instruction", m.instruction);
            item.put("date", date);
            item.put("time", time);
            item.put("status", "pending");
            item.put("createdAt", System.currentTimeMillis());
            item.put("updatedAt", System.currentTimeMillis());
            records.add(item);
            saveHistory(c, records);
        } catch (JSONException ignored) { }
    }

    public static void setDoseStatus(Context c, String medicineId, String date, String time, String status) {
        if (!("taken".equals(status) || "later".equals(status) || "unsure".equals(status))) return;
        String key = doseKey(date, medicineId, time);
        ArrayList<JSONObject> records = history(c);
        JSONObject target = null;
        for (JSONObject item : records) if (key.equals(item.optString("key"))) { target = item; break; }
        if (target == null) {
            target = new JSONObject();
            try {
                target.put("key", key);
                target.put("medicineId", medicineId);
                target.put("date", date);
                target.put("time", time);
                Medicine m = find(c, medicineId);
                if (m != null) {
                    target.put("medicineName", m.name);
                    target.put("dose", m.dose);
                    target.put("instruction", m.instruction);
                }
                target.put("createdAt", System.currentTimeMillis());
            } catch (JSONException ignored) { }
            records.add(target);
        }
        try {
            target.put("status", status);
            target.put("updatedAt", System.currentTimeMillis());
            saveHistory(c, records);
        } catch (JSONException ignored) { }
    }

    public static void importBackup(Context c, JSONObject backup) throws JSONException {
        JSONArray medicines = backup.optJSONArray("medicines");
        if (medicines == null) throw new JSONException("Backup does not contain a medicine list");
        JSONArray validMedicines = new JSONArray();
        for (int i = 0; i < medicines.length(); i++) {
            Medicine m = Medicine.fromJson(medicines.optJSONObject(i));
            if (m != null) validMedicines.put(m.toJson());
        }
        JSONArray history = backup.optJSONArray("history");
        if (history == null) history = new JSONArray();
        JSONArray validHistory = new JSONArray();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            String date = item.optString("date", "");
            String status = item.optString("status", "");
            if (date.matches("\\d{4}-\\d{2}-\\d{2}") &&
                    ("pending".equals(status) || "taken".equals(status) || "later".equals(status) || "unsure".equals(status))) {
                validHistory.put(item);
            }
        }
        prefs(c).edit().putString(MEDICINES, validMedicines.toString())
                .putString(HISTORY, validHistory.toString()).apply();
    }

    public static JSONObject backup(Context c) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("schema", "oushodhsathi.backup.v1");
        result.put("exportedAt", System.currentTimeMillis());
        JSONArray meds = new JSONArray();
        for (Medicine m : medicines(c)) meds.put(m.toJson());
        result.put("medicines", meds);
        JSONArray records = new JSONArray();
        for (JSONObject item : history(c)) records.put(item);
        result.put("history", records);
        return result;
    }
}
