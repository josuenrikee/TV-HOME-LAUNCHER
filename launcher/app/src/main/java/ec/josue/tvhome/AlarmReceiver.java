package ec.josue.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recibe el aviso de la alarma y, tras reiniciar el TV o cambiar la hora, vuelve a programarla. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent in) {
        String a = in == null ? null : in.getAction();
        if (AlarmStore.ACT_RING.equals(a)) {
            if (in.getBooleanExtra("daily", false)) AlarmStore.schedule(ctx);
            try { ctx.startForegroundService(new Intent(ctx, AlarmService.class)); } catch (Exception ignored) { }
        } else {
            AlarmStore.schedule(ctx);
        }
    }
}
