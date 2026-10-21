package ec.josue.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Recibe el aviso de la alarma; tras reiniciar el TV o cambiar la hora, vuelve a programarlo todo. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent in) {
        String a = in == null ? null : in.getAction();
        if (AlarmStore.ACT_RING.equals(a)) {
            AlarmStore.clearSnooze(ctx);
            int idx = in.getIntExtra("idx", -1);
            if (in.getBooleanExtra("daily", false) && idx >= 0) {
                List<AlarmStore.Alarm> l = AlarmStore.list(ctx);
                if (idx < l.size() && l.get(idx).days == 0) {
                    // alarma de una sola vez: se apaga sola al sonar
                    l.get(idx).on = false;
                    AlarmStore.save(ctx, l);
                }
            }
            AlarmStore.schedule(ctx);
            AlarmStore.updatePower(ctx, true);
            try {
                ctx.startForegroundService(new Intent(ctx, AlarmService.class)
                    .putExtra("round", in.getIntExtra("round", 1)).putExtra("label", in.getIntExtra("label", 0)));
            } catch (Exception ignored) { }
        } else if (AlarmStore.ACT_POWER.equals(a)) {
            AlarmStore.updatePower(ctx, false);
        } else {
            AlarmStore.schedule(ctx);
        }
    }
}
