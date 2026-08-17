package com.dragonpal;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generic OpenAI-compatible chat + vision client. No external deps. */
public class AiClient {

    public interface Callback {
        void onResult(String text);
        void onError(String err);
    }

    public static class Msg {
        public String role;         // "user" or "assistant"
        public String content;      // text
        public String imageBase64;  // optional PNG for vision
    }

    public static void chat(MemoryStore mem, List<Msg> msgs, String modelOverride, Callback cb) {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray();
                arr.put(system(mem));
                for (Msg m : msgs) arr.put(toJson(m));
                JSONObject body = base(arr);
                body.put("model", pick(mem, modelOverride));
                body.put("temperature", 0.8);
                body.put("max_tokens", mem.maxTokens());
                post(mem.baseUrl(), mem.apiKey(), body, cb);
            } catch (Exception e) {
                cb.onError(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    public static void test(MemoryStore mem, Callback cb) {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray();
                JSONObject u = new JSONObject();
                u.put("role", "user");
                u.put("content", "ping");
                arr.put(u);
                JSONObject body = new JSONObject();
                body.put("model", mem.model());
                body.put("messages", arr);
                body.put("max_tokens", 5);
                post(mem.baseUrl(), mem.apiKey(), body, cb);
            } catch (Exception e) {
                cb.onError(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    public static void vision(MemoryStore mem, String pngBase64, String prompt, Callback cb) {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray();
                arr.put(system(mem));
                Msg m = new Msg();
                m.role = "user";
                m.content = prompt;
                m.imageBase64 = pngBase64;
                arr.put(toJson(m));
                JSONObject body = base(arr);
                body.put("model", mem.visionModel());
                body.put("max_tokens", 300);
                post(mem.effectiveVisionBaseUrl(), mem.effectiveVisionApiKey(), body, cb);
            } catch (Exception e) {
                cb.onError(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private static String pick(MemoryStore mem, String override) {
        return (override != null && !override.isEmpty()) ? override : mem.model();
    }

    private static JSONObject system(MemoryStore mem) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", "system");
        o.put("content", DragonPersona.systemPrompt(mem));
        return o;
    }

    private static JSONObject base(JSONArray messages) throws Exception {
        JSONObject o = new JSONObject();
        o.put("messages", messages);
        return o;
    }

    private static JSONObject toJson(Msg m) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", m.role);
        if (m.imageBase64 != null) {
            JSONArray parts = new JSONArray();
            JSONObject t = new JSONObject();
            t.put("type", "text");
            t.put("text", m.content);
            parts.put(t);
            JSONObject im = new JSONObject();
            im.put("type", "image_url");
            JSONObject iu = new JSONObject();
            iu.put("url", "data:image/png;base64," + m.imageBase64);
            im.put("image_url", iu);
            parts.put(im);
            o.put("content", parts);
        } else {
            o.put("content", m.content);
        }
        return o;
    }

    private static void post(String base, String key, JSONObject body, Callback cb) {
        HttpURLConnection c = null;
        try {
            String baseUrl = base.replaceAll("/+$", "");
            URL url = new URL(baseUrl + "/chat/completions");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (key != null && !key.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + key);
            }
            byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
            c.getOutputStream().write(out);
            int code = c.getResponseCode();
            InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
            String resp = readAll(is);
            if (code >= 400) {
                cb.onError("API error " + code + ": " + truncate(resp, 300));
                return;
            }
            JSONObject o = new JSONObject(resp);
            String text = o.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
            cb.onResult(text == null ? "" : text.trim());
        } catch (Exception e) {
            cb.onError(String.valueOf(e.getMessage()));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
