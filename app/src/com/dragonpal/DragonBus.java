package com.dragonpal;

/** Tiny static event bus: overlay service registers; capture/accessibility services post to it. */
public class DragonBus {
    public interface Listener {
        void onMessage(String text);   // show bubble + speak
    }

    private static volatile Listener listener;

    public static void set(Listener l) { listener = l; }

    public static void post(String text) {
        Listener l = listener;
        if (l != null) l.onMessage(text);
    }
}
