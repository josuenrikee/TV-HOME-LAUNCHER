package ec.josue.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Alarmas (hasta 5) y programación. Usa la alarma nativa de Android: suena aunque el launcher esté cerrado. */
final class AlarmStore {
    static final String ACT_RING = "ec.josue.tvhome.ALARM_RING";
    static final String ACT_POWER = "ec.josue.tvhome.ALARM_POWER";
    static final int SNOOZE_MIN = 10;
    static final int MAX = 5;
    static final String[] LABELS = {"", "despertar", "trabajo", "siesta", "recordatorio", "ejercicio"};
    static final String[] DAYS = {"L", "M", "X", "J", "V", "S", "D"};

    /** Con una alarma a 12 h o menos, el TV no debe dormirse solo. */
    static final long HOLD_MS = 12L * 3600L * 1000L;
    static final int SCREEN_HOLD = 12 * 3600 * 1000, SLEEP_HOLD = 0;
    static final int SCREEN_NORMAL = 30 * 60 * 1000, SLEEP_NORMAL = 60 * 60 * 1000;

    static final class Alarm {
        int h = 7, m = 0, days = 127, label = 0;
        boolean on = true;
    }

    private AlarmStore() { }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("tvhome", Context.MODE_PRIVATE); }

    // ---- datos

    static boolean feature(Context c) { return sp(c).getBoolean("alarm_feature", true); }

    static void setFeature(Context c, boolean v) {
        sp(c).edit().putBoolean("alarm_feature", v).apply();
        schedule(c);
    }

    static List<Alarm> list(Context c) {
        List<Alarm> out = new ArrayList<>();
        SharedPreferences s = sp(c);
        String raw = s.getString("alarms", null);
        if (raw == null) {
            // migra la alarma única de las versiones 0.1.24 a 0.1.27
            if (s.contains("alarm_h")) {
                Alarm a = new Alarm();
                a.h = s.getInt("alarm_h", 6);
                a.m = s.getInt("alarm_m", 30);
                a.on = s.getBoolean("alarm_on", false);
                out.add(a);
            }
            return out;
        }
        for (String e : raw.split(";")) {
            String[] f = e.split(",");
            if (f.length < 5) continue;
            try {
                Alarm a = new Alarm();
                a.h = Math.max(0, Math.min(23, Integer.parseInt(f[0])));
                a.m = Math.max(0, Math.min(59, Integer.parseInt(f[1])));
                a.days = Integer.parseInt(f[2]) & 127;
                a.on = "1".equals(f[3]);
                a.label = Math.max(0, Math.min(LABELS.length - 1, Integer.parseInt(f[4])));
                out.add(a);
            } catch (NumberFormatException ignored) { }
            if (out.size() >= MAX) break;
        }
        return out;
    }

    static void save(Context c, List<Alarm> l) {
        StringBuilder b = new StringBuilder();
        for (Alarm a : l) {
            if (b.length() > 0) b.append(';');
            b.append(a.h).append(',').append(a.m).append(',').append(a.days).append(',').append(a.on ? 1 : 0).append(',').append(a.label);
        }
        sp(c).edit().putString("alarms", b.toString()).apply();
        schedule(c);
    }

    /** Hay función de alarma activa y al menos una alarma encendida. */
    static boolean armed(Context c) {
        if (!feature(c)) return false;
        for (Alarm a : list(c)) if (a.on) return true;
        return false;
    }

    // ---- cálculo de la próxima

    private static Calendar now() { return Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil")); }

    /** Próxima hora de disparo de una alarma, o -1 si no tiene. */
    static long nextTrigger(Alarm a) {
        Calendar now = now();
        long t0 = now.getTimeInMillis();
        for (int d = 0; d <= 7; d++) {
            Calendar cal = now();
            cal.add(Calendar.DAY_OF_YEAR, d);
            cal.set(Calendar.HOUR_OF_DAY, a.h);
            cal.set(Calendar.MINUTE, a.m);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= t0 + 1000L) continue;
            int idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // lunes = 0
            if (a.days == 0 || (a.days & (1 << idx)) != 0) return cal.getTimeInMillis();
        }
        return -1L;
    }

    /** {cuándo, índice} de la próxima alarma encendida, o null. */
    static long[] next(Context c) {
        if (!feature(c)) return null;
        List<Alarm> l = list(c);
        long best = -1L;
        int bi = -1;
        for (int i = 0; i < l.size(); i++) {
            if (!l.get(i).on) continue;
            long t = nextTrigger(l.get(i));
            if (t > 0 && (best < 0 || t < best)) { best = t; bi = i; }
        }
        return bi < 0 ? null : new long[] {best, bi};
    }

    // ---- programación

    private static PendingIntent ringIntent(Context c, int req, int round, int label, int idx) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACT_RING)
            .putExtra("daily", req == 1).putExtra("round", round).putExtra("label", label).putExtra("idx", idx);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent powerIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACT_POWER);
        return PendingIntent.getBroadcast(c, 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setClock(Context c, long when, int req, int round, int label, int idx) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), ringIntent(c, req, round, label, idx));
    }

    /** (Re)programa la próxima alarma y el aviso que sube los tiempos del TV, o cancela todo si no hay nada. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long[] nx = next(c);
        if (nx == null) {
            am.cancel(ringIntent(c, 1, 1, 0, -1));
            am.cancel(powerIntent(c));
        } else {
            List<Alarm> l = list(c);
            int idx = (int) nx[1];
            setClock(c, nx[0], 1, 1, l.get(idx).label, idx);
            long pt = nx[0] - HOLD_MS;
            if (pt > System.currentTimeMillis() + 5000L) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pt + 1000L, powerIntent(c));
            else am.cancel(powerIntent(c));
        }
        updatePower(c, false);
    }

    /** Un aviso único: posponer, repetición automática o prueba. */
    static void once(Context c, long when, int round, int label) {
        sp(c).edit().putLong("alarm_snooze_at", when).apply();
        setClock(c, when, 2, round, label, -1);
    }

    static void clearSnooze(Context c) { sp(c).edit().remove("alarm_snooze_at").apply(); }

    // ---- tiempos del sistema (opción C)

    /** El TV debe mantenerse despierto: alarma a 12 h o menos, pospuesta, sonando o de prueba. */
    static boolean hold(Context c) {
        if (!feature(c)) return false;
        if (AlarmService.ringing) return true;
        if (sp(c).getLong("alarm_snooze_at", 0L) > System.currentTimeMillis() - 5000L) return true;
        long[] nx = next(c);
        return nx != null && nx[0] - System.currentTimeMillis() <= HOLD_MS;
    }

    /** Sube o baja los tiempos del sistema. Necesita WRITE_SETTINGS y WRITE_SECURE_SETTINGS (se dan por ADB). */
    static void updatePower(Context c, boolean forceHold) {
        boolean h = forceHold || hold(c);
        int screen = h ? SCREEN_HOLD : SCREEN_NORMAL;
        int sleep = h ? SLEEP_HOLD : SLEEP_NORMAL;
        try {
            if (Settings.System.getInt(c.getContentResolver(), "screen_off_timeout", -1) != screen)
                Settings.System.putInt(c.getContentResolver(), "screen_off_timeout", screen);
        } catch (Exception ignored) { }
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), "sleep_timeout", -1) != sleep)
                Settings.Secure.putInt(c.getContentResolver(), "sleep_timeout", sleep);
        } catch (Exception ignored) { }
    }
}
