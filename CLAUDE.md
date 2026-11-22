# Monedas (APK) — la Billetera de Monedas_bot en el teléfono

Reglas generales de Mucho en `~/CLAUDE.md`; las de la Billetera en `~/bot-monedas/CLAUDE.md`. Plan de la APK: `~/ideas-bot-monedas/publico-temporal/plan-apk.html` y la agenda en `~/bot-monedas/PENDIENTES.md` (apartado B).

## Qué es
- Abre la Billetera de la Dell a pantalla completa desde la dirección fija `https://ariel-latitude-e6410.tail177c94.ts.net` (Tailscale Funnel → 127.0.0.1:18095). Nada de datos ni llaves vive en el teléfono; solo la clave cerrada con la huella (si se activa).
- Hecha sin Gradle, como la Calculadora y Puente: `./construir.sh` (aapt2, javac, d8, zipalign, apksigner) → `build/monedas.apk`. `VERSION_CODE=n VERSION_NAME=x ./construir.sh` para versiones nuevas.
- **Llave de firma:** `~/Android/monedas-release.keystore` + `~/Android/monedas-release.pass`. Sin ella no se puede actualizar la app instalada: GUARDAR COPIA.

## Archivos
- `src/dev/monedas/billetera/MainActivity.java`: WebView + barras del teléfono (la página empieza debajo de la hora/batería) + puente `window.MonedasApp` (huella, avisos, sonidos) + «atrás» = `window.monedasAtras()` de la página.
- `Huella.java`: clave cerrada con una llave AES del Keystore que solo se usa con la huella (BiometricPrompt).
- `Avisos.java`: servicio con notificación fija «Monedas · avisos activos»; cada minuto pide `POST /api/disp/avisos {disp, desde}` y muestra cada aviso nuevo (los mismos de Telegram). Suena con `res/raw/*.wav` (USAGE_ALARM si «sonar aunque esté en silencio»).
- `Arranque.java`: al prender el teléfono o actualizar la app, los avisos vuelven solos.
- `sonidos.py`: graba los 4 sonidos (Cristal, Pulso, Alarma, Campana) igual que la página. Ícono: `~/monedas-android-icono/` (el que eligió Mucho, sin esquinas).

## Instalar en el Redmi (por cable)
`adb install -r build/monedas.apk`; luego `pm grant dev.monedas.billetera android.permission.POST_NOTIFICATIONS` y, como la Calculadora, inicio automático (appops 10008), RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND y lista de batería (deviceidle). Xiaomi a veces los borra: repetir. Toda acción por ADB queda en `~/respaldo-dispositivos/<serie>/registro.md` (hook).

## Pruebas
La parte de la página se prueba en `~/bot-monedas/pruebas/e2e_todo.js` con un `window.MonedasApp` falso (huella, avisos, «atrás»). Lo nativo se prueba en el teléfono con Mucho.
