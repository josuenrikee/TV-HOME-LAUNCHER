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

- **Inicio:** lista tipo rueda con la app seleccionada adelante y grande, y las demás atrás en contorno. A la derecha, la hora grande apilada (hh sobre mm, 24 horas con cero) con degradado del color de la app y la fecha centrada encima. Arriba a la izquierda, saludo y clima con ciudad.
- **Todas las apps:** tarjetas con banner o logo; el nombre aparece solo bajo la tarjeta enfocada. Las apps de sistema se ocultan.
- **Entradas:** HDMI 1, HDMI 2, AV y TV (antena), en ese orden.
- **Editar lista:** agregar o quitar apps del inicio, y activar o apagar clima y saludo.
- **Fila inferior** (Todas las apps, Entradas, Ajustes y Google TV si existe): oculta hasta bajar después de la última app.
- **Navegación:** Atrás o Inicio devuelven el foco a YouTube. Sin textos de instrucciones en pantalla.

Lista predeterminada: HBO Max, YouTube, Xuper TV, Netflix, Disney+, con foco inicial en YouTube.

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

## 6. Problemas encontrados y cómo se resolvieron

- **HDMI "conectado" siempre:** el firmware reporta standby; se quitó el texto de estado.
- **Fallo de compilación por una API oculta:** se usa `getType() == TYPE_TUNER` y el id de la entrada.
- **Actualización rechazada (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`):** cada compilación usaba una llave de depuración distinta. Se fijó una llave versionada y se hizo una desinstalación única (incluido el segundo usuario del TV).
- **Texto aplastado en la rueda:** la escala vertical distinta de la horizontal deformaba las letras; ahora la escala es uniforme.
- **Dígitos del reloj cortados en la maqueta:** se resolvió con texto SVG.
- **Clima "congelado":** era correcto, la temperatura no cambió.
- **Nombre de la app de IPTV:** el nombre visible es "Xuper Tv NF" (paquete `com.android.mgstxNF`), la misma app que Xuper TV.

## 7. Pendientes

- Decidir y ejecutar el paso a **inicio real** (hoy Google TV conserva el HOME y el launcher se abre desde "Todas las apps"). Reversible con `scripts\undo-launcher.ps1`.
  Antes de hacerlo, probar el botón de inicio, el micrófono y el asistente del control remoto.
- Revisar la **posición del icono Wi-Fi** junto al clima (no convence del todo).
- Validar en el TV real las tarjetas de "Todas las apps" y la sensación de la rueda.
- Más limpieza opcional: Prime Video, YouTube Music, Kick, Pluto y Apple TV sin confirmar.
- Al terminar: `scripts\finish.ps1` para cerrar el puerto 5555 y desactivar la depuración.
- Actualizar la documentación de diseño para reflejar el aspecto final.

## 8. Cómo seguir

1. Pedir el cambio a Claude, indicando "launcher del TV, repositorio TV-HOME-LAUNCHER".
2. Esperar a que GitHub compile la nueva versión.
3. Pegar en Claude Code el prompt estándar de actualización (`get-apk.ps1`, `install.ps1 -StockLauncher none`, comprobar `versionName`).
4. Comprobar en el TV y anotar lo que moleste para la siguiente versión.

## 9. Créditos

Herramientas de `tools/` y ejemplos: [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT). Clima: [Open-Meteo](https://open-meteo.com) (CC BY 4.0). Tipografía Bebas Neue: SIL OFL 1.1.
