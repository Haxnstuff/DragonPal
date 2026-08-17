package com.dragonpal;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/** The floating dragon: roams, drags, breathes, shows speech bubbles, and speaks. */
public class DragonOverlayService extends Service implements DragonBus.Listener {

    private WindowManager wm;
    private LinearLayout container;
    private ImageView dragon;
    private TextView bubble;
    private ImageView look;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler();
    private final Random rnd = new Random();
    private TextToSpeech tts;
    private boolean dragging = false;
    private int downX, downY, startX, startY;
    private boolean moved = false;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DragonBus.set(this);
        // Foreground promotion is optional: if the platform rejects the service type,
        // we still show the overlay as a normal service rather than crashing the app.
        try {
            startInForeground();
        } catch (Exception e) {
            record(e);
        }
        try {
            initTts();
            buildOverlay();
            breathe();
            if (new MemoryStore(this).roaming()) scheduleWander();
            new MemoryStore(this).clearLastError();
        } catch (Exception e) {
            record(e);
            stopSelf();
        }
    }

    private void startInForeground() {
        Notification n = Notif.build(this, "Your dragon is roaming");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void record(Exception e) {
        android.util.Log.e("DragonPal", "overlay error", e);
        new MemoryStore(this).setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private void initTts() {
        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void buildOverlay() {
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        bubble = new TextView(this);
        bubble.setTextSize(15);
        bubble.setTextColor(0xFF1a1a2e);
        bubble.setBackground(rounded(0xFFeafaf1, dp(16)));
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setMaxWidth(dp(280));
        bubble.setVisibility(View.GONE);
        bubble.setClickable(true);
        bubble.setOnClickListener(v -> hideBubble());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 0, 0, dp(6));
        container.addView(bubble, bp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        look = new ImageView(this);
        look.setImageResource(R.drawable.ic_eye);
        look.setPadding(0, 0, dp(4), 0);
        look.setOnClickListener(v -> requestLook());
        row.addView(look, new LinearLayout.LayoutParams(dp(26), dp(26)));

        dragon = new ImageView(this);
        dragon.setImageResource(R.drawable.ic_dragon);
        dragon.setOnTouchListener(this::onTouch);
        row.addView(dragon, new LinearLayout.LayoutParams(dp(80), dp(80)));

        container.addView(row);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dp(200);

        wm.addView(container, params);
    }

    private boolean onTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                moved = false;
                downX = (int) e.getRawX();
                downY = (int) e.getRawY();
                startX = params.x;
                startY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) e.getRawX() - downX;
                int dy = (int) e.getRawY() - downY;
                if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                params.x = startX + dx;
                params.y = startY + dy;
                wm.updateViewLayout(container, params);
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                if (!moved) {
                    // tap = talk
                    hideBubble();
                    Intent i = new Intent(this, ChatActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
                return true;
        }
        return false;
    }

    private void breathe() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(dragon, "scaleX", 1f, 1.07f, 1f);
        sx.setDuration(1500);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sx.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator sy = ObjectAnimator.ofFloat(dragon, "scaleY", 1f, 1.07f, 1f);
        sy.setDuration(1500);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatMode(ValueAnimator.REVERSE);
        sx.start();
        sy.start();
    }

    private void scheduleWander() {
        handler.postDelayed(() -> {
            wander();
            scheduleWander();
        }, 3500);
    }

    private void wander() {
        if (dragging) return;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int w = dragon.getWidth() > 0 ? dragon.getWidth() : dp(70);
        int h = dragon.getHeight() > 0 ? dragon.getHeight() : dp(70);
        int maxX = Math.max(1, dm.widthPixels - w);
        int maxY = Math.max(1, dm.heightPixels - h);
        int tx = rnd.nextInt(maxX + 1);
        int ty = rnd.nextInt(maxY + 1);
        final int sx = params.x, sy = params.y;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(1700);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            params.x = (int) (sx + (tx - sx) * f);
            params.y = (int) (sy + (ty - sy) * f);
            try { wm.updateViewLayout(container, params); } catch (Exception ignored) {}
        });
        va.start();
    }

    private void requestLook() {
        if (ScreenCaptureService.hasProjection) {
            Intent i = new Intent(this, ScreenCaptureService.class);
            i.setAction(ScreenCaptureService.ACTION_CAPTURE);
            startService(i);
            showBubble("Let me take a look...");
        } else {
            showBubble("Enable \"screen viewing\" in the Dragon Pal app first!");
        }
    }

    @Override
    public void onMessage(String text) {
        // DragonBus posts arrive from background threads (AI callbacks); UI must run on main.
        handler.post(() -> {
            showBubble(text);
            speak(text);
        });
    }

    private void showBubble(String text) {
        bubble.setText(text);
        bubble.setVisibility(View.VISIBLE);
        bubble.setAlpha(0f);
        bubble.animate().alpha(1f).setDuration(220).start();
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, 10000);
    }

    private final Runnable hideRunnable = this::hideBubble;

    private void hideBubble() {
        bubble.animate().alpha(0f).setDuration(200).withEndAction(() -> bubble.setVisibility(View.GONE)).start();
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "dragon");
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        DragonBus.set(null);
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (container != null) {
            try { wm.removeView(container); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
