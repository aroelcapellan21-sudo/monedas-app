package dev.monedas.billetera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Al prender el teléfono (o al instalar una versión nueva), los avisos vuelven a quedar activos solos. */
public class Arranque extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try { Avisos.aplicar(c); } catch (Exception e) { }
    }
}
