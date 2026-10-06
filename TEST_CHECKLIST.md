# RetroLink v0.6.6 RC1 — Checklist

## Build
- [ ] GitHub Actions termina SUCCESS.
- [ ] APK contiene `libretro_n64.so`, `libretrolink_native.so` y `libc++_shared.so`.
- [ ] `versionName` = `0.6.6-rc1`.

## Vista remota / Mario Kart 2P
- [ ] P2 inicia en FULL y los menús se ven completos.
- [ ] Al comenzar carrera, PLAYER P2 centra correctamente el viewport del jugador.
- [ ] P2 no muestra línea/franja del viewport vecino.
- [ ] P2 mantiene proporción equivalente a RACE P1 sin estiramiento adicional en el cliente.
- [ ] PLAYER P2 se ve más nítido que v0.6.5, especialmente textos, bordes de pista y kart.
- [ ] Movimiento rápido no agrega blur temporal visible.
- [ ] HUD P2 puede ocultarse/mostrarse con ⚙/×.
- [ ] Layout de controles P2 sigue persistiendo independientemente.

## Sala / biblioteca
- [ ] Selector 1P–4P completo.
- [ ] Carátulas automáticas siguen funcionando y cargan desde caché.
- [ ] Sin Internet, la biblioteca sigue operativa.

## Regresión N64
- [ ] Smash Bros: stick arriba/abajo/izquierda/derecha correcto.
- [ ] Donkey Kong 64: sin ghosting histórico.
- [ ] P1 RACE conserva su encuadre.
- [ ] Gamepad Bluetooth/USB sigue operativo.
- [ ] Salir/minimizar/reabrir no provoca crash.
