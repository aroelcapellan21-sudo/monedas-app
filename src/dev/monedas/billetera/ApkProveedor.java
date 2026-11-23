package dev.monedas.billetera;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Punto 38: entrega el archivo de esta misma app (monedas.apk) para mandarlo por el menú de compartir (Telegram, WhatsApp…). Solo lectura. */
public class ApkProveedor extends ContentProvider {
    static final String AUTORIDAD = "dev.monedas.billetera.apk";

    File apk() { return new File(getContext().getApplicationInfo().sourceDir); }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri u) { return "application/vnd.android.package-archive"; }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String modo) throws FileNotFoundException {
        if (!"r".equals(modo)) throw new FileNotFoundException("solo lectura");
        return ParcelFileDescriptor.open(apk(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri u, String[] col, String sel, String[] args, String orden) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"Monedas.apk", apk().length()});
        return c;
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
