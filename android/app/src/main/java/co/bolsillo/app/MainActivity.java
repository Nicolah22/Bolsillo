package co.bolsillo.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NotiBancoPlugin.class);
        super.onCreate(savedInstanceState);
        recibir(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        recibir(intent);
    }

    /** "Compartir → Bolsillo" desde el comprobante de Nequi, Bancolombia, etc. */
    private void recibir(Intent i) {
        try {
            if (i == null || !Intent.ACTION_SEND.equals(i.getAction()) || i.getType() == null) return;
            if (i.getType().startsWith("image/")) {
                Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u != null) Compartido.leerImagen(getApplicationContext(), u);
            } else if (i.getType().startsWith("text/")) {
                String t = i.getStringExtra(Intent.EXTRA_TEXT);
                if (t != null) Compartido.guardar(getApplicationContext(), t, "texto");
            }
            setIntent(new Intent(this, MainActivity.class));
        } catch (Exception ignored) {
        }
    }
}
