package com.dragonpal;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chat interface + share target (ACTION_SEND text goes straight to the dragon). */
public class ChatActivity extends Activity {

    private MemoryStore mem;
    private ListView list;
    private EditText input;
    private Button send;
    private TextView header;
    private final List<String> lines = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final List<AiClient.Msg> session = new ArrayList<>();
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_chat);
        mem = new MemoryStore(this);
        list = findViewById(R.id.list);
        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        header = findViewById(R.id.chat_header);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lines);
        list.setAdapter(adapter);
        header.setText(mem.dragonName() + (mem.mood().equals("grumpy") ? " (grumpy)" : ""));

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            hideKeyboard();
            onUserMessage(text);
        });

        // Shared text (e.g. a search query or highlighted passage) -> dragon reacts.
        if (Intent_.isShare(getIntent())) {
            String shared = Intent_.sharedText(getIntent());
            if (shared != null && !shared.isEmpty()) onUserMessage(shared);
        }
    }

    private void onUserMessage(String text) {
        add("You: " + text);
        // Mood engine
        if (DragonPersona.isDragonThreat(text)) {
            mem.setMood("grumpy");
            header.setText(mem.dragonName() + " (grumpy)");
        } else if (DragonPersona.isApology(text) && mem.mood().equals("grumpy")) {
            mem.setMood("happy");
            header.setText(mem.dragonName());
        }
        mem.remember("user", text);

        AiClient.Msg u = new AiClient.Msg();
        u.role = "user";
        u.content = text;
        session.add(u);
        if (session.size() > 20) session.remove(0);

        add(mem.dragonName() + " is thinking...");
        final int thinkingIdx = lines.size() - 1;

        AiClient.chat(mem, new ArrayList<>(session), null, new AiClient.Callback() {
            @Override public void onResult(String t) {
                lines.set(thinkingIdx, mem.dragonName() + ": " + t);
                runOnUiThread(() -> adapter.notifyDataSetChanged());
                speak(t);
                AiClient.Msg a = new AiClient.Msg();
                a.role = "assistant";
                a.content = t;
                session.add(a);
                mem.remember("assistant", t);
            }
            @Override public void onError(String e) {
                lines.set(thinkingIdx, mem.dragonName() + ": (error: " + e + ")");
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
        });
    }

    private void add(String s) {
        lines.add(s);
        adapter.notifyDataSetChanged();
        list.setSelection(lines.size() - 1);
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "dragon");
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    /** Indirection so we never import android.content.Intent twice confusingly. */
    private static final class Intent_ {
        static boolean isShare(android.content.Intent i) {
            return i != null && android.content.Intent.ACTION_SEND.equals(i.getAction());
        }
        static String sharedText(android.content.Intent i) {
            return i == null ? null : i.getStringExtra(android.content.Intent.EXTRA_TEXT);
        }
    }
}
