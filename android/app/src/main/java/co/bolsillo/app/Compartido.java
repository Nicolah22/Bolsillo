package co.bolsillo.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprobantes que el usuario comparte con Bolsillo (imagen o texto).
 * Las imágenes se leen con ML Kit dentro del celular, sin internet.
 */
public class Compartido {
    static final String KEY = "compartidos";

    static void leerImagen(Context c, Uri uri) {
        try {
            InputImage img = InputImage.fromFilePath(c, uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener(t -> guardar(c, ordenar(t), "imagen"))
                .addOnFailureListener(e -> guardar(c, "", "error"));
        } catch (Exception e) {
            guardar(c, "", "error");
        }
    }

    /** Une las líneas de arriba hacia abajo, para que "Para" quede junto al nombre, "¿Cuánto?" junto al monto... */
    private static String ordenar(Text t) {
        List<Text.Line> lineas = new ArrayList<>();
        for (Text.TextBlock b : t.getTextBlocks()) lineas.addAll(b.getLines());
        lineas.sort((a, b) -> {
            Rect ra = a.getBoundingBox(), rb = b.getBoundingBox();
            if (ra == null || rb == null) return 0;
            if (Math.abs(ra.top - rb.top) < Math.max(8, ra.height() / 2)) return ra.left - rb.left;
            return ra.top - rb.top;
        });
        StringBuilder sb = new StringBuilder();
        for (Text.Line l : lineas) sb.append(l.getText()).append("\n");
        return sb.toString();
    }

    static synchronized void guardar(Context c, String texto, String tipo) {
        try {
            SharedPreferences p = c.getSharedPreferences(NotiBancoService.PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            long t = System.currentTimeMillis();
            o.put("id", "c" + t);
            o.put("texto", texto);
            o.put("tipo", tipo);
            o.put("cuando", t);
            a.put(o);
            p.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {
        }
        NotiBancoPlugin.avisarCompartido();
    }
}
