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
