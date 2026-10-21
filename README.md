# TV Home Launcher

Limpia un TV con Android de anuncios y apps que sobran, y reemplaza su pantalla de inicio por una
propia: tipografía grande, foco fijo, color por app y una lista que se edita desde el control remoto.
Hecho para un Innova 32″ de 1,5 GB de RAM y pensado para repetirse en otros TVs.

## Qué incluye

| Carpeta | Para qué sirve |
|---|---|
| `launcher/` | El launcher: app de Android en Java, sin librerías externas. Inicio, Todas las apps, Entradas HDMI, Editar lista y clima de Guayaquil (Open-Meteo). Ajustes abre el nativo del TV. |
| `scripts/` | Los pasos para Windows, sin permisos de administrador: verificar el PC, inventario, instalar, deshacer y cerrar la depuración. |
| `tools/` | Cliente de ADB en Python puro, desactivar y restaurar paquetes con registro para deshacer. Viene de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT). |
| `examples/` | Listas de apps a desactivar de otros modelos, como punto de partida. De CleanYourTV. |
| `PROMPT.md` | Instrucciones para que Claude Code haga todo el proceso en el PC. |

## Cómo se usa

1. En el TV: Ajustes, Acerca de, pulsar 7 veces "Compilación"; luego Opciones de desarrollador y encender
   **Depuración USB** y **Depuración por red**. Anotar la IP del TV.
2. En un PC con Windows en la misma red Wi-Fi: `git clone` de este repositorio y abrir Claude Code en la carpeta.
3. Decirle: *"Ejecuta PROMPT.md. La IP del TV es 192.168.x.x"*.
4. Responder una sola confirmación (la lista de apps a desactivar) y, al final, pulsar Inicio en el control remoto.

Todo se puede deshacer: las apps se **desactivan**, no se desinstalan, y `scripts\undo-launcher.ps1` y
`tools\restore.ps1` devuelven el estado anterior.

## Resumen completo

Todo el historial de versiones, las decisiones de diseño, los problemas resueltos y lo pendiente están en [docs/RESUMEN.md](docs/RESUMEN.md).

## El APK

Lo compila GitHub Actions en cada cambio de `launcher/` y lo publica en
[Releases](../../releases/latest) como `tvhome.apk`. No hace falta instalar Java ni el SDK de Android.

## Usarlo en otro TV

El launcher y el procedimiento son los mismos. Lo que cambia por TV es la lista de apps a desactivar, que
se arma con el inventario de ese modelo (`scripts\inventory.ps1`). Guarda la lista final en `examples/<modelo>/debloat.txt`.

## Créditos y licencia

Las herramientas de `tools/` y los ejemplos son de [CleanYourTV](https://github.com/Gathin23/cleanyourtv),
de Gathin, bajo licencia MIT. El launcher, los scripts y el resto son de Josué Prieto, también MIT. Los datos
de temperatura son de [Open-Meteo.com](https://open-meteo.com), licencia CC BY 4.0.

## Licencias de terceros

- Tipografía **Bebas Neue** (reloj grande del inicio): SIL Open Font License 1.1, texto en `launcher/app/src/main/assets/fonts/OFL.txt`.
- Clima: [Open-Meteo](https://open-meteo.com), datos CC BY 4.0.
