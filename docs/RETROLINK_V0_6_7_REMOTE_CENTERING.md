# RetroLink v0.6.7 — Remote Centering

Objetivo: cerrar la etapa de refinamiento visual N64 antes de iniciar la arquitectura multiplataforma.

## Ajuste principal
- El cliente remoto vuelve a medir bandas negras/near-black después del JPEG.
- El rectángulo visible resultante se centra geométricamente antes de dibujarse.
- El recorte máximo queda limitado a 26% por lado y exige 90% de muestras oscuras.
- El Host también flexibiliza levemente su detección pre-JPEG.
- Se conserva el pipeline v0.6.6 de PLAYER HQ, 4:3 normalizado y RetroSR espacial.

## Resultado esperado
En Mario Kart 64 2P, el cuadro de P2 debe quedar centrado verticalmente y no apoyado hacia abajo. P1, DK64, Smash Bros, gamepads y biblioteca no deben presentar regresiones.
