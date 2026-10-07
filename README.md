# RetroLink Android MVP — v0.6.7 RC1

RetroLink es un frontend Android de juego retro local. Esta versión cierra la etapa N64 de ajuste visual remoto antes de iniciar la arquitectura multiplataforma.

## Incluye

- Nintendo 64 integrado con Mupen64Plus-Next ARM64/GLES3.
- PresentSync y perfiles gráficos existentes.
- Controles táctiles movibles y soporte gamepad Android Bluetooth/USB.
- Biblioteca multi-ROM N64 con búsqueda automática de carátulas y caché local.
- Sala P1–P4 y cliente remoto con HUD ocultable.
- Calidad PLAYER remota heredada de v0.6.6: crop previo, JPEG HQ y RetroSR espacial.
- **Remote Centering v0.6.7:** el cliente vuelve a medir bandas negras/near-black después de decodificar y centra el contenido visible antes de dibujarlo.
- El centrado post-decode tolera artefactos JPEG que podían impedir que el recorte del Host detectara una banda negra completa.
- El recorte está limitado para no confundir túneles/escenas oscuras con letterbox.
- Se conserva FULL como modo inicial remoto y PLAYER como modo manual para carreras split-screen.

RetroLink no incluye ROM, BIOS ni contenido comercial.

## Próxima línea de desarrollo

La siguiente versión mayor será **v0.7.0 MultiPlatform Foundation**, orientada a desacoplar RetroLink de N64 y preparar cores adicionales para SNES, Game Boy/Game Boy Color, Atari y PlayStation 1 sin duplicar la aplicación por consola.


## v0.6.8 Manual Player Viewport
- Ajuste manual de pantalla PLAYER P1-P4.
- Flechas para mover el encuadre, Z-/Z+ para zoom y CENTRO para restablecer.
- Configuración persistente e independiente para Host/P1 y cada cliente remoto.
- El ajuste manual se aplica sobre el enfoque automático de v0.6.7.
- Próxima versión mayor: v0.7.0 MultiPlatform Foundation.


## v0.6.9 RC1 — Remote Manual Viewport Fix
- Corrige P2-P4: los botones de PANTALLA ahora producen desplazamiento visible.
- El paneo remoto usa hasta ±8% del cuadro, con zoom automático progresivo para evitar bordes negros.
- El panel remoto se eleva sobre los controles táctiles para asegurar la recepción de toques.
- Mantiene Remote Centering, PLAYER HQ, RetroSR espacial y ajustes persistentes por jugador.

## v0.6.10 RC1 — Final UI Polish
- Integra el diseño final RetroLink en los menús reales del emulador P1 y P2-P4.
- Rediseña PANTALLA con controles grandes X/Y/Zoom, lectura persistente y prioridad táctil.
- Añade configuración in-game real para gráficos, RetroSR y split-screen.
- Añade panel lateral de Configuración en Sala Multijugador con opciones reales de jugadores, gráficos, streaming, split, HUD y controles.
- Incluye la corrección v0.6.9 del paneo remoto P2-P4.
