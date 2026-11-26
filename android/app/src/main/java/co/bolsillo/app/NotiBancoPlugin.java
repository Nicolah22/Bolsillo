package co.bolsillo.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CapacitorPlugin(name = "NotiBanco")
public class NotiBancoPlugin extends Plugin {

    private boolean activo() {
        Context c = getContext();
        return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
    }

    /** La versión "normal" de la APK no declara el servicio (Play Protect lo bloquea al instalar fuera de la Play Store). */
    private boolean disponible() {
        Context c = getContext();
        Intent i = new Intent("android.service.notification.NotificationListenerService");
        i.setPackage(c.getPackageName());
        List<ResolveInfo> l = c.getPackageManager().queryIntentServices(i, 0);
        return l != null && !l.isEmpty();
    }

    /** Xiaomi y otros a veces desconectan el lector: se le pide a Android que lo vuelva a conectar */
    private void pedirReconexion() {
        try {
            if (disponible() && activo() && android.os.Build.VERSION.SDK_INT >= 24)
                android.service.notification.NotificationListenerService.requestRebind(
                    new android.content.ComponentName(getContext(), NotiBancoService.class));
        } catch (Exception ignored) {
        }
    }

    private static NotiBancoPlugin instancia;

    @Override
    public void load() {
        instancia = this;
        pedirReconexion();
    }

    static void avisarCompartido() {
        try {
            if (instancia != null) instancia.notifyListeners("compartido", new JSObject());
        } catch (Exception ignored) {
        }
    }

    @PluginMethod
    public void compartidos(PluginCall call) {
        try {
            JSObject r = new JSObject();
            synchronized (Compartido.class) {
                SharedPreferences p = getContext().getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
                r.put("lista", new JSArray(p.getString(Compartido.KEY, "[]")));
                p.edit().putString(Compartido.KEY, "[]").apply();
            }
            call.resolve(r);
        } catch (Exception e) {
            call.reject("No se pudo leer");
        }
    }

    @PluginMethod
    public void estado(PluginCall call) {
        JSObject r = new JSObject();
        boolean d = disponible();
        r.put("disponible", d);
        r.put("activo", d && activo());
        SharedPreferences p = getContext().getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
        r.put("conectado", p.getLong("conectado", 0));
        r.put("desconectado", p.getLong("desconectado", 0));
        r.put("vistas", p.getInt("vistas", 0));
        r.put("ultima", p.getLong("ultima", 0));
        call.resolve(r);
    }

    @PluginMethod
    public void reconectar(PluginCall call) {
        pedirReconexion();
        call.resolve();
    }

    @PluginMethod
    public void diagnostico(PluginCall call) {
        try {
            SharedPreferences p = getContext().getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
            JSObject r = new JSObject();
            r.put("lista", new JSArray(p.getString(NotiBancoService.DIAG, "[]")));
            call.resolve(r);
        } catch (Exception e) {
            call.reject("No se pudo leer");
        }
    }

    @PluginMethod
    public void abrirAjustes(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo abrir la configuración");
        }
    }

    @PluginMethod
    public void pendientes(PluginCall call) {
        try {
            SharedPreferences p = getContext().getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
            JSObject r = new JSObject();
            r.put("lista", new JSArray(p.getString(NotiBancoService.KEY, "[]")));
            call.resolve(r);
        } catch (Exception e) {
            call.reject("No se pudieron leer las notificaciones");
        }
    }

    @PluginMethod
    public void borrar(PluginCall call) {
        try {
            JSArray ids = call.getArray("ids");
            Set<String> quitar = new HashSet<>();
            if (ids != null) for (int i = 0; i < ids.length(); i++) quitar.add(ids.getString(i));
            synchronized (NotiBancoService.class) {
                SharedPreferences p = getContext().getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
                JSONArray a = new JSONArray(p.getString(NotiBancoService.KEY, "[]"));
                JSONArray b = new JSONArray();
                for (int i = 0; i < a.length(); i++) {
                    if (!quitar.contains(a.getJSONObject(i).optString("id"))) b.put(a.get(i));
                }
                p.edit().putString(NotiBancoService.KEY, b.toString()).apply();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo limpiar");
        }
    }
}
