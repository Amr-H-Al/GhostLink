package com.example.ghostlink;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StatsManager {
    private static final String PREF_NAME = "ghostlink_stats";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_SAFE_COUNT = "safe_count";
    private static final String KEY_RISKY_COUNT = "risky_count";
    private static final String KEY_DANGER_COUNT = "danger_count";

    private final SharedPreferences prefs;
    private final Gson gson;
    private final Context context;

    public StatsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void addHistoryItem(HistoryItem item) {
        List<HistoryItem> history = getHistory();
        history.add(0, item); // Add to beginning
        if (history.size() > 50) {
            history = history.subList(0, 50);
        }
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();

        if ("SAFE".equals(item.getLabel())) {
            incrementCount(KEY_SAFE_COUNT);
        } else if ("RISKY".equals(item.getLabel())) {
            incrementCount(KEY_RISKY_COUNT);
        } else if ("NOT_SAFE".equals(item.getLabel())) {
            incrementCount(KEY_DANGER_COUNT);
        }
    }

    public void updateHistoryItem(HistoryItem updatedItem) {
        List<HistoryItem> history = getHistory();
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(updatedItem.getId())) {
                history.set(i, updatedItem);
                prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
                break;
            }
        }
    }

    public void deleteHistoryItem(String id) {
        if (id == null) return;
        List<HistoryItem> history = getHistory();
        
        HistoryItem toRemove = null;
        for (HistoryItem item : history) {
            String itemId = item.getId();
            if (itemId == null) itemId = String.valueOf(item.getTimestamp());
            
            if (itemId.equals(id)) {
                toRemove = item;
                break;
            }
        }
        
        if (toRemove != null) {
            history.remove(toRemove);
            if (toRemove.getScreenshotPath() != null) {
                try {
                    File file = new File(toRemove.getScreenshotPath());
                    if (file.exists()) file.delete();
                } catch (Exception e) {
                    Log.e("GhostLink", "Failed to delete screenshot", e);
                }
            }
            prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
        }
    }

    /**
     * Clear all history AND reset scan counts to zero.
     */
    public void clearAllData() {
        // Delete all screenshot files first
        List<HistoryItem> history = getHistory();
        for (HistoryItem item : history) {
            if (item.getScreenshotPath() != null) {
                try {
                    File file = new File(item.getScreenshotPath());
                    if (file.exists()) file.delete();
                } catch (Exception ignored) {}
            }
        }

        // Clear prefs
        prefs.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_SAFE_COUNT)
            .remove(KEY_RISKY_COUNT)
            .remove(KEY_DANGER_COUNT)
            .apply();
    }

    private void incrementCount(String key) {
        int current = prefs.getInt(key, 0);
        prefs.edit().putInt(key, current + 1).apply();
    }

    public List<HistoryItem> getHistory() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        try {
            List<HistoryItem> items = gson.fromJson(json, new TypeToken<List<HistoryItem>>(){}.getType());
            return items != null ? items : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public int getSafeCount() { return prefs.getInt(KEY_SAFE_COUNT, 0); }
    public int getRiskyCount() { return prefs.getInt(KEY_RISKY_COUNT, 0); }
    public int getDangerCount() { return prefs.getInt(KEY_DANGER_COUNT, 0); }
}
