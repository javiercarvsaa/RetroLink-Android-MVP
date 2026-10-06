# RetroLink v0.5.1 — PresentSync

## Objetivo
Eliminar la presentación de buffers antiguos entre VIs del N64 en pantallas Android de alta frecuencia.

## Cambio principal
La v0.4.9 ejecutaba `GLSurfaceView` en `RENDERMODE_CONTINUOUSLY`. En una pantalla de 120 Hz, `onDrawFrame()` podía entrar 120 veces por segundo mientras el núcleo N64 avanzaba 50/60 veces. Aunque RetroLink omitía `nativeRunFrame()` cuando todavía no correspondía un VI, `GLSurfaceView` podía igualmente realizar el swap de EGL al terminar el callback. Eso permitía presentar contenido antiguo entre frames reales.

PresentSync cambia el frontend a `RENDERMODE_WHEN_DIRTY` y solicita un render únicamente cuando corresponde presentar un nuevo VI, sincronizado con `Choreographer`.

## Incluye
- PresentSync 50/60 Hz sobre pantallas 60/90/120 Hz.
- Ningún swap intencional cuando no existe un nuevo VI.
- Mantiene DK64 VI/Framebuffer Sync de v0.4.9.
- DK64 continúa RAW, sin mezcla temporal RetroSR.
- Mantiene controles de volumen, BLE, streaming y Gamer UI.
- Diagnóstico visible `VI/PRESENT ... · PSYNC`.

No se incluye ninguna ROM.


## v0.5.1 Clean HUD
- Barra de diagnóstico oculta por defecto durante el juego.
- Botón flotante ⚙ para mostrar/ocultar HUD y acceder a Ajustes sin ensuciar la imagen.
- Estado HUD persistente entre sesiones.


## RetroLink v0.6.0 RC1 working copy

Esta working copy conserva PresentSync/Clean HUD de v0.5.1 y añade editor de layout táctil, recorte real de split-screen P1 y teardown idempotente del core. Ver `docs/RETROLINK_V0_6_PROGRESS.md`.
