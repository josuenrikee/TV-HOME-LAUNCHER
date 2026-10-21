package ec.josue.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Solo filtra teclas: el boton de ajustes de este control manda KEYCODE_NOTIFICATION y el de entradas KEYCODE_TV_INPUT y el sistema lo consume
 * antes de que lo vea cualquier app. No lee el contenido de la pantalla.
 */
public class KeyService extends AccessibilityService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean settings = code == KeyEvent.KEYCODE_NOTIFICATION;
        boolean inputs = code == KeyEvent.KEYCODE_TV_INPUT
            || (code >= KeyEvent.KEYCODE_TV_INPUT_HDMI_1 && code <= KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_2);
        if (!settings && !inputs) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            try {
                if (settings) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    startActivity(new Intent(this, MainActivity.class).putExtra("inputs", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
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
