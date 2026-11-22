package dev.monedas.billetera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * APK paso 5: se actualiza sola desde GitHub (aroelcapellan21-sudo/monedas-app, repositorio público solo con la app).
 * Al abrir la app (como mucho cada 6 horas) mira la última versión publicada; si es más nueva, pregunta «¿Actualizar ahora?»,
 * la baja y la instala encima. Los datos no se tocan (viven en la Dell) y la huella sigue (misma firma).
 */
class Actualizar {
    static final String API = "https://api.github.com/repos/aroelcapellan21-sudo/monedas-app/releases/latest";
    static final long CADA = 6 * 3600_000L;

    static int miVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (int) pi.getLongVersionCode();
        } catch (Exception e) { return 0; }
    }

    /** Revisa en segundo plano; si hay una versión nueva, pregunta en pantalla. 'siempre' = sin esperar las 6 horas (botón de Ajustes). */
    static void revisar(Activity a, boolean siempre) {
        SharedPreferences p = a.getSharedPreferences("actualizar", Context.MODE_PRIVATE);
        long ahora = System.currentTimeMillis();
        if (!siempre && ahora - p.getLong("ultima", 0) < CADA) return;
        p.edit().putLong("ultima", ahora).apply();
        new Thread(() -> {
            try {
                JSONObject r = leer(API);
                String tag = r.optString("tag_name", "");            // «v5» = versión 5
                int nueva = Integer.parseInt(tag.replaceAll("[^0-9]", ""));
                String url = null;
                JSONArray as = r.optJSONArray("assets");
                for (int k = 0; as != null && k < as.length(); k++)
                    if (as.getJSONObject(k).optString("name").endsWith(".apk")) url = as.getJSONObject(k).optString("browser_download_url");
                final String bajar = url, nombre = r.optString("name", tag);
                if (nueva > miVersion(a) && bajar != null) {
                    a.runOnUiThread(() -> new AlertDialog.Builder(a)
                            .setTitle("Hay una versión nueva")
                            .setMessage(nombre + "\n\n¿Actualizar ahora? Tus datos y tu huella se quedan igual.")
                            .setPositiveButton("Actualizar", (d, w) -> bajarEInstalar(a, bajar))
                            .setNegativeButton("Ahora no", null).show());
                } else if (siempre) {
                    a.runOnUiThread(() -> Toast.makeText(a, "Ya tienes la versión más nueva", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (siempre) a.runOnUiThread(() -> Toast.makeText(a, "No pude revisar ahora (sin internet)", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    static void bajarEInstalar(Activity a, String url) {
        Toast.makeText(a, "Bajando la versión nueva…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(a.getPackageName());
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setInstanceFollowRedirects(true);
                    con.setConnectTimeout(20000); con.setReadTimeout(60000);
                    try (InputStream in = con.getInputStream(); OutputStream out = s.openWrite("monedas.apk", 0, -1)) {
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    Intent i = new Intent(a, Instalado.class);
                    PendingIntent pend = PendingIntent.getBroadcast(a, id, i, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    s.commit(pend.getIntentSender());
                }
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, "No se pudo bajar la versión nueva. Prueba otra vez.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    static JSONObject leer(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000); con.setReadTimeout(20000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        }
    }

    /** Respuesta de Android al instalar: si pide permiso a la persona («¿Instalar esta actualización?»), se lo muestra. */
    public static class Instalado extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmar = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmar != null) { confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirmar); }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(c, "La actualización no se instaló", Toast.LENGTH_LONG).show();
            }
        }
    }
}
