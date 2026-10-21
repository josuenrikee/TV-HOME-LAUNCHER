package ec.josue.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Atiende el intent del panel de notificaciones que el sistema lanza con el boton de ajustes del control
 * (antes lo atendia el inicio de Google TV). Abre los Ajustes del TV y se cierra.
 */
public class PanelActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
        finish();
    }
}
