package ec.josue.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Atiende los botones del control que el sistema no resuelve desde que el inicio de Google TV esta desactivado.
 * Solo filtra teclas y escucha el aviso GLOBAL_BUTTON de Realtek; no lee el contenido de la pantalla.
 *  - Engranaje: KEYCODE_NOTIFICATION (83) -> Ajustes del TV.
 *  - Entradas: KEYCODE_TV_INPUT (178) -> pantalla de entradas del launcher.
 *  - Botones de apps (codigos de Realtek): YouTube 3003, Netflix 3001, Prime Video 195, YouTube Music 3025.
 */
public class KeyService extends AccessibilityService {
    private static final String TAG = "TvHomeKeys";
    private static final String ACTION_GLOBAL = "android.intent.action.GLOBAL_BUTTON";
    private static final String ACTION_LAUNCH = "com.google.global_button.ACTION_LAUNCH_APP";

    static volatile KeyService instance;
    private long lastLaunch = 0L;
    private int lastCode = -1;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent in) {
            try {
                Bundle ex = in.getExtras();
                StringBuilder sb = new StringBuilder("recibido ").append(in.getAction());
                if (ex != null) for (String k : ex.keySet()) sb.append(" | ").append(k).append('=').append(ex.get(k));
                Log.d(TAG, sb.toString());
                KeyEvent ke = in.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (ke != null) handleCode(ke.getKeyCode(), ke.getAction() == KeyEvent.ACTION_UP);
                String pkg = in.getStringExtra("launchPackageName");
                if (pkg != null) launchPackage(pkg);
            } catch (Exception e) {
                Log.d(TAG, "error " + e);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(ACTION_GLOBAL);
            f.addAction(ACTION_LAUNCH);
            registerReceiver(receiver, f);
        } catch (Exception e) {
            Log.d(TAG, "no se pudo registrar: " + e);
        }
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private static String pkgFor(int code) {
        switch (code) {
            case 3003: return "com.google.android.youtube.tv";
            case 3001: return "com.netflix.ninja";
            case 195: return "com.amazon.amazonvideo.livingroom";
            case 3025: return "com.google.android.youtube.tvmusic";
            default: return null;
        }
    }

    /** Evita abrir dos veces por el mismo boton (llega por el servicio de teclas y por el aviso). */
    private boolean handleCode(int code, boolean up) {
        Log.d(TAG, "tecla " + code + (up ? " up" : ""));
        String pkg = pkgFor(code);
        if (pkg == null) return false;
        long now = SystemClock.uptimeMillis();
        if (code == lastCode && now - lastLaunch < 1200) return true;
        lastCode = code;
        lastLaunch = now;
        launchPackage(pkg);
        return true;
    }

    void launchPackage(String pkg) {
        try {
            Intent in = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (in == null) in = getPackageManager().getLaunchIntentForPackage(pkg);
            if (in == null) return;
            if (pkg.contains("youtube.tv")) in.putExtra("yt_remote_button", true);
            startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.d(TAG, "no se pudo abrir " + pkg + ": " + e);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean settings = code == KeyEvent.KEYCODE_NOTIFICATION;
        boolean inputs = code == KeyEvent.KEYCODE_TV_INPUT
            || (code >= KeyEvent.KEYCODE_TV_INPUT_HDMI_1 && code <= KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_2);
        boolean apps = pkgFor(code) != null;
        Log.d(TAG, "onKeyEvent " + code + " accion " + event.getAction());
        if (!settings && !inputs && !apps) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            try {
                if (settings) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else if (inputs) {
                    startActivity(new Intent(this, MainActivity.class).putExtra("inputs", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                } else {
                    handleCode(code, false);
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
