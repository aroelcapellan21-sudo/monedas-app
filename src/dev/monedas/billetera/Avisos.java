package dev.monedas.billetera;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * APK paso 4: con la app cerrada, cada minuto pregunta a la Dell si hay avisos nuevos (los mismos de Telegram) y los muestra
 * como notificación. Suena con el sonido elegido; si «sonar aunque esté en silencio» está prendido, suena por el canal de alarma.
 * Android exige una notificación fija («Monedas · avisos activos») para poder revisar cada minuto.
 */
public class Avisos extends Service {
    static final String CANAL_FIJO = "fija", CANAL_AVISOS = "avisos";
    static final int ID_FIJA = 1;
    static final long CADA = 60_000;
    Handler h;
    HandlerThread hilo;
    int siguiente = 100;

    static SharedPreferences pref(Context c) { return c.getSharedPreferences("avisos", MODE_PRIVATE); }

    /** Prende o apaga el servicio según lo guardado (lo llama la página y el arranque del teléfono). */
    static void aplicar(Context c) {
        SharedPreferences p = pref(c);
        Intent i = new Intent(c, Avisos.class);
        if (p.getBoolean("encendidos", true) && p.getString("disp", "").length() > 10) {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } else {
            c.stopService(i);
        }
    }

    static void canales(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel fija = new NotificationChannel(CANAL_FIJO, "Avisos activos (fija)", NotificationManager.IMPORTANCE_MIN);
        fija.setShowBadge(false);
        nm.createNotificationChannel(fija);
        NotificationChannel av = new NotificationChannel(CANAL_AVISOS, "Avisos de Monedas", NotificationManager.IMPORTANCE_HIGH);
        av.setSound(null, null);          // el sonido lo pone la app (para poder sonar aunque esté en silencio)
        av.enableVibration(true);
        av.setVibrationPattern(new long[]{0, 250, 150, 250});
        nm.createNotificationChannel(av);
    }

    PendingIntent abrirApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        canales(this);
        Notification fija = new Notification.Builder(this, CANAL_FIJO)
                .setSmallIcon(R.drawable.ic_noti)
                .setContentTitle("Monedas · avisos activos")
                .setContentText("Revisando tu Billetera cada minuto. Toca para abrir.")
                .setContentIntent(abrirApp())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_FIJA, fija, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID_FIJA, fija);
        hilo = new HandlerThread("avisos");
        hilo.start();
        h = new Handler(hilo.getLooper());
        h.post(this::revisar);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        if (h != null) h.removeCallbacksAndMessages(null);
        if (hilo != null) hilo.quitSafely();
        super.onDestroy();
    }

    void revisar() {
        try {
            SharedPreferences p = pref(this);
            JSONObject pide = new JSONObject().put("disp", p.getString("disp", ""));
            if (p.contains("desde")) pide.put("desde", p.getInt("desde", 0));
            JSONObject r = post(MainActivity.DIRECCION + "api/disp/avisos", pide.toString());
            if (r != null) {
                JSONArray lista = r.optJSONArray("avisos");
                String sonidoFinal = null;
                for (int k = 0; lista != null && k < lista.length(); k++) {
                    JSONObject a = lista.getJSONObject(k);
                    boolean urg = a.optBoolean("urgente");
                    mostrar(a.optString("texto"), urg);
                    sonidoFinal = urg ? p.getString("sonidoVigia", "alarma") : (sonidoFinal == null ? p.getString("sonido", "campana") : sonidoFinal);
                }
                if (sonidoFinal != null) sonar(this, sonidoFinal);   // un solo sonido aunque lleguen varios
                p.edit().putInt("desde", r.optInt("ultimo", p.getInt("desde", 0))).apply();
            }
        } catch (Exception e) { /* sin internet o la Dell apagada: se vuelve a probar en un minuto */ }
        h.postDelayed(this::revisar, CADA);
    }

    void mostrar(String texto, boolean urgente) {
        String[] partes = texto.split("\n", 2);
        String titulo = partes[0].replace("✅ HECHO · ", "✅ ").replace("⏳ ESPERANDO · ", "⏳ ").replace("⚠️ ATENCIÓN · ", "⚠️ ");
        if (titulo.length() > 70) titulo = titulo.substring(0, 67) + "…";
        Notification n = new Notification.Builder(this, CANAL_AVISOS)
                .setSmallIcon(R.drawable.ic_noti)
                .setContentTitle(titulo)
                .setContentText(partes.length > 1 ? partes[1] : texto)
                .setStyle(new Notification.BigTextStyle().bigText(texto))
                .setCategory(urgente ? Notification.CATEGORY_ALARM : Notification.CATEGORY_MESSAGE)
                .setContentIntent(abrirApp())
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(siguiente++, n);
    }

    /** Suena el sonido elegido. Si «sonar aunque esté en silencio» está prendido, por el canal de alarma (Android lo deja sonar en silencio). */
    static void sonar(Context c, String nombre) {
        int id = c.getResources().getIdentifier(nombre, "raw", c.getPackageName());
        if (id == 0) return;
        boolean silencio = pref(c).getBoolean("silencio", true);
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(silencio ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(c, android.net.Uri.parse("android.resource://" + c.getPackageName() + "/" + id));
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.prepare();
            mp.start();
        } catch (Exception e) { }
    }

    static JSONObject post(String url, String cuerpo) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000); con.setReadTimeout(20000);
        con.setRequestMethod("POST"); con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = con.getOutputStream()) { o.write(cuerpo.getBytes(StandardCharsets.UTF_8)); }
        if (con.getResponseCode() != 200) return null;
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        }
    }
}
