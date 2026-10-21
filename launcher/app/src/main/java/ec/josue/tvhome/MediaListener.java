package ec.josue.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Sin logica propia: tenerlo habilitado (acceso a notificaciones) es lo que permite al launcher leer las
 * sesiones de multimedia para la mini consola. No lee ni guarda notificaciones.
 */
public class MediaListener extends NotificationListenerService {
}
