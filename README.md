# Bolsillo

App para llevar el control de tu plata: ingresos, gastos, ahorros con metas, préstamos, deudas, cuentas y gastos fijos. Los datos se guardan solo en el celular; no hace falta crear cuenta.

## Descargar la APK

Ve a **Releases** (a la derecha en GitHub) y descarga `Bolsillo.apk` de la versión más reciente. Ábrela en el celular Android y acepta instalar desde esta fuente.

Cada vez que se sube un cambio a `main`, GitHub compila una APK nueva automáticamente (pestaña **Actions**). Como todas se firman con la misma llave, la nueva versión se instala encima sin borrar los datos.

## Pasar tus datos desde la versión de Claude

1. En la versión de Claude: ⚙ → Tus datos → **Descargar copia**.
2. En la app: ⚙ → Tus datos → **Importar copia** y elige ese archivo.

## Estructura

- `src/bolsillo.html` — la app (misma base que la versión de Claude).
- `scripts/build_www.py` — la convierte en `www/` para el celular: fuentes incluidas, datos en el teléfono, copias con el menú Compartir.
- `android/` — proyecto Android de Capacitor. `app/bolsillo.keystore` es la llave de firma; no la cambies o las actualizaciones no se podrán instalar encima.
- `.github/workflows/apk.yml` — compila y publica la APK.

## Compilar en tu computador (opcional)

Necesitas Node 20, JDK 17 y Android Studio.

```
npm ci
python3 scripts/build_www.py
npx cap sync android
cd android && ./gradlew assembleRelease
```
