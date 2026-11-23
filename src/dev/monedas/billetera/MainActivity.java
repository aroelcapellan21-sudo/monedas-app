package dev.monedas.billetera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

/**
 * Monedas (APK, paso 3): abre la Billetera de la Dell a pantalla completa, desde la dirección fija (Tailscale Funnel).
 * La página es la misma de siempre; la app solo agrega la huella (MonedasApp) y el botón «atrás» del teléfono.
 * Nada de datos ni llaves vive aquí: todo está en la Dell. En el teléfono solo queda la clave cerrada con la huella (si se activa).
 * La página empieza DEBAJO de la barra del teléfono (hora, señal, batería) y termina ENCIMA de la barra de abajo: nada se tapa.
 */
public class MainActivity extends Activity {
    static final String DIRECCION = "https://ariel-latitude-e6410.tail177c94.ts.net/";
    static final String HOST = "ariel-latitude-e6410.tail177c94.ts.net";
    static final int AZUL = Color.parseColor("#0a3fb5");
    WebView web;
    View barraArriba, barraAbajo;
    Huella huella;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        huella = new Huella(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // la página recuerda el teléfono y las pistas (localStorage)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);   // los sonidos de los avisos
        s.setUserAgentString(s.getUserAgentString() + " MonedasApp/2");
        web.addJavascriptInterface(new Puente(), "MonedasApp");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;               // la Billetera se queda dentro de la app
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }   // lo demás, en el navegador
                return true;
            }
        });
        // arriba una franja azul del alto de la barra del teléfono; abajo una blanca del alto de la barra de gestos (o del teclado)
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Color.WHITE);
        barraArriba = new View(this); barraArriba.setBackgroundColor(AZUL);
        barraAbajo = new View(this); barraAbajo.setBackgroundColor(Color.WHITE);
        raiz.addView(barraArriba, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        raiz.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        raiz.addView(barraAbajo, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        raiz.setOnApplyWindowInsetsListener((v, ins) -> {
            int arriba, abajo;
            if (Build.VERSION.SDK_INT >= 30) {
                arriba = ins.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
                abajo = Math.max(ins.getInsets(WindowInsets.Type.navigationBars()).bottom, ins.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                arriba = ins.getSystemWindowInsetTop();
                abajo = ins.getSystemWindowInsetBottom();
            }
            barraArriba.getLayoutParams().height = arriba;
            barraAbajo.getLayoutParams().height = abajo;
            barraArriba.requestLayout(); barraAbajo.requestLayout();
            return ins;
        });
        setContentView(raiz);
        Window w = getWindow();
        w.setStatusBarColor(AZUL);
        w.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 30) {   // íconos de arriba en blanco (sobre el azul) y los de abajo oscuros (sobre el blanco)
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if (b != null) web.restoreState(b); else web.loadUrl(DIRECCION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Actualizar.revisar(this, false);   // paso 5: ¿hay versión nueva en GitHub? (como mucho cada 6 horas)
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        web.saveState(b);
    }

    /** El «atrás» del teléfono hace lo mismo que «← Volver» de la página; si ya no hay a dónde volver, la app va al fondo. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.monedasAtras && window.monedasAtras()) ? 1 : 0", valor -> {
            if (!"1".equals(valor)) moveTaskToBack(true);
        });
    }

    /** Android 13+: el permiso de notificaciones se pide una vez (sin él, los avisos no se ven). */
    void pedirPermiso() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    void js(String codigo) {
        runOnUiThread(() -> web.evaluateJavascript(codigo, null));
    }

    static String comillas(String t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : t.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 32 || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    /** Lo que la página puede pedirle a la app (window.MonedasApp). */
    class Puente {
        @JavascriptInterface public boolean huellaDisponible() { return huella.disponible(); }
        @JavascriptInterface public boolean tieneHuella() { return huella.guardada(); }
        /** Guarda la clave cerrada con la huella. Responde llamando window.monedasHuellaGuardada(true/false). */
        @JavascriptInterface public void guardarHuella(String clave) {
            runOnUiThread(() -> huella.guardar(clave, ok -> js("window.monedasHuellaGuardada && window.monedasHuellaGuardada(" + ok + ")")));
        }
        /** Pide la huella y devuelve la clave a window.monedasHuella(clave) (null si se canceló). */
        @JavascriptInterface public void pedirHuella() {
            runOnUiThread(() -> huella.leer(clave -> js("window.monedasHuella && window.monedasHuella(" + comillas(clave) + ")")));
        }
        @JavascriptInterface public void quitarHuella() { huella.borrar(); }
        @JavascriptInterface public String version() { return String.valueOf(Actualizar.miVersion(MainActivity.this)); }
        @JavascriptInterface public void buscarActualizacion() { runOnUiThread(() -> Actualizar.revisar(MainActivity.this, true)); }
        /** Paso 4: la página manda {disp, encendidos, sonido, sonidoVigia, silencio}; se guarda y se prende/apaga el servicio. */
        @JavascriptInterface public void configurarAvisos(String json) {
            try {
                org.json.JSONObject j = new org.json.JSONObject(json);
                android.content.SharedPreferences.Editor e = Avisos.pref(MainActivity.this).edit();
                if (j.has("disp")) e.putString("disp", j.getString("disp"));
                if (j.has("encendidos")) e.putBoolean("encendidos", j.getBoolean("encendidos"));
                if (j.has("sonido")) e.putString("sonido", j.getString("sonido"));
                if (j.has("sonidoVigia")) e.putString("sonidoVigia", j.getString("sonidoVigia"));
                if (j.has("silencio")) e.putBoolean("silencio", j.getBoolean("silencio"));
                e.apply();
                runOnUiThread(() -> { pedirPermiso(); Avisos.aplicar(MainActivity.this); });
            } catch (Exception ex) { }
        }
        @JavascriptInterface public String avisos() {
            android.content.SharedPreferences p = Avisos.pref(MainActivity.this);
            try {
                return new org.json.JSONObject().put("encendidos", p.getBoolean("encendidos", true)).put("sonido", p.getString("sonido", "campana"))
                        .put("sonidoVigia", p.getString("sonidoVigia", "alarma")).put("silencio", p.getBoolean("silencio", true)).toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void probarSonido(String nombre) { Avisos.sonar(MainActivity.this, nombre); }
        /** Punto 38: manda el archivo de la app por el menú de compartir del teléfono (Telegram, WhatsApp, Instagram…). */
        @JavascriptInterface public void compartirApk() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/vnd.android.package-archive");
                i.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://" + ApkProveedor.AUTORIDAD + "/Monedas.apk"));
                i.putExtra(Intent.EXTRA_TEXT, "Monedas: la app de la Billetera");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Enviar la app"));
            });
        }
        /** Punto 38: manda un texto (el enlace) por el menú de compartir. */
        @JavascriptInterface public void compartirTexto(String texto) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, texto);
                startActivity(Intent.createChooser(i, "Compartir el enlace"));
            });
        }
        /** Punto 38: copia un texto al portapapeles. */
        @JavascriptInterface public void copiar(String texto) {
            runOnUiThread(() -> ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Monedas", texto)));
        }
    }
}
