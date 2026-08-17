# DragonPal

Hey, I'm Haxnstuff. DragonPal is a little AI dragon that lives on my phone screen and hangs out with me. It floats over whatever I'm doing, talks out loud, remembers stuff, and gets adorably grumpy if I talk about slaying dragons. I built it because I wanted a companion that feels alive instead of a chatbot I have to open an app to talk to.

## What it can do

- **Roams my screen**: floats on top of everything, breathes, and wanders around on its own if I let it.
- **Talks**: tap it and it opens a chat, then speaks its replies out loud.
- **Sees my screen**: with the eye button it takes a look at what I'm doing and comments on it (needs a vision model).
- **Reacts to highlighted text**: turn on its accessibility service and it explains/defines/rewrites whatever I select anywhere.
- **Share target**: I can share any text straight to it.
- **Has a mood**: say something about killing dragons (even in a game or D&D) and it gets all offended and pouty until I apologize. It's genuinely funny.
- **Remembers me**: rolling memory + mood, so it feels like a companion, not a generic bot.

## Install

1. Download `DragonPal.apk` from this repo to my phone.
2. Tap it. Android asks me to allow "install unknown apps" the first time.
3. Open **Dragon Pal**.

## Setup

1. Pick an AI provider from the dropdown: OpenAI, OpenRouter, Groq, Together, Gemini, DeepSeek, Hugging Face, or Ollama (local, free).
2. Paste my API key.
3. (Optional) rename the dragon. Default is **Ember**.
4. **Save**, then **Test connection** to make sure it's talking.
5. Tap **Start Dragon** and grant the overlay permission.

## Permissions it needs

- **Overlay**: so it can float. Asked when I press Start Dragon.
- **Notifications**: so it can run in the background.
- **Accessibility**: optional, only for the highlight-text reactions. In-app button: "Enable Accessibility".
- **Screen capture**: optional, only for the "see my screen" thing. In-app button: "Enable screen viewing", then the eye icon on the dragon.

## How I use it

- **Tap** the dragon to chat.
- **Drag** it to move it wherever.
- **Roam** toggle: it wanders on its own.
- **Highlight** any text: it explains it in a bubble (needs Accessibility).
- **Share** text: pick Dragon Pal from the share sheet.
- **Eye button**: it looks at my screen and reacts (needs screen viewing and a vision model).
- **Reset memory**: it forgets everything and chills out.

## Build it from source

No Gradle. I build it on Android/Termux with the raw tools:

```bash
pkg install aapt2 d8 apksigner openjdk-21 python
```

You also need `android.jar` from Android 34 (SDK cmdline-tools, `sdkmanager "platforms;android-34"`). Put an SDK in `./sdk` or export `ANDROID_JAR=/path/to/android.jar`, then:

```bash
./build.sh
```

That compiles, dexes, signs (it generates a `keystore.jks` on first run), and drops `DragonPal.apk` in the repo root.

## Notes

- My API key never leaves my phone. It's stored in the app's local prefs, and the app talks straight to whatever provider I picked.
- For a totally free setup, run [Ollama](https://ollama.com) locally and pick "Ollama (local)" in the app.
- DeepSeek's hosted API doesn't take images, so screen viewing needs a vision model from OpenAI, Gemini, Groq, or similar instead.
