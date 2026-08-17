#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build"

# android.jar: set ANDROID_JAR, or drop an SDK under ./sdk, or use $ANDROID_HOME.
if [ -n "$ANDROID_JAR" ]; then
  AJ="$ANDROID_JAR"
elif [ -f "$ROOT/sdk/platforms/android-34/android.jar" ]; then
  AJ="$ROOT/sdk/platforms/android-34/android.jar"
elif [ -n "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  AJ="$ANDROID_HOME/platforms/android-34/android.jar"
else
  echo "Set ANDROID_JAR to android-34's android.jar (or put an SDK in ./sdk)." >&2
  exit 1
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/dex"

echo "== aapt2 compile =="
aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "== aapt2 link =="
aapt2 link -o "$BUILD/resources.apk" \
  -I "$AJ" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  "$BUILD/res.zip"

echo "== javac =="
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac --release 8 -encoding UTF-8 -Xlint:-options \
  -classpath "$AJ" \
  -d "$BUILD/obj" @"$BUILD/sources.txt"

echo "== strip MethodParameters (javac21/d8 bug) =="
python3 "$ROOT/strip_mp.py" "$BUILD/obj"

echo "== d8 (dex) =="
d8 --release --lib "$AJ" --min-api 26 --output "$BUILD/dex" $(find "$BUILD/obj" -name '*.class')

echo "== package =="
cp "$BUILD/resources.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/unsigned.apk" classes.dex)

echo "== sign =="
KS="$ROOT/keystore.jks"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias dragon -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass dragonpass -keypass dragonpass \
    -dname "CN=haxnstuff, O=haxnstuff, C=US" 2>/dev/null
fi
apksigner sign --ks "$KS" --ks-pass pass:dragonpass --key-pass pass:dragonpass \
  --out "$ROOT/DragonPal.apk" "$BUILD/unsigned.apk"

echo "== verify =="
apksigner verify "$ROOT/DragonPal.apk"
ls -la "$ROOT/DragonPal.apk"
