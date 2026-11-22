package dev.monedas.billetera;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * La clave de la Billetera, cerrada con la huella: una llave AES del Keystore de Android que SOLO se puede usar
 * después de poner el dedo. Ni la app puede abrir la clave sin la huella. Si cambian las huellas del teléfono, la llave se anula.
 */
class Huella {
    interface Listo { void ok(boolean bien); }
    interface Clave { void ok(String clave); }

    static final String ALIAS = "monedas_huella";
    final Activity act;
    final SharedPreferences pref;

    Huella(Activity a) {
        act = a;
        pref = a.getSharedPreferences("huella", Activity.MODE_PRIVATE);
    }

    boolean disponible() {
        BiometricManager bm = act.getSystemService(BiometricManager.class);
        return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
    }

    boolean guardada() {
        return pref.contains("dato") && pref.contains("iv");
    }

    void borrar() {
        pref.edit().clear().apply();
        try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(ALIAS); } catch (Exception e) { }
    }

    private SecretKey llave(boolean nueva) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (nueva || !ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)          // solo con la huella
                    .setInvalidatedByBiometricEnrollment(true)    // si agregan otra huella al teléfono, hay que volver a activarla
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private void pedirDedo(String titulo, Cipher c, BiometricPrompt.AuthenticationCallback cb) {
        BiometricPrompt bp = new BiometricPrompt.Builder(act)
                .setTitle(titulo)
                .setSubtitle("Monedas")
                .setNegativeButton("Usar mi clave", act.getMainExecutor(), (d, w) -> cb.onAuthenticationError(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "cancelado"))
                .build();
        bp.authenticate(new BiometricPrompt.CryptoObject(c), new CancellationSignal(), act.getMainExecutor(), cb);
    }

    /** Cierra la clave con la huella (pide el dedo una vez para confirmar). */
    void guardar(String clave, Listo listo) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, llave(true));
            pedirDedo("Pon tu dedo para activar la huella", c, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    try {
                        Cipher ci = r.getCryptoObject().getCipher();
                        byte[] dato = ci.doFinal(clave.getBytes(StandardCharsets.UTF_8));
                        pref.edit().putString("dato", Base64.encodeToString(dato, Base64.NO_WRAP))
                                .putString("iv", Base64.encodeToString(ci.getIV(), Base64.NO_WRAP)).apply();
                        listo.ok(true);
                    } catch (Exception e) { listo.ok(false); }
                }
                @Override public void onAuthenticationError(int cod, CharSequence t) { listo.ok(false); }
            });
        } catch (Exception e) { listo.ok(false); }
    }

    /** Abre la clave con la huella. Devuelve null si se canceló o la huella cambió (entonces se borra y se vuelve a la clave). */
    void leer(Clave cb) {
        try {
            byte[] iv = Base64.decode(pref.getString("iv", ""), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, llave(false), new GCMParameterSpec(128, iv));
            pedirDedo("Entrar con tu huella", c, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    try {
                        byte[] dato = Base64.decode(pref.getString("dato", ""), Base64.NO_WRAP);
                        cb.ok(new String(r.getCryptoObject().getCipher().doFinal(dato), StandardCharsets.UTF_8));
                    } catch (Exception e) { cb.ok(null); }
                }
                @Override public void onAuthenticationError(int cod, CharSequence t) { cb.ok(null); }
            });
        } catch (Exception e) {   // la llave se anuló (cambiaron las huellas del teléfono): se borra y se entra con la clave
            borrar();
            cb.ok(null);
        }
    }
}
