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

    static final String DIAG = "recientes";
    private static final Pattern VERBO_MONTO = Pattern.compile(
        "(?i)(envi[oó]|enviaste|recibiste|pagaste|transferiste|pagaron|mand[oó]|lleg[oó]|por|de)\\s+(un total de\\s+)?\\$?\\s?\\d");

    @Override
    public void onListenerConnected() {
        try {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("conectado", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        try {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("desconectado", System.currentTimeMillis()).apply();
            requestRebind(new android.content.ComponentName(this, NotiBancoService.class));
        } catch (Exception ignored) {
        }
    }

    private static String txt(CharSequence c) { return c == null ? "" : c.toString(); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle e = n.extras;
            if (e == null) return;
            String titulo = txt(e.getCharSequence(Notification.EXTRA_TITLE));
            if (titulo.isEmpty()) titulo = txt(e.getCharSequence(Notification.EXTRA_TITLE_BIG));
            String texto = txt(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (texto.isEmpty()) texto = txt(e.getCharSequence(Notification.EXTRA_TEXT));
            // Bandejas tipo "inbox" y chats de SMS: el texto viene en líneas
            CharSequence[] lineas = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lineas != null && lineas.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence l : lineas) if (l != null) sb.append(l).append(" · ");
                if (sb.length() > texto.length()) texto = sb.toString();
            }
            android.os.Parcelable[] msjs = e.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msjs != null && msjs.length > 0) {
                Object ult = msjs[msjs.length - 1];
                if (ult instanceof Bundle) {
                    String m = txt(((Bundle) ult).getCharSequence("text"));
                    if (m.length() > texto.length()) texto = m;
                }
            }
            String todo = titulo + " " + texto;
            SharedPreferences pr = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            pr.edit().putInt("vistas", pr.getInt("vistas", 0) + 1).putLong("ultima", System.currentTimeMillis()).apply();
            boolean clave = CLAVE.matcher(todo).find();
            // Nequi a veces escribe el monto sin "$" ni puntos: "PAULA te envió 10, ¡lo mejor!"
            if (!MONTO.matcher(todo).find() && !(clave && VERBO_MONTO.matcher(todo).find())) return;
            String app;
            try {
                app = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)).toString();
            } catch (Exception ex) {
                app = sbn.getPackageName();
            }
            diagnostico(this, app, titulo, texto, sbn.getPostTime(), clave);
            if (!clave) return;
            guardar(this, sbn.getPackageName(), app, titulo, texto, sbn.getPostTime());
        } catch (Exception ignored) {
        }
    }

    /** Últimas notificaciones con montos que vio el lector, para revisar si algo no se reconoce */
    static synchronized void diagnostico(Context c, String app, String titulo, String texto, long cuando, boolean clave) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(p.getString(DIAG, "[]"));
            JSONObject o = new JSONObject();
            o.put("app", app);
            o.put("titulo", titulo.length() > 200 ? titulo.substring(0, 200) : titulo);
            o.put("texto", texto.length() > 400 ? texto.substring(0, 400) : texto);
            o.put("cuando", cuando);
            o.put("clave", clave);
            a.put(o);
            JSONArray b = new JSONArray();
            for (int i = Math.max(0, a.length() - 15); i < a.length(); i++) b.put(a.get(i));
            p.edit().putString(DIAG, b.toString()).apply();
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
