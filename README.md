# DragonPal

A little AI dragon that lives on your screen. I'm Haxnstuff, and I built this because I wanted a companion that feels alive instead of a chatbot you have to open an app to talk to. It floats over whatever you're doing, talks out loud, remembers stuff, and gets adorably grumpy if you talk about slaying dragons.

There are three versions:

- **Android**: the floating dragon app. Source in `app/`, ready-to-install build in `DragonPal.apk`.
- **Windows**: the desktop dragon. Source in `windows/`.
- **Linux**: the desktop dragon, packaged for the main distros. Source in `windows/`, package files in `linux/`.

## What it can do

- Roams your screen: floats on top of everything, breathes, and wanders around on its own if you let it.
- Talks: you tap it and it opens a chat, then speaks its replies out loud.
- Sees your screen: it can take a look at what you're doing and comment on it (needs a vision model).
- Reacts to text you highlight or copy: it explains, defines, or rewrites it.
- Has a mood: say something about killing dragons (even in a game or D&D) and it gets all offended and pouty until you apologize.
- Remembers you: rolling memory plus mood, so it feels like a companion.

## Android

### Install

1. Download `DragonPal.apk` from this repo to your phone.
2. Tap it. Android asks you to allow "install unknown apps" the first time.
3. Open Dragon Pal.

### Setup

1. Pick an AI provider: OpenAI, OpenRouter, Groq, Together, Gemini, DeepSeek, Hugging Face, or Ollama (local, free).
2. Paste your API key.
3. (Optional) rename the dragon. Default is Ember.
4. Save, then Test connection.
5. Tap Start Dragon and grant the overlay permission.

### Permissions

- Overlay: so it can float. Asked when you press Start Dragon.
- Notifications: so it can run in the background.
- Accessibility: optional, for the highlight-text reactions. Button: "Enable Accessibility".
- Screen capture: optional, for the "see your screen" thing. Button: "Enable screen viewing", then the eye icon on the dragon.

### Using it

- Tap the dragon to chat.
- Drag it to move it.
- Roam toggle: it wanders on its own.
- Highlight any text: it explains it in a bubble (needs Accessibility).
- Share text: pick Dragon Pal from the share sheet.
- Eye button: it looks at your screen (needs screen viewing and a vision model).
- Reset memory: it forgets everything.

### Build from source

No Gradle. I build it on Android/Termux:

```bash
pkg install aapt2 d8 apksigner openjdk-21 python
```

You also need `android.jar` from Android 34 (SDK cmdline-tools, `sdkmanager "platforms;android-34"`). Put an SDK in `./sdk` or export `ANDROID_JAR=/path/to/android.jar`, then:

```bash
./build.sh
```

## Windows

The Windows version is a Python desktop app. Same dragon, same brain, on your PC.

### Install

You need Python 3.8 or newer. Then:

```bash
cd windows
pip install -r requirements.txt
```

The dependencies are optional. Without them the app still runs, but:

- no `pillow` or `mss`: the dragon can't look at your screen
- no `pyttsx3`: the dragon won't speak out loud

### Run

```bash
cd windows
python run.py
```

A settings window opens. Fill it in and hit Start Dragon, and the little guy appears on your desktop.

### Setup

Same as Android: pick a provider, paste your API key, optionally rename the dragon, then Test connection and Start Dragon.

### Using it

- Click the dragon to open chat, and type a message to talk to it.
- Drag it around your screen.
- Click the little eye icon on the dragon to make it look at your screen (needs `pillow` and a vision model).
- Copy any text (Ctrl+C): the dragon reacts to it, same as highlighting on Android.
- Roam checkbox: it wanders around on its own.
- Reset memory: it forgets everything.

Minimize the settings window instead of closing it. Closing it quits the app, dragon and all.

To sanity-check the core logic on any machine:

```bash
cd windows
python tests/test_logic.py
```

### Standalone exe

You don't need Python to get the exe. Every push to master builds one on GitHub's Windows runners. Grab it from the latest Actions run (Actions tab, pick the run, then Artifacts, then DragonPal-windows).

Or build it yourself on a Windows machine:

```bat
windows\build_exe.bat
```

That drops `windows\dist\DragonPal.exe`.

## Linux

The Linux version is the same Python app, packaged for the main distros. Every push to master builds them on GitHub Actions and attaches them to the latest release.

Packages:

- Debian / Ubuntu: `dragonpal_1.0.0_all.deb`
- Fedora / RHEL: `dragonpal-1.0.0-1.noarch.rpm`
- Arch: `dragonpal-1.0.0-1-any.pkg.tar.zst` (plus a PKGBUILD in `linux/`)
- Universal: `DragonPal-linux-x86_64.tar.gz` (a single binary, no Python needed)

### Install

Debian / Ubuntu:

```bash
sudo apt install ./dragonpal_1.0.0_all.deb
```

Fedora:

```bash
sudo dnf install ./dragonpal-1.0.0-1.noarch.rpm
```

Arch:

```bash
sudo pacman -U ./dragonpal-1.0.0-1-any.pkg.tar.zst
```

Universal binary: untar it and run `./DragonPal`. It needs Tk 8.6 and X11, which ship with any desktop distro.

### Run

After installing any of the packages, launch it from your app menu (it's called Dragon Pal), or run `dragonpal` in a terminal. Setup is the same as Windows: pick a provider, paste your API key, hit Start Dragon.

### Linux notes

- The packages need Python 3 and Tkinter, which they declare as dependencies.
- Screen viewing needs `pillow` and `mss` (`pip install pillow mss`) and an X11 session. On Wayland, desktop screen capture is usually blocked by the compositor, so the eye button may not work there.
- Speech needs `pyttsx3` (`pip install pyttsx3`) plus `espeak` on your system.
- The shared Python source lives in `windows/dragonpal/` and is reused by every build.

## Notes

- Your API key never leaves your machine. It's stored locally, and the app talks straight to whatever provider you picked.
- For a totally free setup, run Ollama locally and pick "Ollama (local)".
- DeepSeek's hosted API doesn't take images, so screen viewing needs a vision model from OpenAI, Gemini, Groq, or similar instead.
