# RetroLink v0.6.4 RC1 — Checklist

## Build
- [ ] GitHub Actions termina SUCCESS.
- [ ] APK contiene `libretro_n64.so`, `libretrolink_native.so` y `libc++_shared.so`.
- [ ] `versionName` = `0.6.4-rc1`.

## Biblioteca / carátulas
- [ ] Abrir biblioteca con Wi-Fi/datos disponibles.
- [ ] Las ROM existentes siguen apareciendo aunque no haya carátula.
- [ ] Al importar una ROM se inicia búsqueda automática sin bloquear la UI.
- [ ] Mario Kart 64 muestra una carátula coherente si existe coincidencia.
- [ ] Donkey Kong 64 muestra una carátula coherente si existe coincidencia.
- [ ] Super Smash Bros. / SMASH BROTHERS encuentra la carátula correcta si está en el catálogo.
- [ ] Cerrar y reabrir la app: carátulas cargan desde caché local.
- [ ] Sin Internet: la biblioteca sigue siendo utilizable y no impide jugar.
- [ ] Botón `↻ CARÁTULAS` reintenta las faltantes.
- [ ] No se muestra una carátula cuando el match es dudoso.

## Sala
- [ ] Selector de jugadores muestra 1, 2, 3 y 4 completos, sin recortes.
- [ ] Cambiar 1P/2P/3P/4P actualiza la sesión correctamente.

## Perfil
- [ ] Dispositivo muestra solo modelo, versión Android y disponibilidad N64.
- [ ] `Virtual` no aparece como mando físico.
- [ ] Un gamepad Bluetooth/USB real sí aparece.

## Regresión N64
- [ ] Smash Bros: stick arriba/abajo/izquierda/derecha correcto.
- [ ] Donkey Kong 64: sin ghosting visual previo.
- [ ] Mario Kart 64: recorte P1/P2 sigue correcto.
- [ ] Editor de controles conserva posiciones/tamaño/opacidad.
- [ ] Salir/minimizar/reabrir no muestra crash de Android.


## v0.6.5 Remote View Parity
- [ ] P2 inicia en FULL y muestra menús/pantallas completas.
- [ ] Al entrar a carrera 2P, cambiar a PLAYER P2 y confirmar proporción 4:3 equivalente a P1.
- [ ] Ocultar/mostrar barra superior de P2 con botón ⚙/×.
- [ ] Verificar modo inmersivo sin barras Android persistentes.
- [ ] Mover controles de P2 y confirmar persistencia independiente.
- [ ] Revalidar P1 RACE, DK64 y carátulas.
