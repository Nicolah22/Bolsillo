package co.bolsillo.app;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * Escucha las notificaciones del celular y guarda solo las que parecen
 * movimientos de plata (tienen un monto y una palabra como "recibiste",
 * "transferiste", "pagaste"...). Bolsillo las lee después y te las muestra
 * para que confirmes. Nada sale del celular.
 */
public class NotiBancoService extends NotificationListenerService {
    static final String PREFS = "bolsillo_noti";
    static final String KEY = "cola";
    private static final int MAX = 300;

    private static final Pattern MONTO = Pattern.compile("\\$\\s?\\d|\\d{1,3}([.,]\\d{3})+([.,]\\d{1,2})?");
    private static final Pattern CLAVE = Pattern.compile(
        "(?i)(recib|transfer|envi|envío|pag|compr|retir|abon|consign|lleg|plata|dinero|d[eé]bit|cargo|movimiento|giro)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle e = n.extras;
            if (e == null) return;
            CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence x = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (x == null) x = e.getCharSequence(Notification.EXTRA_TEXT);
            String titulo = t == null ? "" : t.toString();
            String texto = x == null ? "" : x.toString();
            String todo = titulo + " " + texto;
            if (!MONTO.matcher(todo).find() || !CLAVE.matcher(todo).find()) return;
            String app;
            try {
                app = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)).toString();
            } catch (Exception ex) {
                app = sbn.getPackageName();
            }
            guardar(this, sbn.getPackageName(), app, titulo, texto, sbn.getPostTime());
        } catch (Exception ignored) {
        }
    }

    static synchronized void guardar(Context c, String pkg, String app, String titulo, String texto, long cuando) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            String firma = pkg + "|" + titulo + "|" + texto;
            // La misma notificación puede llegar varias veces (actualizaciones): se guarda una sola
            for (int i = Math.max(0, a.length() - 40); i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (firma.equals(o.optString("firma")) && Math.abs(cuando - o.optLong("cuando")) < 10 * 60 * 1000) return;
            }
            JSONObject o = new JSONObject();
            o.put("id", cuando + "-" + Integer.toHexString(firma.hashCode()));
            o.put("pkg", pkg);
            o.put("app", app);
            o.put("titulo", titulo);
            o.put("texto", texto);
            o.put("cuando", cuando);
            o.put("firma", firma);
            a.put(o);
            JSONArray b = new JSONArray();
            for (int i = Math.max(0, a.length() - MAX); i < a.length(); i++) b.put(a.get(i));
            p.edit().putString(KEY, b.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
