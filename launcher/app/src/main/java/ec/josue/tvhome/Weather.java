package ec.josue.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Temperatura de Guayaquil desde Open-Meteo (https://open-meteo.com, datos CC BY 4.0).
 * Coordenadas fijas, sin permiso de ubicación; una consulta como máximo cada 30 minutos.
 */
final class Weather {
    static final long TTL_MS = 30L * 60L * 1000L;
    static final long MAX_AGE_MS = 2L * 60L * 60L * 1000L;
    static final int NONE = Integer.MIN_VALUE;

    private static final String URL_STR =
        "https://api.open-meteo.com/v1/forecast?latitude=-2.1894&longitude=-79.8891"
        + "&current=temperature_2m&timezone=America%2FGuayaquil";

    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    Weather(Context ctx) {
        sp = ctx.getSharedPreferences("tvhome_weather", Context.MODE_PRIVATE);
    }

    /** Última temperatura guardada, o NONE. */
    int cached() {
        if (!sp.contains("temp")) return NONE;
        long age = System.currentTimeMillis() - sp.getLong("at", 0L);
        if (age < 0 || age > MAX_AGE_MS) return NONE; // dato viejo: mejor "--" que un número congelado
        return Math.round(sp.getFloat("temp", 0f));
    }

    /** Pide una temperatura nueva si la guardada tiene más de 30 minutos. Avisa en el hilo principal. */
    void refreshIfStale(final Runnable onChanged) {
        long age = System.currentTimeMillis() - sp.getLong("at", 0L);
        if (busy || (sp.contains("temp") && age >= 0 && age < TTL_MS)) return;
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(URL_STR).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    double t = new JSONObject(sb.toString()).getJSONObject("current").getDouble("temperature_2m");
                    sp.edit().putFloat("temp", (float) t).putLong("at", System.currentTimeMillis()).apply();
                    main.post(onChanged);
                } catch (Exception ignored) {
                    // Sin red o servicio caído: se conserva el último valor.
                } finally {
                    if (c != null) c.disconnect();
                    busy = false;
                }
            }
        }, "weather").start();
    }
}
