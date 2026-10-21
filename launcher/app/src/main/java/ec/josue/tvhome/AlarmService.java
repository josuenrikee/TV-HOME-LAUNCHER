package ec.josue.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/** Hace sonar la alarma: sube el volumen poco a poco, despierta la pantalla y muestra la pantalla de alarma. */
public class AlarmService extends Service {
    static final String ACT_STOP = "ec.josue.tvhome.ALARM_STOP";
    static final String ACT_SNOOZE = "ec.josue.tvhome.ALARM_SNOOZE";
    static volatile boolean ringing = false;
    static volatile String label = "alarma";
    private static final long MAX_MS = 10 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private ToneGenerator tone;
    private PowerManager.WakeLock wl;
    private AudioManager am;
    private AudioFocusRequest focus;
    private long startedAt;
    private int round = 1;
    private int labelIdx = 0;
    private int prevVol = -1;
    private long rampT0 = 0L;
    private boolean raised = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        String a = in == null ? null : in.getAction();
        if (ACT_STOP.equals(a)) { finishRing(false); return START_NOT_STICKY; }
        if (ACT_SNOOZE.equals(a)) { finishRing(true); return START_NOT_STICKY; }
        if (ringing) return START_NOT_STICKY;
        round = in == null ? 1 : in.getIntExtra("round", 1);
        labelIdx = in == null ? 0 : in.getIntExtra("label", 0);
        label = labelIdx > 0 && labelIdx < AlarmStore.LABELS.length ? AlarmStore.LABELS[labelIdx] : "alarma";
        ringing = true;
        raised = false;
        startedAt = SystemClock.elapsedRealtime();
        startForegroundNotice();
        wake();
        startSound();
        try {
            startActivity(new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        } catch (Exception ignored) { }
        h.postDelayed(ramp, 100);
        return START_NOT_STICKY;
    }

    private void startForegroundNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("alarm", "Alarma", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, AlarmActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarma")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, 2 /* mediaPlayback */);
        else startForeground(7, n);
    }

    @SuppressWarnings("deprecation")
    private void wake() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "tvhome:alarm");
            wl.acquire(MAX_MS + 30000L);
        } catch (Exception ignored) { }
    }

    private void startSound() {
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attr = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        try {
            prevVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            getSharedPreferences("tvhome", MODE_PRIVATE).edit().putInt("alarm_prev_vol", prevVol).apply();
            // el audio arranca con el volumen del sistema al mínimo y en silencio; sube al 40 % cuando ya está fluyendo
            am.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attr).build();
            am.requestAudioFocus(focus);
        } catch (Exception ignored) { }
        Uri u = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(attr);
            mp.setDataSource(this, u);
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.prepare();
            mp.setVolume(0f, 0f);
            mp.start();
            mp.setVolume(0f, 0f);
        } catch (Exception e) {
            mp = null;
            try { tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100); } catch (Exception ignored) { }
        }
        // 1,2 s después (con el audio ya estable) el sistema sube al 40 % y empieza la subida suave
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!ringing) return;
                try {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, Math.round(max * .40f)), 0);
                } catch (Exception ignored) { }
                raised = true;
                rampT0 = SystemClock.elapsedRealtime();
                if (mp != null) { try { mp.setVolume(.1f, .1f); } catch (Exception ignored) { } }
                else beep.run();
            }
        }, 1200);
    }

    private final Runnable beep = new Runnable() {
        @Override public void run() {
            if (!ringing) return;
            if (tone != null) tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700);
            h.postDelayed(this, 1500);
        }
    };

    private final Runnable ramp = new Runnable() {
        @Override public void run() {
            if (!ringing) return;
            long t = SystemClock.elapsedRealtime() - startedAt;
            if (t > MAX_MS) {
                // nadie la apagó: la primera vez se repite sola 10 min después; la segunda se rinde
                finishRing(false, round == 1);
                return;
            }
            if (!raised) {
                if (mp != null) { try { mp.setVolume(0f, 0f); } catch (Exception ignored) { } }
                h.postDelayed(this, 100);
                return;
            }
            // de 4 a 40 en la escala del TV (10 % a 100 % del volumen interno), en 30 s
            float v = Math.min(1f, .1f + .9f * (SystemClock.elapsedRealtime() - rampT0) / 30000f);
            if (mp != null) { try { mp.setVolume(v, v); } catch (Exception ignored) { } }
            h.postDelayed(this, 200);
        }
    };

    private void finishRing(boolean snooze) { finishRing(snooze, false); }

    private void finishRing(boolean snooze, boolean auto) {
        ringing = false;
        h.removeCallbacksAndMessages(null);
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception ignored) { }
        mp = null;
        if (tone != null) { tone.release(); tone = null; }
        try { if (am != null && focus != null) am.abandonAudioFocusRequest(focus); } catch (Exception ignored) { }
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
        try {
            if (am != null) {
                int v = prevVol >= 0 ? prevVol : getSharedPreferences("tvhome", MODE_PRIVATE).getInt("alarm_prev_vol", -1);
                if (v >= 0) am.setStreamVolume(AudioManager.STREAM_ALARM, v, 0);
            }
        } catch (Exception ignored) { }
        prevVol = -1;
        getSharedPreferences("tvhome", MODE_PRIVATE).edit().remove("alarm_prev_vol").apply();
        if (snooze) AlarmStore.once(this, System.currentTimeMillis() + AlarmStore.SNOOZE_MIN * 60000L, 1, labelIdx);
        else if (auto) AlarmStore.once(this, System.currentTimeMillis() + AlarmStore.SNOOZE_MIN * 60000L, 2, labelIdx);
        AlarmStore.updatePower(this, false);
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        ringing = false;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
