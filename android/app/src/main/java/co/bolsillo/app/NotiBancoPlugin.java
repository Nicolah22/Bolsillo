package co.bolsillo.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.Set;

@CapacitorPlugin(name = "NotiBanco")
public class NotiBancoPlugin extends Plugin {

    private boolean activo() {
        Context c = getContext();
        return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
    }

    @PluginMethod
    public void estado(PluginCall call) {
        JSObject r = new JSObject();
        r.put("activo", activo());
        call.resolve(r);
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
