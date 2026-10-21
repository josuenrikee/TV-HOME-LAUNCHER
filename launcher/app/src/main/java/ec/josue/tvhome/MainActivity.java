package ec.josue.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/** Pantalla de inicio del TV. Todo el dibujo y la navegación viven en {@link LauncherView}. */
public class MainActivity extends Activity {
    private LauncherView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new LauncherView(this);
        setContentView(view);
        hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        view.onResumed();
    }

    @Override
    protected void onPause() {
        view.onPaused();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("inputs", false)) view.openInputsFromKey();
        else view.onHomePressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            event.startTracking();
            return true;
        }
        if (view.onKey(keyCode, false)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            view.onKey(keyCode, true);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            if (event.isTracking() && !event.isCanceled()) view.onKey(keyCode, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Ya estamos en el inicio: Atrás no hace nada aquí. Las pantallas internas lo manejan en onKeyDown.
    }

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
            || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
    }
}
