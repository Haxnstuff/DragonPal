package com.dragonpal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Holds a MediaProjection and, on demand, snapshots the screen for the dragon to "see". */
public class ScreenCaptureService extends Service {

    public static final String ACTION_START = "com.dragonpal.START";
    public static final String ACTION_CAPTURE = "com.dragonpal.CAPTURE";
    public static final String ACTION_STOP = "com.dragonpal.STOP";

    public static volatile boolean hasProjection = false;

    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int width, height, dpi;
    private Handler h;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(2, Notif.build(this, "Screen viewing on"));
        HandlerThread ht = new HandlerThread("capture");
        ht.start();
        h = new Handler(ht.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String a = intent.getAction();
        if (ACTION_START.equals(a)) {
            startForeground(2, Notif.build(this, "Screen viewing on"));
            int code = intent.getIntExtra("code", 0);
            Intent data = intent.getParcelableExtra("data");
            setup(code, data);
        } else if (ACTION_CAPTURE.equals(a) && hasProjection) {
            h.post(this::captureAndAnalyze);
        } else if (ACTION_STOP.equals(a)) {
            teardown();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void setup(int code, Intent data) {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
            width = dm.widthPixels;
            height = dm.heightPixels;
            dpi = dm.densityDpi;
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            // Android 14+ requires a callback registered before creating the virtual display.
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    teardown();
                    stopSelf();
                }
            }, h);
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            display = projection.createVirtualDisplay("dragon-eye", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
            hasProjection = true;
        } catch (Exception e) {
            DragonBus.post("Couldn't start screen view: " + e.getMessage());
            teardown();
        }
    }

    private void captureAndAnalyze() {
        try {
            MemoryStore m = new MemoryStore(this);
            if (m.effectiveVisionBaseUrl().contains("deepseek")) {
                DragonBus.post("DeepSeek's hosted API doesn't accept images, so screen viewing can't run on it. Switch to a vision provider like OpenAI, Gemini, or Groq in settings.");
                return;
            }
            if (m.visionModel().isEmpty()) {
                DragonBus.post("No vision model is set, so I can't see the screen. Pick a vision model in settings.");
                return;
            }
            Bitmap bmp = grabFrame();
            if (bmp == null) { DragonBus.post("Couldn't grab the screen."); return; }
            int maxW = 1280;
            if (bmp.getWidth() > maxW) {
                int nh = (int) (bmp.getHeight() * ((float) maxW / bmp.getWidth()));
                bmp = Bitmap.createScaledBitmap(bmp, maxW, nh, true);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 70, bos);
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            if (bmp != null) bmp.recycle();

            String prompt = "You are looking at the user's screen right now. In your dragon voice, "
                    + "react to what's on screen: briefly say what you see and give ONE short, helpful "
                    + "or fun suggestion or comment. 1-2 sentences max.";

            AiClient.vision(m, b64, prompt, new AiClient.Callback() {
                @Override public void onResult(String t) {
                    DragonBus.post(t);
                }
                @Override public void onError(String e) {
                    DragonBus.post("Error: " + e);
                }
            });
        } catch (Exception e) {
            DragonBus.post("Screen view error: " + e.getMessage());
        }
    }

    private Bitmap grabFrame() {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return null;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            int bw = width + rowPadding / pixelStride;
            Bitmap full = Bitmap.createBitmap(bw, height, Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(buf);
            Bitmap cropped = Bitmap.createBitmap(full, 0, 0, width, height);
            if (full != cropped) full.recycle();
            return cropped;
        } finally {
            if (image != null) image.close();
        }
    }

    private void teardown() {
        hasProjection = false;
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        display = null; reader = null; projection = null;
    }

    @Override
    public void onDestroy() {
        teardown();
        if (h != null) h.getLooper().quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
