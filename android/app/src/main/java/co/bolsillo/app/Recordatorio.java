package co.bolsillo.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;

import java.util.Calendar;

/** Recordatorio de la noche: "Tienes N movimientos por revisar". También se reprograma al prender el celular. */
public class Recordatorio extends BroadcastReceiver {
    static final String CANAL = "recordatorios";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
    }

    static boolean activo(Context c) { return prefs(c).getBoolean("rec_activo", false); }
    static int hora(Context c) { return prefs(c).getInt("rec_hora", 20); }

    private static PendingIntent alarma(Context c) {
        Intent i = new Intent(c, Recordatorio.class).setAction("co.bolsillo.app.RECORDAR");
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void programar(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarma(c);
            am.cancel(pi);
            if (!activo(c)) return;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hora(c));
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60_000) cal.add(Calendar.DAY_OF_YEAR, 1);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (Exception ignored) {
        }
    }

    static int pendientes(Context c) {
        int n = prefs(c).getInt("pend_js", 0);
        try {
            n += new JSONArray(prefs(c).getString(NotiBancoService.KEY, "[]")).length();
        } catch (Exception ignored) {
        }
        return n;
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
            programar(c);
            return;
        }
        try {
            int n = pendientes(c);
            if (activo(c) && n > 0) avisar(c, n);
        } catch (Exception ignored) {
        }
        programar(c);
    }

    private static void avisar(Context c, int n) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CANAL) == null)
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT));
        Intent abrir = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String txt = n == 1 ? "Tienes 1 movimiento por revisar" : "Tienes " + n + " movimientos por revisar";
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bolsillo")
            .setContentText(txt + ". Tócalo para agregarlos.")
            .setContentIntent(pi)
            .setAutoCancel(true);
        if (NotificationManagerCompat.from(c).areNotificationsEnabled()) nm.notify(7, b.build());
    }
}
