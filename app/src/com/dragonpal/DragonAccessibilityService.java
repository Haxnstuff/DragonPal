package com.dragonpal;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Sees text the user selects anywhere, and shows the dragon's definition/suggestion. */
public class DragonAccessibilityService extends AccessibilityService {

    private final Handler handler = new Handler();
    private TextView bubbleView;
    private WindowManager bubbleWm;
    private long lastReactTime = 0;
    private String lastReactText = "";
    private String pendingText = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            String text = selectedText(e);
            if (text != null && text.length() >= 2 && text.length() <= 500) {
                doReact(text);
            }
        } else if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            String text = typedText(e);
            if (text != null && text.length() >= 4 && text.length() <= 500) {
                pendingText = text;
                handler.removeCallbacks(typedReactionRunnable);
                handler.postDelayed(typedReactionRunnable, 2500);
            }
        }
    }

    private final Runnable typedReactionRunnable = () -> {
        String t = pendingText;
        pendingText = null;
        if (t != null) doReact(t);
    };

    private void doReact(String text) {
        long now = System.currentTimeMillis();
        if (text.equals(lastReactText) && now - lastReactTime < 15000) return;
        if (now - lastReactTime < 8000) return; // cooldown so the dragon doesn't spam
        lastReactText = text;
        lastReactTime = now;

        MemoryStore mem = new MemoryStore(this);
        final String selected = text;

        // Instant grumpy reaction if they mention dragon-murder content.
        if (DragonPersona.isDragonThreat(text)) {
            mem.setMood("grumpy");
            String quip = DragonPersona.GRUMPY_QUIPS[(int) (Math.random() * DragonPersona.GRUMPY_QUIPS.length)];
            DragonBus.post(quip);
        }

        List<AiClient.Msg> msgs = new ArrayList<>();
        AiClient.Msg m = new AiClient.Msg();
        m.role = "user";
        m.content = "The user selected or typed this text: \"" + selected + "\". "
                + "As the dragon, react helpfully: give a SHORT explanation, definition, or a rewrite "
                + "suggestion (pick whichever fits). 1-2 sentences. Keep your personality.";
        msgs.add(m);

        AiClient.chat(mem, msgs, null, new AiClient.Callback() {
            @Override public void onResult(String t) {
                mem.remember("assistant", "re: " + selected + " -> " + t);
                handler.post(() -> showBubble(t));
            }
            @Override public void onError(String err) {
                handler.post(() -> showBubble("Error: " + err));
            }
        });
    }

    private String typedText(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        if (src == null) return null;
        try {
            CharSequence t = src.getText();
            return t == null ? null : t.toString();
        } finally {
            src.recycle();
        }
    }

    private String selectedText(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        if (src == null) return null;
        try {
            int s = src.getTextSelectionStart();
            int en = src.getTextSelectionEnd();
            CharSequence full = src.getText();
            if (full != null && s >= 0 && en > s && en <= full.length()) {
                return full.subSequence(s, en).toString();
            }
        } finally {
            src.recycle();
        }
        return null;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void showBubble(String text) {
        removeBubble();
        bubbleWm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubbleView = new TextView(this);
        bubbleView.setText(text);
        bubbleView.setTextSize(15);
        bubbleView.setTextColor(0xFF1a1a2e);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFeafaf1);
        d.setCornerRadius(dp(16));
        bubbleView.setBackground(d);
        bubbleView.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubbleView.setMaxWidth(dp(300));
        bubbleView.setClickable(true);
        bubbleView.setOnClickListener(v -> removeBubble());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(130);
        bubbleWm.addView(bubbleView, lp);
        handler.postDelayed(this::removeBubble, 9000);
    }

    private void removeBubble() {
        if (bubbleView != null && bubbleWm != null) {
            try { bubbleWm.removeView(bubbleView); } catch (Exception ignored) {}
        }
        bubbleView = null;
        bubbleWm = null;
    }

    @Override
    public void onInterrupt() { removeBubble(); }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeBubble();
        super.onDestroy();
    }
}
