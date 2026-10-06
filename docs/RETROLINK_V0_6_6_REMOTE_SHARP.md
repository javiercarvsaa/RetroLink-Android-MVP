# RetroLink v0.6.6 — Remote Sharp View

Objetivo: acercar la imagen remota P2–P4 a la claridad y encuadre de P1 sin aumentar complejidad visible.

## Cambios
1. `SplitScreenProfile.focusedSourceRect()` aplica el overscan configurado y recorta la unión entre viewports.
2. `FrameStreamServer` normaliza el recorte de cada jugador a 4:3 antes de comprimir.
3. PLAYER remoto eleva la calidad JPEG a un rango 90–94.
4. `RemoteFrameView` deja de volver a deformar frames ya normalizados.
5. PLAYER remoto desactiva historia temporal y refuerza solo el sharpen espacial.

## Validación física prioritaria
Comparar simultáneamente RACE P1 y PLAYER P2 en Mario Kart 64, revisando proporción, legibilidad, bordes, HUD y respuesta durante movimiento rápido.
