#!/bin/sh
# Builds Penombra with the Android SDK command-line tools only: no Gradle, no libraries.
#
#   sh build.sh        normal build, installs with a tap           -> build/Penombra.apk
#   sh build.sh adb    test-only build (android:testOnly), for     -> build/Penombra-adb.apk
#                      `adb install -t` only. It is the one build Android lets read the real state of
#                      Extra dim (Reduce bright colors); the normal build keeps its own record instead.
#
# Needs: Android SDK with build-tools and platforms;android-34, JDK 17+, Python 3 (only to zip classes.dex).
# Optional settings, as environment variables or in a file `build.local` next to this script (not committed):
#   ANDROID_HOME        SDK folder (default: the usual place for this OS)
#   BUILD_TOOLS         build-tools version (default: the newest installed)
#   JAVA_HOME           JDK folder (default: javac on PATH)
#   PENOMBRA_KEYSTORE   signing keystore (default: penombra.keystore, created on first build)
#   PENOMBRA_KS_PASS    its password (default: android, fine for a local key; use your own for releases)
#   COPY_TO             folder that also gets a copy named Penombra[-adb]-<version>.apk
set -e
cd "$(dirname "$0")"
[ -f build.local ] && . ./build.local

VARIANT="${1:-normal}"
case "$VARIANT" in
  normal|share) VARIANT=normal ;;
  adb) ;;
  *) echo "usage: sh build.sh [adb]" >&2; exit 1 ;;
esac

# ---- tools -----------------------------------------------------------------------------------------
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) WIN=1; EXE=.exe; BAT=.bat; SEP=';' ;;
  *) WIN=; EXE=; BAT=; SEP=':' ;;
esac
winpath() { if [ -n "$WIN" ]; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  if [ -n "$WIN" ] && [ -n "$LOCALAPPDATA" ]; then SDK="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk"
  elif [ "$(uname -s)" = Darwin ]; then SDK="$HOME/Library/Android/sdk"
  else SDK="$HOME/Android/Sdk"; fi
fi
[ -n "$WIN" ] && SDK="$(cygpath -u "$SDK")"
[ -d "$SDK/build-tools" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 1; }
BT="$SDK/build-tools/${BUILD_TOOLS:-$(ls "$SDK/build-tools" | sort -V | tail -n 1)}"
JAR="$SDK/platforms/android-34/android.jar"
[ -f "$JAR" ] || { echo "Missing platforms;android-34 in $SDK" >&2; exit 1; }
if [ -n "$JAVA_HOME" ]; then JBIN="$( [ -n "$WIN" ] && cygpath -u "$JAVA_HOME" || printf '%s' "$JAVA_HOME")/bin/"; else JBIN=; fi
PY=
for p in python3 python; do
  if "$p" -c 'import zipfile' >/dev/null 2>&1; then PY="$p"; break; fi
done
[ -n "$PY" ] || { echo "Python 3 not found" >&2; exit 1; }

# Target 34 (= the platform compiled against). With 30, Google Play Protect blocks a fresh install
# as "built for an older version of Android".
TARGET_SDK=34
KS="${PENOMBRA_KEYSTORE:-penombra.keystore}"
KS_PASS="${PENOMBRA_KS_PASS:-android}"

# ---- build -----------------------------------------------------------------------------------------
VER="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)"
W="build/$VARIANT"
rm -rf "$W"
mkdir -p "$W/res" "$W/gen" "$W/classes" "$W/dex"

if [ "$VARIANT" = adb ]; then
  # The only difference: android:testOnly, which the normal package installer refuses.
  awk '{ print } /android:allowBackup="false"/ { match($0, /^[ \t]*/); print substr($0, 1, RLENGTH) "android:testOnly=\"true\"" }' \
    app/AndroidManifest.xml > "$W/AndroidManifest.xml"
  grep -q 'android:testOnly="true"' "$W/AndroidManifest.xml" || { echo "could not add testOnly" >&2; exit 1; }
  MANIFEST="$W/AndroidManifest.xml"
  NAME=Penombra-adb
else
  if grep -q testOnly app/AndroidManifest.xml; then echo "app/AndroidManifest.xml must not set testOnly" >&2; exit 1; fi
  MANIFEST=app/AndroidManifest.xml
  NAME=Penombra
fi
OUT="build/$NAME.apk"
echo "Penombra $VER ($VARIANT), build-tools $(basename "$BT")"

echo "1/6 resources"
"$BT/aapt2$EXE" compile --dir app/res -o "$W/res/res.zip"
"$BT/aapt2$EXE" link -I "$JAR" --manifest "$MANIFEST" --java "$W/gen" \
  --min-sdk-version 31 --target-sdk-version "$TARGET_SDK" -o "$W/unsigned.apk" "$W/res/res.zip"

echo "2/6 java"
"${JBIN}javac$EXE" -source 8 -target 8 -bootclasspath "$(winpath "$JAR")$SEP$(winpath "$BT/core-lambda-stubs.jar")" \
  -encoding UTF-8 -Xlint:-options -d "$W/classes" $(find app/src "$W/gen" -name '*.java')

echo "3/6 dex"
"$BT/d8$BAT" --lib "$JAR" --min-api 31 --release --output "$W/dex" $(find "$W/classes" -name '*.class')

echo "4/6 package"
"$PY" -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'classes.dex'); z.close()" \
  "$W/unsigned.apk" "$W/dex/classes.dex"
"$BT/zipalign$EXE" -f -p 4 "$W/unsigned.apk" "$W/aligned.apk"

echo "5/6 key"
if [ ! -f "$KS" ]; then
  echo "    creating a new signing key in $KS (keep it: updates must be signed with the same key)"
  "${JBIN}keytool$EXE" -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias penombra -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Penombra" >/dev/null 2>&1
fi

echo "6/6 sign"
"$BT/apksigner$BAT" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT" "$W/aligned.apk"
ls -l "$OUT"
if [ -n "$COPY_TO" ]; then
  cp "$OUT" "$COPY_TO/$NAME-$VER.apk"
  ls -l "$COPY_TO/$NAME-$VER.apk"
fi
