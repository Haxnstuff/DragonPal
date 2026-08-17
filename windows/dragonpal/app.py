"""DragonPal for Windows.

A floating desktop dragon you can drag around, tap to chat with, copy text to,
and (optionally) let look at your screen. Pure tkinter + stdlib, with two
optional extras: Pillow (screen viewing) and pyttsx3 (speech).

Run with:  python -m dragonpal.app   (from the windows/ directory)
"""

import io
import math
import random
import threading
import tkinter as tk
from tkinter import ttk

import dragonpal.ai_client as ai
import dragonpal.catalog as catalog
import dragonpal.memory as memory
import dragonpal.persona as persona

try:
    import pyttsx3
except Exception:
    pyttsx3 = None


MAGENTA = "#ff00ff"
GREEN = "#3fb950"
GREEN_DARK = "#1f7a33"
WING = "#8be09b"
BUBBLE_BG = "#eafaf1"


def _grab_screen():
    """Return a PIL Image of the whole screen, or None if nothing works.

    Tries mss first (cross-platform), then Pillow ImageGrab (Windows/macOS),
    then a Linux subprocess fallback (ImageMagick import or scrot).
    """
    try:
        import mss
        with mss.mss() as sct:
            monitor = sct.monitors[0]
            shot = sct.grab(monitor)
            from PIL import Image
            return Image.frombytes("RGB", shot.size, shot.rgb)
    except Exception:
        pass
    try:
        from PIL import ImageGrab
        img = ImageGrab.grab()
        if img is not None:
            return img
    except Exception:
        pass
    try:
        import os
        import subprocess
        import tempfile
        from PIL import Image
        path = tempfile.mktemp(suffix=".png")
        for cmd in (["import", "-window", "root", path], ["scrot", path]):
            r = subprocess.run(cmd, capture_output=True, timeout=10)
            if r.returncode == 0 and os.path.exists(path):
                img = Image.open(path)
                img.load()
                os.remove(path)
                return img
        if os.path.exists(path):
            os.remove(path)
    except Exception:
        pass
    return None


class DragonPal:
    """Owns the tk root, settings window, the floating dragon, and chat."""

    def __init__(self):
        self.mem = memory.MemoryStore()
        self.root = tk.Tk()
        self.root.title("Dragon Pal")
        self.root.geometry("560x560")
        self.root.resizable(False, False)

        self.overlay = None
        self.chat_win = None
        self.tts = None
        self._tts_ready = threading.Event()
        self._last_clipboard = self._read_clipboard()

        self._build_settings()
        self._init_tts_async()
        self.root.after(1500, self._watch_clipboard)

    # ---------- TTS ----------

    def _init_tts_async(self):
        def work():
            if pyttsx3 is None:
                return
            try:
                engine = pyttsx3.init()
                engine.setProperty("rate", 170)
                self.tts = engine
                self._tts_ready.set()
            except Exception:
                self.tts = None

        threading.Thread(target=work, daemon=True).start()

    def speak(self, text):
        if pyttsx3 is None:
            return
        if not self._tts_ready.is_set() or self.tts is None:
            return
        engine = self.tts

        def work():
            try:
                engine.say(text)
                engine.runAndWait()
            except Exception:
                pass

        threading.Thread(target=work, daemon=True).start()

    # ---------- settings window ----------

    def _row(self, parent, label):
        ttk.Label(parent, text=label, width=16, anchor="e").grid(row=parent._r, column=0, sticky="e", padx=(6, 4), pady=3)
        entry = ttk.Entry(parent)
        entry.grid(row=parent._r, column=1, sticky="we", padx=(0, 6), pady=3)
        parent.columnconfigure(1, weight=1)
        parent._r += 1
        return entry

    def _combo_row(self, parent, label):
        ttk.Label(parent, text=label, width=16, anchor="e").grid(row=parent._r, column=0, sticky="e", padx=(6, 4), pady=3)
        cb = ttk.Combobox(parent)
        cb.grid(row=parent._r, column=1, sticky="we", padx=(0, 6), pady=3)
        parent.columnconfigure(1, weight=1)
        parent._r += 1
        return cb

    def _build_settings(self):
        f = ttk.Frame(self.root, padding=8)
        f.pack(fill="both", expand=True)
        f._r = 0
        f.columnconfigure(1, weight=1)

        ttk.Label(f, text="Provider").grid(row=f._r, column=0, sticky="e", padx=(6, 4), pady=3)
        self.provider = ttk.Combobox(f, values=catalog.PROVIDERS, state="readonly")
        self.provider.grid(row=f._r, column=1, sticky="we", padx=(0, 6), pady=3)
        f._r += 1

        self.name = self._row(f, "Dragon name")
        self.baseurl = self._row(f, "Base URL")
        self.apikey = self._row(f, "API key")
        self.apikey.configure(show="*")
        self.model = self._combo_row(f, "Model")
        self.visionmodel = self._combo_row(f, "Vision model")
        self.visionmodel.configure(values=catalog.VISION_MODELS)
        self.visionbaseurl = self._row(f, "Vision URL")
        self.visionkey = self._row(f, "Vision key")
        self.visionkey.configure(show="*")

        self.roaming = tk.BooleanVar()
        ttk.Checkbutton(f, text="Roam on its own", variable=self.roaming).grid(row=f._r, column=1, sticky="w", pady=3)
        f._r += 1

        # load current state
        self.name.insert(0, self.mem.dragon_name())
        self.baseurl.insert(0, self.mem.base_url())
        self.model.set(self.mem.model())
        self.visionmodel.set(self.mem.vision_model())
        self.visionbaseurl.insert(0, self.mem.vision_base_url())
        self.visionkey.insert(0, self.mem.vision_api_key())
        self.roaming.set(self.mem.roaming())

        detected = catalog.detect(self.mem.base_url())
        self.mem.migrate_legacy_key(detected)
        self.mem.set_provider_index(detected)
        self.provider.set(detected == 0 and catalog.PROVIDERS[0] or catalog.PROVIDERS[detected])
        self.provider.bind("<<ComboboxSelected>>", self._on_provider_changed)
        self.apikey.insert(0, self.mem.api_key())
        self._set_chat_models(detected)

        btns = ttk.Frame(f)
        btns.grid(row=f._r, column=0, columnspan=2, sticky="we", pady=(10, 0))
        f._r += 1
        ttk.Button(btns, text="Test connection", command=self.test_connection).pack(side="left", padx=3)
        ttk.Button(btns, text="Save", command=self.save).pack(side="left", padx=3)
        ttk.Button(btns, text="Start dragon", command=self.start_dragon).pack(side="left", padx=3)
        ttk.Button(btns, text="Open chat", command=self.open_chat).pack(side="left", padx=3)
        ttk.Button(btns, text="Reset memory", command=self.reset_memory).pack(side="left", padx=3)

        self.status = ttk.Label(f, text="", foreground="#555")
        self.status.grid(row=f._r, column=0, columnspan=2, sticky="w", pady=(8, 0))
        f._r += 1
        self.refresh_status()

    def _set_chat_models(self, provider_index):
        models = catalog.chat_models(provider_index)
        if hasattr(self, "model"):
            self.model.configure(values=models)

    def _on_provider_changed(self, event=None):
        self.save_key_for_current()
        pos = self.provider.current()
        self._set_chat_models(pos)
        if pos == 0:
            return
        p = catalog.preset(pos)
        self.baseurl.delete(0, "end")
        self.baseurl.insert(0, p.base_url)
        self.model.set(p.default_chat)
        self.visionmodel.set(p.default_vision)
        self.apikey.delete(0, "end")
        self.apikey.insert(0, self.mem.api_key_for(pos))

    def save_key_for_current(self):
        pos = self.mem.provider_index()
        self.mem.set_api_key_for(pos, self.apikey.get().strip())

    def save(self):
        pos = self.provider.current()
        self.mem.set_provider_index(pos)
        self.mem.set_api_key_for(pos, self.apikey.get().strip())
        self.mem.set_dragon_name(self.name.get().strip() or "Ember")
        self.mem.set_base_url(self.baseurl.get().strip())
        self.mem.set_model(self.model.get().strip())
        self.mem.set_vision_model(self.visionmodel.get().strip())
        self.mem.set_vision_base_url(self.visionbaseurl.get().strip())
        self.mem.set_vision_api_key(self.visionkey.get().strip())
        self.mem.set_roaming(self.roaming.get())
        self.status.configure(text="Saved")

    def test_connection(self):
        self.save()
        if not self.mem.api_key() and not self._is_local():
            self.status.configure(text="Add your API key, then test.")
            return
        self.status.configure(text="Testing connection...")

        def work():
            try:
                ai.test(self.mem)
                self.root.after(0, lambda: self.status.configure(text="Connected. Model: " + self.mem.model()))
            except Exception as e:
                self.root.after(0, lambda: self.status.configure(text="Connection failed: " + str(e)))

        threading.Thread(target=work, daemon=True).start()

    def _is_local(self):
        u = self.mem.base_url()
        return "localhost" in u or "127.0.0.1" in u

    def start_dragon(self):
        self.save()
        if not self.mem.api_key() and not self._is_local():
            self.status.configure(text="Add your API key first, then start the dragon.")
            return
        if self.overlay is not None and self.overlay.alive():
            self.status.configure(text="Dragon is already roaming.")
            return
        self.overlay = DragonOverlay(self)
        self.status.configure(text="Dragon is roaming. Click it to chat, right-click the eye to make it look.")

    def open_chat(self):
        if self.chat_win is None or not self.chat_win.alive():
            self.chat_win = ChatWindow(self)
        self.chat_win.win.deiconify()
        self.chat_win.win.lift()

    def reset_memory(self):
        self.mem.reset_memory()
        self.status.configure(text="Memory reset. Dragon forgot everything.")

    def refresh_status(self):
        self.status.configure(text="API key set" if self.mem.api_key() else "no API key")

    # ---------- vision ----------

    def look_at_screen(self):
        base = self.mem.effective_vision_base_url()
        if "deepseek" in base:
            self.post_to_dragon("DeepSeek's hosted API doesn't accept images. Switch to a vision provider like OpenAI, Gemini, or Groq.")
            return
        if not self.mem.vision_model():
            self.post_to_dragon("No vision model is set, so I can't see the screen.")
            return
        self.post_to_dragon("Let me take a look...")
        threading.Thread(target=self._do_vision, daemon=True).start()

    def _do_vision(self):
        try:
            img = _grab_screen()
            if img is None:
                self.post_to_dragon("Couldn't grab the screen. Screen viewing needs pillow and mss (pip install pillow mss), and an X11 session on Linux.")
                return
            max_w = 1280
            if img.width > max_w:
                img = img.resize((max_w, int(img.height * max_w / img.width)))
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            prompt = ("You are looking at the user's screen right now. In your dragon voice, "
                      "react to what's on screen: briefly say what you see and give ONE short, "
                      "helpful or fun suggestion or comment. 1-2 sentences max.")
            text = ai.vision(self.mem, buf.getvalue(), prompt)
            self.post_to_dragon(text)
        except Exception as e:
            self.post_to_dragon("Screen view error: " + str(e))

    # ---------- clipboard reactions ----------

    def _read_clipboard(self):
        try:
            return self.root.clipboard_get()
        except Exception:
            return ""

    def _watch_clipboard(self):
        try:
            txt = self._read_clipboard()
            if txt and txt != self._last_clipboard:
                self._last_clipboard = txt
                if 2 <= len(txt) <= 500:
                    self.react_to_text(txt)
        except Exception:
            pass
        self.root.after(1500, self._watch_clipboard)

    def react_to_text(self, text):
        if persona.is_dragon_threat(text):
            self.mem.set_mood("grumpy")
            self.post_to_dragon(random.choice(persona.GRUMPY_QUIPS))
        prompt = ('The user selected or copied this text: "%s". As the dragon, react helpfully: '
                  'give a SHORT explanation, definition, or a rewrite suggestion (pick whichever '
                  'fits). 1-2 sentences. Keep your personality.' % text)

        def work():
            try:
                out = ai.chat(self.mem, [ai.Msg("user", prompt)])
                self.mem.remember("assistant", "re: " + text + " -> " + out)
                self.post_to_dragon(out)
            except Exception as e:
                self.post_to_dragon("Error: " + str(e))

        threading.Thread(target=work, daemon=True).start()

    def post_to_dragon(self, text):
        """Route a message to the floating dragon (or just speak if it is gone)."""
        def show():
            if self.overlay is not None and self.overlay.alive():
                self.overlay.show_bubble(text)
            if self.chat_win is not None and self.chat_win.alive():
                self.chat_win.append(self.mem.dragon_name() + ": " + text)
        self.root.after(0, show)
        self.speak(text)

    def run(self):
        self.root.mainloop()


class DragonOverlay:
    """The floating, draggable, breathing dragon window."""

    def __init__(self, app):
        self.app = app
        self.mem = app.mem
        self.win = tk.Toplevel(app.root)
        self.win.overrideredirect(True)
        self.win.attributes("-topmost", True)
        self.win.attributes("-transparentcolor", MAGENTA)
        self.win.geometry("+%d+%d" % (60, 120))

        self.canvas = tk.Canvas(self.win, width=210, height=210, bg=MAGENTA, highlightthickness=0)
        self.canvas.pack()

        self._phase = 0.0
        self._dragging = False
        self._moved = False
        self._down = (0, 0)
        self._start = (0, 0)
        self._on_eye = False
        self._bubble_after = None

        self.canvas.bind("<ButtonPress-1>", self._on_press)
        self.canvas.bind("<B1-Motion>", self._on_move)
        self.canvas.bind("<ButtonRelease-1>", self._on_release)

        self._draw()
        self._breathe()
        if self.mem.roaming():
            self._wander()

    def alive(self):
        try:
            return self.win.winfo_exists()
        except Exception:
            return False

    def _draw(self):
        c = self.canvas
        c.delete("all")
        r = 34 + 3 * math.sin(self._phase)
        cx, cy = 105, 115
        # tail
        c.create_polygon(cx - r, cy + 4, cx - r - 22, cy + 16, cx - r - 6, cy - 8,
                         fill=GREEN, outline=GREEN_DARK, tags=("dragon",))
        # wings
        c.create_oval(cx - r - 16, cy - 34, cx - r + 8, cy - 2, fill=WING, outline=GREEN_DARK, tags=("dragon",))
        c.create_oval(cx + r - 8, cy - 34, cx + r + 16, cy - 2, fill=WING, outline=GREEN_DARK, tags=("dragon",))
        # body
        c.create_oval(cx - r, cy - r, cx + r, cy + r, fill=GREEN, outline=GREEN_DARK, width=2, tags=("dragon",))
        # eyes
        c.create_oval(cx - 16, cy - 18, cx - 2, cy - 4, fill="white", outline=GREEN_DARK, tags=("dragon",))
        c.create_oval(cx + 2, cy - 18, cx + 16, cy - 4, fill="white", outline=GREEN_DARK, tags=("dragon",))
        c.create_oval(cx - 11, cy - 13, cx - 6, cy - 8, fill="black", tags=("dragon",))
        c.create_oval(cx + 6, cy - 13, cx + 11, cy - 8, fill="black", tags=("dragon",))
        # smile
        c.create_arc(cx - 10, cy + 2, cx + 10, cy + 16, start=200, extent=140,
                     style="arc", outline=GREEN_DARK, width=2, tags=("dragon",))
        # eye button (top-right): a little eye icon you click to make it look
        ex, ey = cx + r - 4, cy - r + 6
        c.create_oval(ex - 10, ey - 10, ex + 10, ey + 10, fill="white", outline=GREEN_DARK, width=2, tags=("eye",))
        c.create_oval(ex - 4, ey - 4, ex + 4, ey + 4, fill="black", tags=("eye",))

    def _breathe(self):
        if not self.alive():
            return
        self._phase += 0.15
        self._draw()
        self.win.after(70, self._breathe)

    def _wander(self):
        if not self.alive():
            return
        if not self._dragging:
            sw = self.win.winfo_screenwidth()
            sh = self.win.winfo_screenheight()
            tx = random.randint(0, max(1, sw - 210))
            ty = random.randint(0, max(1, sh - 210))
            self._slide_to(tx, ty)
        self.win.after(3500, self._wander)

    def _slide_to(self, tx, ty):
        x = self.win.winfo_x()
        y = self.win.winfo_y()
        steps = 12
        dx = (tx - x) / steps
        dy = (ty - y) / steps

        def step(i):
            if not self.alive() or self._dragging:
                return
            if i >= steps:
                return
            self.win.geometry("+%d+%d" % (int(x + dx * (i + 1)), int(y + dy * (i + 1))))
            self.win.after(40, lambda: step(i + 1))

        step(0)

    def _on_press(self, e):
        self._dragging = True
        self._moved = False
        self._down = (e.x_root, e.y_root)
        self._start = (self.win.winfo_x(), self.win.winfo_y())
        cur = self.canvas.find_withtag("current")
        self._on_eye = bool(cur) and "eye" in self.canvas.gettags(cur[0])

    def _on_move(self, e):
        dx = e.x_root - self._down[0]
        dy = e.y_root - self._down[1]
        if abs(dx) > 6 or abs(dy) > 6:
            self._moved = True
        self.win.geometry("+%d+%d" % (self._start[0] + dx, self._start[1] + dy))

    def _on_release(self, e):
        self._dragging = False
        if self._moved:
            return
        if self._on_eye:
            self.app.look_at_screen()
        else:
            self.app.open_chat()

    def show_bubble(self, text):
        if not self.alive():
            return
        c = self.canvas
        # clear any previous bubble (everything above the dragon line)
        c.delete("bubble")
        if not text:
            return
        lines = self._wrap(text, 30)
        w = max(len(ln) for ln in lines) * 7 + 24
        h = len(lines) * 18 + 18
        x0, y0 = 10, 10
        c.create_rectangle(x0, y0, x0 + w, y0 + h, fill=BUBBLE_BG, outline=GREEN_DARK, tags=("bubble",))
        c.create_text(x0 + 12, y0 + 10, text="\n".join(lines), anchor="nw",
                      fill="#1a1a2e", font=("TkDefaultFont", 10), width=w - 24, tags=("bubble",))
        if self._bubble_after is not None:
            self.win.after_cancel(self._bubble_after)
        self._bubble_after = self.win.after(10000, lambda: c.delete("bubble"))

    @staticmethod
    def _wrap(text, width):
        words = text.split()
        lines, cur = [], ""
        for w in words:
            if len(cur) + len(w) + 1 > width:
                lines.append(cur)
                cur = w
            else:
                cur = (cur + " " + w).strip()
        if cur:
            lines.append(cur)
        return lines or [text]


class ChatWindow:
    """A chat dialog. Text copied to the clipboard also lands here via reactions."""

    def __init__(self, app):
        self.app = app
        self.mem = app.mem
        self.session = []
        self.win = tk.Toplevel(app.root)
        self.win.title("Dragon chat")
        self.win.geometry("460x520")

        self.header = ttk.Label(self.win, text="")
        self.header.pack(fill="x", padx=6, pady=(6, 0))
        self._update_header()

        self.log = tk.Text(self.win, state="disabled", wrap="word", height=20)
        self.log.pack(fill="both", expand=True, padx=6, pady=6)

        bar = ttk.Frame(self.win)
        bar.pack(fill="x", padx=6, pady=(0, 6))
        self.entry = ttk.Entry(bar)
        self.entry.pack(side="left", fill="x", expand=True)
        ttk.Button(bar, text="Send", command=self._on_send).pack(side="left", padx=(4, 0))
        self.entry.bind("<Return>", lambda e: self._on_send())

        self.lines = []
        self.add("Say hi to %s!" % self.mem.dragon_name())

    def alive(self):
        try:
            return self.win.winfo_exists()
        except Exception:
            return False

    def _update_header(self):
        self.header.configure(text=self.mem.dragon_name() + (" (grumpy)" if self.mem.mood() == "grumpy" else ""))

    def add(self, text):
        self.lines.append(text)
        self._render()

    def set_last(self, text):
        if self.lines:
            self.lines[-1] = text
        else:
            self.lines.append(text)
        self._render()

    def append(self, text):
        self.add(text)

    def _render(self):
        self.log.configure(state="normal")
        self.log.delete("1.0", "end")
        if self.lines:
            self.log.insert("1.0", "\n".join(self.lines) + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def _on_send(self):
        text = self.entry.get().strip()
        if not text:
            return
        self.entry.delete(0, "end")
        self.add("You: " + text)

        if persona.is_dragon_threat(text):
            self.mem.set_mood("grumpy")
        elif persona.is_apology(text) and self.mem.mood() == "grumpy":
            self.mem.set_mood("happy")
        self._update_header()
        self.mem.remember("user", text)

        self.session.append(ai.Msg("user", text))
        if len(self.session) > 20:
            self.session = self.session[-20:]

        self.add(self.mem.dragon_name() + " is thinking...")
        thinking_idx = len(self.lines) - 1

        def work():
            try:
                out = ai.chat(self.mem, list(self.session))
                self.mem.remember("assistant", out)
                self.session.append(ai.Msg("assistant", out))

                def show():
                    self.lines[thinking_idx] = self.mem.dragon_name() + ": " + out
                    self._render()
                self.app.root.after(0, show)
                self.app.speak(out)
            except Exception as e:
                def show_err():
                    self.set_last(self.mem.dragon_name() + ": (error: " + str(e) + ")")
                self.app.root.after(0, show_err)

        threading.Thread(target=work, daemon=True).start()


def main():
    DragonPal().run()


if __name__ == "__main__":
    main()
