# RetroLink v0.6.7 RC1 — Checklist

## Build
- [ ] GitHub Actions termina SUCCESS.
- [ ] APK contiene `libretro_n64.so`, `libretrolink_native.so` y `libc++_shared.so`.
- [ ] `versionName` = `0.6.7-rc1`.

## Remote Centering / Mario Kart 2P
- [ ] P2 inicia en FULL y menús se ven completos.
- [ ] PLAYER P2 muestra el cuadro del videojuego centrado verticalmente.
- [ ] No queda una banda negra grande solo arriba o solo abajo.
- [ ] El contenido visible mantiene un centro vertical estable entre frames.
- [ ] No se recorta pista/HUD por detectar falsamente escenas oscuras.
- [ ] PLAYER P2 conserva nitidez de v0.6.6 y JPEG HQ.
- [ ] HUD remoto puede ocultarse/mostrarse con ⚙/×.
- [ ] Layout remoto de controles persiste.

## Sala / biblioteca
- [ ] Selector 1P–4P completo.
- [ ] Carátulas automáticas siguen funcionando y cargan desde caché.

## Regresión N64
- [ ] Smash Bros: stick arriba/abajo/izquierda/derecha correcto.
- [ ] Donkey Kong 64: sin ghosting histórico.
- [ ] P1 RACE conserva comportamiento validado.
- [ ] Gamepad Bluetooth/USB sigue operativo.
- [ ] Salir/minimizar/reabrir no provoca crash.

## Gate para v0.7.0
- [ ] Con v0.6.7 físicamente validado, congelar baseline N64 y comenzar MultiPlatform Foundation.


## v0.6.8 Manual Player Viewport
- [ ] P1 2P: PANTALLA abre panel y flechas desplazan el cuadro en vivo.
- [ ] P2 PLAYER: PANTALLA abre panel y ajuste es independiente de P1.
- [ ] P3/P4 conservan ajuste propio al reconectar.
- [ ] Z-/Z+ ajustan zoom sin mostrar bordes negros.
- [ ] CENTRO restablece X=0, Y=0, Z=100%.
- [ ] Reiniciar app conserva encuadre manual.
- [ ] FULL no se altera por los ajustes PLAYER.


## v0.6.9 Remote Manual Viewport Fix
- [ ] P2 abre PANTALLA y ←/→ mueve el cuadro claramente.
- [ ] P2 ↑/↓ mueve el cuadro claramente.
- [ ] El texto X/Y cambia en cada pulsación.
- [ ] Z+/Z− cambia el zoom.
- [ ] CENTRO vuelve a X 0 / Y 0 / Z 100%.
- [ ] El panel recibe toques aunque L/R y controles táctiles estén visibles.
- [ ] P1 sigue ajustando pantalla sin regresiones.

## v0.6.10 Final UI Polish
- [ ] ⚙ del Host abre/cierra el panel Configuración de Sala.
- [ ] 1/2/3/4 jugadores funcionan tanto en la barra principal como en el panel lateral.
- [ ] Gráficos, Video remoto, Split-screen y HUD actualizan valores reales.
- [ ] P1: ⚙ muestra menú final; PANTALLA mueve X/Y y Z+/Z−.
- [ ] P1: AJUSTES abre panel in-game y sus tres controles cambian preferencias reales.
- [ ] P2-P4: PANTALLA recibe toques y el desplazamiento es claramente visible.
- [ ] MOVER sigue abriendo el editor de controles correspondiente.
- [ ] FULL/PLAYER, RESET y SALIR mantienen comportamiento existente.
