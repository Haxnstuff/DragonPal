package com.dragonpal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Settings + rolling memory + mood. All persistence lives here. */
public class MemoryStore {
    public static final String PREFS = "dragon_prefs";
    public static final String MEMORY = "dragon_memory";
    private final SharedPreferences p;

    public MemoryStore(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int providerIndex() { return p.getInt("provider", 0); }
    public void setProviderIndex(int i) { p.edit().putInt("provider", i).apply(); }

    /** API key for the currently selected provider (used by the AI client). */
    public String apiKey() { return apiKeyFor(providerIndex()); }
    public void setApiKey(String v) { setApiKeyFor(providerIndex(), v); }

    /** API key stored for a specific provider index, so each provider's key is remembered. */
    public String apiKeyFor(int provider) { return p.getString("key_" + provider, ""); }
    public void setApiKeyFor(int provider, String v) { p.edit().putString("key_" + provider, v).apply(); }

    /** One-time: move the old single "api_key" value into the detected provider's slot. */
    public void migrateLegacyKey(int provider) {
        String legacy = p.getString("api_key", "");
        if (!legacy.isEmpty() && apiKeyFor(provider).isEmpty()) {
            p.edit().putString("key_" + provider, legacy).remove("api_key").apply();
        }
    }

    public String lastError() { return p.getString("last_error", ""); }
    public void setLastError(String e) { p.edit().putString("last_error", e).apply(); }
    public void clearLastError() { p.edit().remove("last_error").apply(); }

    public String baseUrl() { return p.getString("base_url", "https://api.openai.com/v1"); }
    public void setBaseUrl(String v) { p.edit().putString("base_url", v).apply(); }

    public String model() { return p.getString("model", "gpt-4o-mini"); }
    public void setModel(String v) { p.edit().putString("model", v).apply(); }

    public String visionModel() { return p.getString("vision_model", "gpt-4o-mini"); }
    public void setVisionModel(String v) { p.edit().putString("vision_model", v).apply(); }

    /** Optional vision endpoint + key overrides. Empty means "use the chat provider's". */
    public String visionBaseUrl() { return p.getString("vision_base_url", ""); }
    public void setVisionBaseUrl(String v) { p.edit().putString("vision_base_url", v).apply(); }
    public String effectiveVisionBaseUrl() {
        String v = visionBaseUrl();
        return (v == null || v.isEmpty()) ? baseUrl() : v;
    }

    public String visionApiKey() { return p.getString("vision_api_key", ""); }
    public void setVisionApiKey(String v) { p.edit().putString("vision_api_key", v).apply(); }
    public String effectiveVisionApiKey() {
        String v = visionApiKey();
        return v.isEmpty() ? apiKey() : v;
    }

    public String dragonName() { return p.getString("name", "Ember"); }
    public void setDragonName(String v) { p.edit().putString("name", v).apply(); }

    public int maxTokens() { return p.getInt("max_tokens", 400); }

    /** Whether the dragon wanders the screen on its own. Off by default. */
    public boolean roaming() { return p.getBoolean("roaming", false); }
    public void setRoaming(boolean v) { p.edit().putBoolean("roaming", v).apply(); }

    public String mood() { return p.getString("mood", "happy"); }
    public void setMood(String v) { p.edit().putString("mood", v).apply(); }

    public void remember(String role, String text) {
        try {
            JSONArray a = history();
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("text", text);
            a.put(o);
            while (a.length() > 40) a.remove(0);
            p.edit().putString(MEMORY, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public JSONArray history() {
        String s = p.getString(MEMORY, "[]");
        try { return new JSONArray(s); } catch (Exception e) { return new JSONArray(); }
    }

    /** Last n turns as compact text, for system-prompt context (personalization). */
    public String recent(int n) {
        try {
            JSONArray a = history();
            int start = Math.max(0, a.length() - n);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String t = o.getString("text");
                if (t.length() > 160) t = t.substring(0, 160) + "...";
                sb.append(o.getString("role")).append(": ").append(t).append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    public void resetMemory() {
        p.edit().remove(MEMORY).remove("mood").apply();
    }
}
