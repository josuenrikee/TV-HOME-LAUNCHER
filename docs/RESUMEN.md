# Resumen del proyecto: TV Home Launcher

Documento de referencia con todo lo hecho, las decisiones de diseño, el historial de versiones y lo que queda pendiente.
Lo que se hizo en el TV concreto (apps desactivadas, estado actual del equipo) lo documenta el informe local que genera Claude Code en el PC.

## 1. Objetivo

Un TV Innova 32″ con Google TV (Android 11, 1,5 GB de RAM) en Guayaquil, con anuncios y apps que sobran. Se quiso:

- Quitar anuncios y bloatware de forma reversible (desactivar, no desinstalar).
- Reemplazar la pantalla de inicio por una propia, minimalista, tipográfica y rápida.
- Hacerlo todo con el mínimo esfuerzo del dueño: ADB por Wi-Fi, scripts de Windows sin permisos de administrador, APK compilado en GitHub y actualizaciones pegando un prompt en Claude Code.

Paquete: `ec.josue.tvhome`. Dueño: Josué Prieto (`josuenrikee`).

## 2. Cómo está armado

| Pieza | Detalle |
|---|---|
| Launcher | App Android en Java, sin librerías externas. Una sola `View` que dibuja todo en un lienzo lógico de 1920×1080. |
| Animaciones | Ayudante propio sobre `ValueAnimator`; interpolador con rebote leve para la rueda. |
| Tipografía | Roboto (sistema) y Bebas Neue (reloj grande), esta última en `assets/fonts` con licencia SIL OFL. |
| Clima | Open-Meteo, se actualiza cada 30 min; si el dato tiene más de 2 h muestra `--°`. |
| Saludo | Según la hora de Guayaquil. |
| Firma | Llave fija versionada en el repositorio, para que cada versión se instale encima de la anterior. |
| Compilación | GitHub Actions publica `tvhome.apk` en la release `latest`. |
| Instalación | `scripts\get-apk.ps1` baja el APK y `scripts\install.ps1 -StockLauncher none` lo instala por ADB. |
| Deshacer | `tools\restore.ps1` (apps desactivadas), `scripts\undo-launcher.ps1` (inicio) y `scripts\finish.ps1` (cierra la depuración). |

## 3. Pantallas

- **Inicio:** lista tipo rueda con la app seleccionada adelante y grande, y las demás atrás en contorno. A la derecha, la hora grande apilada (hh sobre mm, con transición al cambiar de minuto) con degradado del color de la app y la fecha centrada encima. Arriba a la izquierda, saludo, campana si hay alarma activa, clima con ciudad e icono de Wi-Fi. Entre el saludo y la fecha, la consola de música cuando algo suena.
- **Todas las apps:** tarjetas con banner o logo; el nombre aparece solo bajo la tarjeta enfocada. Las apps de sistema se ocultan.
- **Entradas:** HDMI 1, HDMI 2, AV y TV (antena), en ese orden.
- **Editar lista:** agregar, quitar, mover y restablecer (con confirmación) apps del inicio; interruptores de clima, saludo, efecto de apertura y alarma; tiempo de reposo (apagado, 5, 10, 15, 30, 60).
- **Alarma:** lista de hasta 5 alarmas; al editar, hora (modo hora con OK), días, etiqueta y eliminar. Al sonar, pantalla sobre otras apps con botones «apagar» y «posponer 10 min».
- **Reposo con reloj:** el reloj apilado al centro, la fecha y, con alarma activa, «alarma HH:MM» arriba de la fecha; con música, portada, título y artista abajo a la izquierda y el reloj teñido con el color de la portada.
- **Fila inferior** (Todas las apps, Entradas, Editar lista, Alarma si está activada, Ajustes y Google TV si existe): oculta hasta bajar después de la última app.
- **Navegación:** Atrás o Inicio devuelven siempre a la primera app. Sin textos de instrucciones en pantalla.

Lista predeterminada: HBO Max, YouTube, Xuper TV, Netflix, Disney+, más BitChord al final cuando está instalado.

## 4. Decisiones de diseño

- Todo el texto en minúsculas, como sello propio.
- Color de acento por app (colores de marca conocidos; para las demás, color dominante del icono, guardado en caché).
- Rueda con profundidad (tamaño y opacidad), sin aplastar el texto en vertical.
- Reloj grande apilado con Bebas Neue, degradado sutil, sin segundos.
- Sin estado de las entradas HDMI: el firmware de este TV reporta "standby" para todas, así que se quitó para no mostrar datos falsos.
- Wi-Fi como icono discreto, con una X si no hay conexión.

## 5. Historial de versiones

| Versión | Cambios |
|---|---|
| 0.1.0 | Primer launcher, scripts de Windows, compilado automático y guía. |
| 0.1.1 | Botón para abrir el inicio original (Google TV) y convivencia con él. |
| 0.1.2 | Entradas HDMI con estado por callback (luego descartado por poco fiable). |
| 0.1.3 | Firma con llave fija: se puede actualizar encima de la versión anterior. Entradas: se quitó el estado y se agregó TV en vivo (antena); se evitó una API oculta. |
| 0.1.4 | Sin textos de ayuda, fila inferior oculta, Atrás vuelve a YouTube, lista nueva, entradas ordenadas y Wi-Fi como icono. |
| 0.1.5 | Todo en minúsculas, interruptores, cuadrícula centrada con nombres en 2 líneas, apertura más corta y shaders reutilizados. Colores de marca por app, color desde el icono y clima que caduca a las 2 h. |
| 0.1.6 | Xuper TV: nombre corto y color azul. |
| 0.1.7 | Tarjetas con logo en todas las apps, rueda en el inicio, nombres cortos y apps de sistema ocultas. |
| 0.1.8 | La app seleccionada se adelanta en la rueda; navegación de la cuadrícula hasta la última fila; nombre sin solaparse; sin puntos. |
| 0.1.9 | Rueda sin aplastado vertical: solo profundidad, tamaño uniforme. |
| 0.1.10 | Reloj grande apilado con Bebas Neue en el inicio; fecha centrada. |
| 0.1.11 | Wi-Fi junto al clima, fecha a la altura del saludo, reloj 20 px más abajo y degradado más suave (55 % a 16 %). |
| 0.1.12 | Los botones dedicados del control (Ajustes, Entradas/Fuentes, TV, HDMI) los atiende el launcher cuando está en pantalla. |
| 0.1.13 | Pantalla invisible para el aviso del panel de notificaciones y servicio de accesibilidad de respaldo (solo filtra teclas); con él el engranaje del control abre Ajustes. |
| 0.1.14 | El servicio también atiende Entradas/Fuentes (KEYCODE_TV_INPUT) y abre la pantalla de Entradas del launcher desde cualquier app. |
| 0.1.15 | Botones de YouTube, Netflix, Prime Video y YouTube Music del control: el servicio escucha el aviso `GLOBAL_BUTTON` de Realtek y las teclas 3003, 3001, 195 y 3025, y abre cada app. Deja registro en logcat (etiqueta TvHomeKeys). Resultado: las teclas no llegan a Android; se quedan en el servicio de Realtek. |
| 0.1.16 | Receptor declarado en el manifiesto para `ACTION_LAUNCH_APP` (experimento). Resultado: no llegó nada; el `sendBroadcast` que se veía era `ACTION_HIDE_STORE_WINDOW` de Realtek hacia el modo tienda. |
| 0.1.17 | Prioridad 1000 en el filtro HOME del launcher (experimento para convivir con `launcherx` activo; el rol HOME ya lo tenía y no bastó). Resultado: Android deja la prioridad en 0 para apps que no son del sistema. |
| 0.1.18 | BitChord (cliente de YouTube Music para TV, `com.music.bitchord.tv`): se agrega una sola vez al final de la lista del inicio cuando está instalado, con acento amarillo (FFD60A). |
| 0.1.19 | Inicio y Atrás vuelven siempre a la primera app de la lista (ya no a YouTube), con recorrido de la rueda; regreso desde otra app con fundido suave (sin el relleno de color) y fundido más suave entre pantallas. Mini consola de multimedia entre el saludo y la fecha (portada, título, artista, progreso, anterior/play-pausa/siguiente); aparece al sonar algo y se esconde 10 min después de pausar; se enfoca con Arriba desde la primera app. Requiere activar una vez el acceso a notificaciones: `cmd notification allow_listener ec.josue.tvhome/ec.josue.tvhome.MediaListener` (reversa: `disallow_listener`). |
| 0.1.20 | Consola: Izquierda tras «anterior» enfoca toda la consola (portada y textos) y OK abre la app que suena. Inicio desde la lista ya no da un brinco: `clampSelection` cancelaba el recorrido de la rueda al reanudar (el sistema pausa y reanuda el launcher al pulsar Inicio). |
| 0.1.21 | La consola se oculta a los 3 min en pausa (antes 10). Reposo con reloj: tras N min sin tocar el control en el inicio (Editar lista: apagado/5/10/15/30/60, por defecto 10) la lista y los datos salen y el reloj apilado se desliza al centro; la fecha se queda; con música sonando aparece portada+título+artista abajo a la izquierda (sin controles). Cualquier tecla solo despierta (se consume). Sin redibujar salvo cambio de minuto y con micro-desplazamiento del reloj. |
| 0.1.22–0.1.23 | Reposo en Editar: fila sin enfocar muestra solo el valor activo; al enfocarla salen las seis opciones con la activa rellena y las demás apagadas. |
| 0.1.24 | Alarma diaria (primera versión): entrada «Alarma» en la fila inferior del inicio (activar, hora con ±1 h / ±5 min, «probar en 2 minutos»). Usa AlarmManager.setAlarmClock (suena con el launcher cerrado y se reprograma al reiniciar), servicio en primer plano con sonido de alarma con volumen creciente, wake lock para despertar la pantalla y AlarmActivity encima de otras apps (OK apaga, Abajo pospone 10 min, se silencia sola a los 10 min). Requiere por ADB: appops SYSTEM_ALERT_WINDOW allow y lista blanca de batería. Falta probar con el TV suspendido. |
| 0.1.25 | Alarma: volumen del sistema fijo al 40 % mientras suena (sube de 4 a 40 en 30 s) y se restaura al valor anterior; pantalla con botones «apagar» y «posponer 10 min» (flechas izquierda/derecha + OK, sin botón elegido OK no hace nada); sin respuesta suena 10 min, se repite sola 10 min después y a la segunda se rinde; prueba en 1 minuto. |
| 0.1.26 | Alarma: arranque en silencio real (sistema al mínimo, 1,2 s después sube al 40 % y el sonido sube de 4 a 40 en 30 s) para evitar el golpe inicial; con alarma activa la pantalla no se apaga sola mientras el launcher está al frente (FLAG_KEEP_SCREEN_ON), aviso «no apagues el TV con el control», línea «alarma HH:MM» bajo la fecha en el reposo y reloj de reposo más tenue. |
| 0.1.27 | Textos en minúsculas con caché (menos cadenas nuevas por cuadro); «restablecer lista» pide un segundo OK en 3,5 s; icono de campana junto al saludo cuando hay alarma activa (la hora sigue visible solo en el reposo). |
| 0.1.28 | Alarma completa: hasta 5 alarmas (hora, días L M X J V S D, etiqueta predefinida: despertar, trabajo, siesta, recordatorio, ejercicio; interruptor individual; una vez si no hay días). Interruptor «Alarma» en Editar lista: apagado, desaparece de la fila inferior, de la campana y del reposo y no se programa nada (las alarmas guardadas se conservan). Tiempos del sistema automáticos (opción C): con una alarma a 12 h o menos, pospuesta, sonando o de prueba, pantalla 12 h y sin reposo; si no, normal 30 min de pantalla y 60 de reposo; se revisa al arrancar, en cada alarma y 12 h antes. Requiere por ADB: `appops set ec.josue.tvhome WRITE_SETTINGS allow` y `pm grant ec.josue.tvhome android.permission.WRITE_SECURE_SETTINGS`. En reposo con música, el reloj toma el color dominante de la portada con fundido de 0,6 s. |
| 0.1.29 | Alarma: al editar la hora, OK entra en modo hora; izquierda/derecha alternan horas y minutos y arriba/abajo cambian el número (con flechas visibles); el resto de campos sin cambios. En el reposo con alarma activa, la línea «alarma HH:MM» va arriba de la fecha, centrada. |
| 0.1.30 | Reloj grande puntual (se actualiza al empezar cada minuto, antes hasta 15 s tarde) y con transición: los dígitos que cambian suben y se desvanecen y los nuevos entran desde abajo en 0,45 s. Incluye todo lo de la 0.1.29. |
| 0.1.31 | El contorno del texto de las apps no seleccionadas se dibuja con un Path de contornos unidos (con caché): en Android 14 la fuente variable mostraba el esqueleto de cada letra. |
| 0.1.32 | Atrás en Entradas, abierta con el botón del control desde otra app, vuelve a esa app. Sonido suave al moverse por la lista de inicio, reactivo a la velocidad, con interruptor en Editar lista. |
| 0.1.33 | En el ENVTECH U4K (Realtek, Android 14): si el menú de Entradas de Realtek sale encima del nuestro tras pulsar Entradas, el servicio de teclas lo detecta (paquete y clase, solo si pasaron menos de 2,5 s desde nuestro Entradas) y lo cierra con Atrás. Atrás en Entradas esconde el launcher sin dibujar el inicio antes. El receptor del servicio declara RECEIVER_EXPORTED en Android 13 o más. |

## 6. Problemas encontrados y cómo se resolvieron

- **HDMI "conectado" siempre:** el firmware reporta standby; se quitó el texto de estado.
- **Fallo de compilación por una API oculta:** se usa `getType() == TYPE_TUNER` y el id de la entrada.
- **Actualización rechazada (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`):** cada compilación usaba una llave de depuración distinta. Se fijó una llave versionada y se hizo una desinstalación única (incluido el segundo usuario del TV).
- **Texto aplastado en la rueda:** la escala vertical distinta de la horizontal deformaba las letras; ahora la escala es uniforme.
- **Dígitos del reloj cortados en la maqueta:** se resolvió con texto SVG.
- **Clima "congelado":** era correcto, la temperatura no cambió.
- **Nombre de la app de IPTV:** el nombre visible es "Xuper Tv NF" (paquete `com.android.mgstxNF`), la misma app que Xuper TV.

## 7. Pendientes

- Confirmar de noche la alarma con la lógica de tiempos automática (opción C): que suene y que el TV se mantenga despierto.
- Dar la versión por final (1.0) y cerrar con `scripts\finish.ps1` (cierra el puerto 5555 y desactiva la depuración) cuando el dueño lo decida.
- Revisar a mano si el menú propio del TV (Realtek/KTC) tiene un apagado automático.
- Ideas sin hacer: título de la consola más ancho (hoy se corta a unos 21 caracteres), estado de señal en Entradas (no se sabe si el firmware lo informa), marcar en «Todas las apps» las ya fijadas, nombres dentro de tarjetas que son solo icono.
- Limitaciones aceptadas: Entradas y los botones de apps del control (ver sección 10); la alarma no despierta el TV si se apaga con el botón de encendido.
- Hecho y retirado de esta lista: inicio real, botón de ajustes por `KeyService`, consola, reposo, alarmas completas, BitChord, medición de rendimiento.

## 8. Cómo seguir

1. Pedir el cambio a Claude, indicando "launcher del TV, repositorio TV-HOME-LAUNCHER".
2. Esperar a que GitHub compile la nueva versión.
3. Pegar en Claude Code el prompt estándar de actualización (`get-apk.ps1`, `install.ps1 -StockLauncher none`, comprobar `versionName`).
4. Comprobar en el TV y anotar lo que moleste para la siguiente versión.

## 9. Créditos

Herramientas de `tools/` y ejemplos: [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT). Clima: [Open-Meteo](https://open-meteo.com) (CC BY 4.0). Tipografía Bebas Neue: SIL OFL 1.1. BitChord: [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord), app de terceros instalada aparte. Diseño y desarrollo: Josué Prieto con Claude (Anthropic).

## 10. Conclusión sobre el inicio real y los botones del control (21 oct 2026)

- Con `launcherx` (inicio de Google TV) activo, gana siempre sobre el launcher propio (prioridad 2 contra 0). Probado: `set-home-activity`, rol HOME y prioridad 1000 en el manifiesto; ninguno cambia el resultado sin app privilegiada del sistema (root o ROM modificada).
- Por eso el launcher es el inicio real solo con `launcherx` y `setupwraith` desactivados (usuario 0). Costo: Entradas/Fuentes y los botones de YouTube, Netflix, Prime Video y YouTube Music del control no responden; Inicio, micrófono, Ajustes (por `KeyService`), flechas y Atrás sí.
- Para volver a Google TV: `pm enable --user 0 com.google.android.apps.tv.launcherx` y `com.google.android.tungsten.setupwraith`, o `scripts\undo-launcher.ps1 -StockLauncher com.google.android.apps.tv.launcherx`. La red `XTRIM-FAMILIA-CARRIONC` se borró al desactivar `setupwraith`.

## 11. Estado a 21 oct 2026 (versión 0.1.30)

**Alarma:** AlarmManager.setAlarmClock + servicio en primer plano + pantalla encima de otras apps. Hasta 5 alarmas, días, etiquetas predefinidas, posponer 10 min; sin respuesta suena 10 min, se repite una vez y se rinde. Volumen del sistema fijo al 40 % mientras suena (arranque en silencio, 4 a 40 en 30 s) y se restaura después.

**Tiempos del sistema (opción C):** con una alarma a 12 h o menos, pospuesta, sonando o de prueba, el launcher sube `screen_off_timeout` a 12 h y `sleep_timeout` a 0; si no, los deja en 30 min y 60 min. Requiere por ADB: `appops set ec.josue.tvhome WRITE_SETTINGS allow` y `pm grant ec.josue.tvhome android.permission.WRITE_SECURE_SETTINGS`. Reversible: `settings put system screen_off_timeout 600000` y `settings put secure sleep_timeout 3600000` devuelven los valores de fábrica.

**Hallazgo:** con el TV suspendido con el botón de encendido (STR de Realtek, `persist.vendor.rtk.forcestr=1`) la alarma no despierta el equipo; sonó al despertarlo a mano. Por eso hay que dejarlo en el reposo del launcher. Con la pantalla siempre encendida y los tiempos altos, la alarma de la madrugada del 21 oct sonó bien.

**Permisos por ADB de la alarma:** `appops set ec.josue.tvhome SYSTEM_ALERT_WINDOW allow` y `cmd deviceidle whitelist +ec.josue.tvhome`. Reversibles con `default` y `-`.

**Rendimiento (medición del 21 oct):** RAM justa (420 a 550 MB disponibles, ~360 MB en swap), launcher 72 a 88 MB de PSS, 8,3 a 12,7 % de cuadros lentos (meta menor a 5 %). Se cacheó la conversión a minúsculas del texto (0.1.27); queda por volver a medir.

**Evaluación de diseño (0.1.26):** el informe local (nota estimada 8,3/10) respalda la identidad del color por app y recomienda confirmar acciones destructivas, cuidar la franja superior con música y subir el contraste de las apps lejanas. Las decisiones de autor (fila inferior oculta, todo en minúsculas, sin lector de pantalla) se respetan.
