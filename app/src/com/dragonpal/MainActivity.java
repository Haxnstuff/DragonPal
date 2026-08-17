package com.dragonpal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1;
    private static final int REQ_CAPTURE = 2;

    private static final String[] VISION_PROVIDERS = {
            "Same as chat provider",
            "OpenAI", "OpenRouter", "Groq", "Together AI", "Google Gemini",
            "DeepSeek", "Hugging Face", "Ollama (local)",
            "Custom / manual"
    };

    private MemoryStore mem;
    private EditText name, baseurl, apikey;
    private AutoCompleteTextView visionbaseurl;
    private EditText visionkey;
    private AutoCompleteTextView model, visionmodel;
    private Spinner provider;
    private Spinner visionprovider;
    private CheckBox showKey;
    private CheckBox roaming;
    private TextView status;
    private int currentProvider = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        mem = new MemoryStore(this);

        name = findViewById(R.id.name);
        baseurl = findViewById(R.id.baseurl);
        apikey = findViewById(R.id.apikey);
        visionbaseurl = findViewById(R.id.visionbaseurl);
        visionkey = findViewById(R.id.visionkey);
        visionprovider = findViewById(R.id.visionprovider);
        model = findViewById(R.id.model);
        visionmodel = findViewById(R.id.visionmodel);
        provider = findViewById(R.id.provider);
        showKey = findViewById(R.id.showkey);
        roaming = findViewById(R.id.roaming);
        status = findViewById(R.id.status);

        name.setText(mem.dragonName());
        baseurl.setText(mem.baseUrl());
        model.setText(mem.model());
        visionmodel.setText(mem.visionModel());
        visionbaseurl.setText(mem.visionBaseUrl());
        visionkey.setText(mem.visionApiKey());
        roaming.setChecked(mem.roaming());

        setupModelDropdowns();
        setupVisionProvider();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ModelCatalog.PROVIDERS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        provider.setAdapter(adapter);
        int detected = ModelCatalog.detect(mem.baseUrl());
        mem.migrateLegacyKey(detected);
        mem.setProviderIndex(detected);
        currentProvider = detected;
        apikey.setText(mem.apiKey());
        provider.setSelection(detected);
        setChatModels(detected);
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                onProviderChanged(position);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        showKey.setOnCheckedChangeListener((button, checked) -> {
            int t = checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            apikey.setInputType(t);
            visionkey.setInputType(t);
            apikey.setSelection(apikey.getText().length());
            visionkey.setSelection(visionkey.getText().length());
        });

        findViewById(R.id.btn_test).setOnClickListener(v -> testConnection());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_start).setOnClickListener(v -> startDragon());
        findViewById(R.id.btn_chat).setOnClickListener(v -> openChat());
        findViewById(R.id.btn_access).setOnClickListener(v -> openAccessibility());
        findViewById(R.id.btn_screen).setOnClickListener(v -> requestCapture());
        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            mem.resetMemory();
            Toast.makeText(this, "Memory reset. Dragon forgot everything.", Toast.LENGTH_SHORT).show();
        });

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }
        refreshStatus();
    }

    private void setupModelDropdowns() {
        model.setThreshold(1);
        model.setOnClickListener(v -> model.showDropDown());
        visionmodel.setThreshold(1);
        visionmodel.setOnClickListener(v -> visionmodel.showDropDown());
        visionmodel.setAdapter(new PaidModelAdapter(this, Arrays.asList(ModelCatalog.VISION_MODELS)));
    }

    private void setChatModels(int providerIndex) {
        List<String> models = ModelCatalog.chatModels(providerIndex);
        model.setAdapter(new PaidModelAdapter(this, models));
    }

    private void setupVisionProvider() {
        ArrayAdapter<String> va = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, VISION_PROVIDERS);
        va.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        visionprovider.setAdapter(va);
        visionprovider.setSelection(detectVisionProvider(mem.visionBaseUrl()));
        visionprovider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                applyVisionProvider(position);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        visionbaseurl.setThreshold(1);
        visionbaseurl.setOnClickListener(v -> visionbaseurl.showDropDown());
        visionbaseurl.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, ModelCatalog.BASE_URLS));
    }

    private int detectVisionProvider(String url) {
        if (url == null || url.isEmpty()) return 0;
        int d = ModelCatalog.detect(url);
        return d == 0 ? 9 : d; // unknown -> "Custom / manual"
    }

    private void applyVisionProvider(int pos) {
        if (pos == 0) {
            visionbaseurl.setText("");
            int cp = provider.getSelectedItemPosition();
            if (cp >= 1) visionmodel.setText(ModelCatalog.preset(cp).defaultVision);
        } else if (pos >= 1 && pos <= 8) {
            ModelCatalog.Preset p = ModelCatalog.preset(pos);
            visionbaseurl.setText(p.baseUrl);
            visionmodel.setText(p.defaultVision);
        }
        // pos == 9: custom, leave fields as the user typed them
    }

    /** Dropdown adapter that appends "$" to paid models in the list only (value stays the raw id). */
    private static class PaidModelAdapter extends ArrayAdapter<String> {
        PaidModelAdapter(Context c, List<String> models) {
            super(c, android.R.layout.simple_dropdown_item_1line, models);
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
            String id = getItem(position);
            if (ModelCatalog.isPaid(id)) tv.setText(id + "   $");
            return tv;
        }
    }

    private void onProviderChanged(int position) {
        // Remember the key typed for the previous provider before switching.
        mem.setApiKeyFor(currentProvider, apikey.getText().toString().trim());
        currentProvider = position;
        apikey.setText(mem.apiKeyFor(position));
        applyProvider(position);
    }

    private void applyProvider(int position) {
        if (position == 0) {
            setChatModels(0);
            return; // custom: leave url and models as the user typed them
        }
        ModelCatalog.Preset p = ModelCatalog.preset(position);
        baseurl.setText(p.baseUrl);
        setChatModels(position);
        model.setText(p.defaultChat);
        // Sync the vision model only when no separate vision provider is chosen.
        if (visionprovider.getSelectedItemPosition() == 0) {
            visionbaseurl.setText("");
            visionmodel.setText(p.defaultVision);
        }
    }

    private boolean isLocal() {
        String u = mem.baseUrl();
        return u.contains("localhost") || u.contains("127.0.0.1");
    }

    private void save() {
        int idx = provider.getSelectedItemPosition();
        currentProvider = idx;
        mem.setProviderIndex(idx);
        mem.setDragonName(name.getText().toString().trim());
        mem.setBaseUrl(baseurl.getText().toString().trim());
        mem.setApiKey(apikey.getText().toString().trim());
        mem.setModel(model.getText().toString().trim());
        mem.setVisionModel(visionmodel.getText().toString().trim());
        mem.setVisionBaseUrl(visionbaseurl.getText().toString().trim());
        mem.setVisionApiKey(visionkey.getText().toString().trim());
        mem.setRoaming(roaming.isChecked());
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        save();
        if (mem.apiKey().isEmpty() && !isLocal()) {
            status.setText("Add your API key, then test.");
            return;
        }
        status.setText("Testing connection...");
        AiClient.test(mem, new AiClient.Callback() {
            @Override public void onResult(String t) {
                runOnUiThread(() -> status.setText("Connected. Model: " + mem.model()));
            }
            @Override public void onError(String e) {
                runOnUiThread(() -> status.setText("Connection failed: " + e));
            }
        });
    }

    private void startDragon() {
        save();
        if (mem.apiKey().isEmpty() && !isLocal()) {
            status.setText("Add your API key first, then start the dragon.");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
        } else {
            launchOverlay();
        }
    }

    private void launchOverlay() {
        try {
            Intent i = new Intent(this, DragonOverlayService.class);
            startService(i);
            status.setText("Dragon is roaming. Tap it to chat.");
            Toast.makeText(this, "Dragon released!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            status.setText("Couldn't start dragon: " + e.getMessage());
        }
    }

    private void openChat() {
        startActivity(new Intent(this, ChatActivity.class));
    }

    private void openAccessibility() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        Toast.makeText(this, "Enable \"Dragon Pal\" in Accessibility.", Toast.LENGTH_LONG).show();
    }

    private void requestCapture() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) launchOverlay();
            else status.setText("Overlay permission denied.");
        } else if (req == REQ_CAPTURE && result == RESULT_OK && data != null) {
            Intent i = new Intent(this, ScreenCaptureService.class);
            i.setAction(ScreenCaptureService.ACTION_START);
            i.putExtra("code", result);
            i.putExtra("data", data);
            startForegroundService(i);
            status.setText("Screen viewing enabled. Use the eye button on the dragon.");
        }
    }

    private void refreshStatus() {
        String err = mem.lastError();
        if (!err.isEmpty()) {
            status.setText("Dragon error: " + err);
            return;
        }
        boolean overlay = Settings.canDrawOverlays(this);
        status.setText((overlay ? "Overlay ready" : "Overlay not granted")
                + "  |  " + (mem.apiKey().isEmpty() ? "no API key" : "API key set"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }
}
