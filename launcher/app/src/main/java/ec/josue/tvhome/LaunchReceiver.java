package ec.josue.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

/**
 * Receptor declarado en el manifiesto para el aviso de Google "abrir app" que Realtek emite con los botones
 * de YouTube, Netflix, Prime Video y YouTube Music. Realtek solo lo envia si encuentra un receptor declarado.
 */
public class LaunchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        try {
            Bundle ex = in.getExtras();
            StringBuilder sb = new StringBuilder("manifiesto recibido ").append(in.getAction());
            if (ex != null) for (String k : ex.keySet()) sb.append(" | ").append(k).append('=').append(ex.get(k));
            Log.d("TvHomeKeys", sb.toString());
            String pkg = in.getStringExtra("launchPackageName");
            Parcelable li = in.getParcelableExtra("launchIntent");
            KeyService svc = KeyService.instance;
            if (svc != null && pkg != null) {
                svc.launchPackage(pkg);
            } else if (li instanceof Intent) {
                c.startActivity(((Intent) li).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Exception e) {
            Log.d("TvHomeKeys", "error en receptor: " + e);
        }
    }
}
