package ec.josue.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.TimeZone;

/** Datos y programación de la alarma. Usa la alarma nativa de Android, así que suena aunque el launcher esté cerrado. */
final class AlarmStore {
    static final String ACT_RING = "ec.josue.tvhome.ALARM_RING";
    static final int SNOOZE_MIN = 10;

    private AlarmStore() { }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("tvhome", Context.MODE_PRIVATE); }

    static boolean enabled(Context c) { return sp(c).getBoolean("alarm_on", false); }
    static int hour(Context c) { return sp(c).getInt("alarm_h", 6); }
    static int minute(Context c) { return sp(c).getInt("alarm_m", 30); }

    static void save(Context c, boolean on, int h, int m) {
        sp(c).edit().putBoolean("alarm_on", on).putInt("alarm_h", h).putInt("alarm_m", m).apply();
        schedule(c);
    }

    /** Próxima vez que toca la alarma diaria, en hora de Guayaquil. */
    static long nextTrigger(Context c) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"));
        long now = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, hour(c));
        cal.set(Calendar.MINUTE, minute(c));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now + 1000L) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    private static PendingIntent ringIntent(Context c, int req) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACT_RING).putExtra("daily", req == 1);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setClock(Context c, long when, int req) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), ringIntent(c, req));
    }

    /** (Re)programa la alarma diaria, o la cancela si está apagada. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (enabled(c)) setClock(c, nextTrigger(c), 1);
        else am.cancel(ringIntent(c, 1));
    }

    /** Un aviso único: posponer o prueba. */
    static void once(Context c, long when) { setClock(c, when, 2); }
}
