# Instrucciones para Claude Code

Estás en un PC con Windows, sin permisos de administrador, dentro del repositorio TV Home Launcher.
La persona dueña del TV quiere participar lo mínimo: haz todo tú y pregúntale solo lo que se indica en
**Confirmaciones**. Habla en español, en frases cortas, y al terminar cada paso di en una línea qué pasó.

El TV es un Android TV de 32″ con poca memoria (cerca de 1 GB). Todo es reversible: las apps se
**desactivan** con `pm disable-user --user 0`, nunca se desinstalan.

## Reglas

- No uses `-ExecutionPolicy Bypass`: el entorno lo bloquea. Ejecuta los scripts con `powershell -File scripts\x.ps1`.
  Si Windows dice que el script está bloqueado por venir de internet, quita la marca con
  `Get-ChildItem -Recurse scripts, tools | Unblock-File`. Si la política de la empresa solo permite
  scripts firmados, díselo a la persona y detente.
- No necesitas administrador y no debes pedirlo. Lo único que se crea en el PC es `tools\venv`, `dist\`,
  `inventory\` y `backup\` dentro de esta carpeta. Si hace falta Python, instálalo solo para el usuario.
- Antes de cambiar nada en el TV, el inventario es de solo lectura.
- Un comando `pm` por llamada. Aplica los cambios en lotes pequeños (unos 8 a 10 paquetes) y comprueba
  entre lotes que el TV sigue respondiendo y que el inicio sigue resolviendo (ver Verificar).
- Nunca desactives: servicios de entradas HDMI/AV/antena (los scripts ya protegen los que piden
  `TV_INPUT_HARDWARE`), el menú de fuentes, el control remoto y el pareo Bluetooth, el teclado en pantalla,
  Google Play Store y Google Play Services, el servicio de Wi-Fi, y el actualizador del fabricante.
  Si el nombre de un paquete no dice qué hace, mira sus actividades con `dumpsys package <paquete>` antes de decidir.
- Si algo falla y no estás seguro, para y explica. No insistas en reinstalar en bucle.
- No muevas ni renombres los archivos de lista una vez aplicados: el registro para deshacer usa su ruta.

## Confirmaciones (lo único que le preguntas)

1. La IP del TV, si no puedes encontrarla con `arp -a`, y que ya activó Depuración USB y por red.
2. Cuando conectes por primera vez: que acepte en el TV el aviso "¿Permitir depuración USB?" marcando "Siempre".
3. Una sola vez: la lista de lo que vas a desactivar, en una tabla corta (grupo, paquete, para qué sirve),
   y qué se queda. Si dice "sí", sigues sin más preguntas.
4. Al final: que pulse Inicio en el control remoto y confirme si el inicio nuevo se ve bien.
5. Opcional, una sola pregunta: bloqueo de anuncios por DNS privado (AdGuard). Explica que no bloquea los
   anuncios de YouTube y que cada consulta de DNS del TV pasa por ese servicio.

## Pasos

### 0. Preparar el PC
```
powershell -File scripts\preflight.ps1
powershell -File scripts\setup.ps1
```
Si `preflight` dice NO, explica exactamente qué falta y detente. Si no hay Python, instálalo para el
usuario actual (instalador de python.org con `/quiet InstallAllUsers=0 PrependPath=1`) y repite.

### 1. Inventario (solo lectura)
```
powershell -File scripts\inventory.ps1 -TvHost <IP>
```
Lee `inventory\<IP>\inventory.txt` y resume: marca y modelo, versión de Android y SDK, espacio libre en `/data`,
RAM, quién es el launcher actual, quién más reclama Home, dueños del Wi-Fi guardado, y las entradas de TV.
Reglas de decisión:
- SDK menor que 26: el launcher no se puede instalar. Haz solo la limpieza, díselo claro.
- Menos de 100 MB libres en `/data`: la instalación puede fallar. Primero desactiva apps pesadas o avisa.
- `setupwraith` es dueño del Wi-Fi guardado: no lo desactives (el script ya lo omite).
- Si el TV rechaza APKs (`INSTALL_FAILED_VERIFICATION_FAILURE`) o exige firma del fabricante para Home,
  haz solo la limpieza y di que el launcher es imposible en este TV.

### 2. Armar la lista a desactivar
Busca en `examples\` listas de modelos parecidos (misma marca o misma plataforma: Changhong AI PONT, TCL,
CVTE, MediaTek) y úsalas como guía, no las copies a ciegas: confirma cada paquete contra el inventario.
Agrupa en: telemetría del fabricante, contenido y anuncios del fabricante, servicios de fondo prescindibles,
apps precargadas. Guarda la lista en `lists\<modelo>-lote1.txt`, `lote2.txt`… (8 a 10 paquetes cada una, con
comentarios que expliquen cada línea). Presenta la tabla (Confirmación 3).

### 3. Aplicar la limpieza
Para cada lote, en orden:
```
powershell -File tools\disable.ps1 -TvHost <IP> lists\<lote>.txt
```
Después de cada lote, verifica (sección Verificar). Si algo se rompe, restaura ese lote con
`tools\restore.ps1 -TvHost <IP> lists\<lote>.txt` y sigue con los demás sin ese paquete.
En esta etapa el launcher original sigue activo.

### 4. Instalar el launcher
```
powershell -File scripts\setup.ps1
powershell -File scripts\install.ps1 -TvHost <IP>
```
`install.ps1` descarga el APK de la última release, lo instala, lo arranca, desactiva el launcher original y
lo deja como inicio. Si no queda como inicio, devuelve el original solo. Anota el nombre del launcher
original (`Launcher original: ...`), lo necesitarás para deshacer.

### 5. Verificar
Después de cada paso importante:
- `tools\tv.py 'dumpsys activity activities | grep topResumedActivity'` (para HDMI y Live TV las capturas salen vacías).
- `tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'`
  debe decir `ec.josue.tvhome/.MainActivity`.
- Teclas: `input keyevent KEYCODE_HOME`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_CENTER`. Captura con
  `screencap -p /sdcard/s.png`, tráela con `tv.py --pull` y mírala. Corrige lo que veas mal.
- Abre una app de la lista y vuelve con Inicio. Comprueba que Todas las apps lista apps y que Editar lista
  abre sin errores.
- Entradas HDMI: la pantalla lista las entradas que el TV da a las apps. Prueba cambiar a una con
  `am start -a android.intent.action.VIEW -d content://android.media.tv/passthrough/<id de la entrada>`.
  Si el TV no muestra entradas, dilo: el botón de entrada del control remoto sigue sirviendo.
- Si algo del launcher falla, usa `scripts\undo-launcher.ps1 -TvHost <IP> -StockLauncher <paquete>`.

### 6. Cerrar
```
powershell -File scripts\finish.ps1 -TvHost <IP>
```
Debe decir que el puerto 5555 quedó cerrado. Recuérdale que en el TV puede apagar las Opciones de desarrollador.
Déjale un resumen corto: qué se desactivó, cómo deshacerlo (`tools\restore.ps1` con cada lote y
`scripts\undo-launcher.ps1`), y cómo editar la lista de apps desde el TV (fila inferior, Editar lista).

### 7. Para otros TVs
Si todo salió bien, copia las listas finales a `examples\<marca-modelo>\debloat.txt` con un encabezado de
comentarios (modelo, Android, qué se verificó y qué no) y haz un commit local. No hagas push sin que te lo pida.
