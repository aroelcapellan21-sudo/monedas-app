#!/bin/bash
# Compila, alinea y firma la APK «Monedas» (abre la Billetera de la Dell), sin Gradle: las mismas herramientas que la Calculadora y Puente.
# Uso: ./construir.sh        (deja build/monedas.apk)
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="$HOME/Android/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$HOME/Android/sdk"
BT="$SDK/build-tools/36.0.0"
AJ="$SDK/platforms/android-36/android.jar"
LLAVE="${LLAVE:-$HOME/Android/monedas-release.keystore}"   # fuera del repo a propósito
CLAVE_ARCHIVO="$HOME/Android/monedas-release.pass"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJ"; do
  [ -e "$f" ] || { echo "FALTA $f"; exit 1; }
done

rm -rf build && mkdir -p build/compiled build/classes build/dex build/gen

echo "[1/5] recursos"
"$BT/aapt2" compile --dir res -o build/compiled

echo "[2/5] enlazar manifiesto y recursos"
"$BT/aapt2" link -o build/sin_codigo.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 29 --target-sdk-version 36 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --java build/gen \
  $(ls build/compiled/*.flat)

echo "[3/5] javac + d8"
javac -Xlint:-options --release 8 -classpath "$AJ" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 29 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

echo "[4/5] meter classes.dex y alinear"
cp build/sin_codigo.apk build/con_codigo.apk
(cd build/dex && zip -q -u ../con_codigo.apk classes.dex)
"$BT/zipalign" -f -p 4 build/con_codigo.apk build/alineado.apk

echo "[5/5] firmar"
if [ ! -f "$LLAVE" ]; then
  umask 077
  head -c 24 /dev/urandom | base64 | tr -d '/+=' > "$CLAVE_ARCHIVO"
  keytool -genkeypair -keystore "$LLAVE" -alias monedas -keyalg RSA -keysize 4096 \
    -validity 9125 -storepass "$(cat "$CLAVE_ARCHIVO")" -keypass "$(cat "$CLAVE_ARCHIVO")" \
    -dname "CN=Monedas, O=Personal" >/dev/null 2>&1
  chmod 600 "$LLAVE" "$CLAVE_ARCHIVO"
  echo "   (llave creada en $LLAVE; GUARDA una copia: sin ella no se puede actualizar la app)"
fi
CLAVE="$(cat "$CLAVE_ARCHIVO")"
"$BT/apksigner" sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --min-sdk-version 29 \
  --ks "$LLAVE" --ks-key-alias monedas --ks-pass pass:"$CLAVE" --key-pass pass:"$CLAVE" \
  --out build/monedas.apk build/alineado.apk

"$BT/apksigner" verify build/monedas.apk && echo "   firma: bien"
ls -l build/monedas.apk | awk '{print "APK:", $5, "bytes"}'
