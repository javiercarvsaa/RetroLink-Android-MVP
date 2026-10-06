# RetroLink v0.6.3 RC1 — Checklist

## Inicio
- [ ] El menú mantiene el diseño neón y no muestra métricas simuladas.
- [ ] CONTINUAR abre la ROM seleccionada en 1P.
- [ ] Sin ROM, CONTINUAR pasa a importar un juego.
- [ ] JUGAR AHORA abre Biblioteca y selector.
- [ ] BIBLIOTECA abre la biblioteca real.
- [ ] HOST LOCAL abre Sala y solicita Bluetooth solo cuando corresponde.
- [ ] UNIRSE abre el modo teléfono-control.
- [ ] CONTROLES y AJUSTES abren herramientas reales.
- [ ] El resumen de biblioteca muestra el número real de ROM N64.

## Biblioteca N64
- [ ] La biblioteca muestra todas las ROM N64 importadas válidas.
- [ ] Importar otra ROM no elimina los otros juegos.
- [ ] Tocar una tarjeta la deja como juego seleccionado.
- [ ] JUGAR 1P inicia exactamente la ROM seleccionada.
- [ ] HOST LOCAL conserva la ROM seleccionada al abrir Sala.
- [ ] Solo aparece Nintendo 64 como plataforma disponible.
- [ ] No hay Favoritos, búsqueda ni sistemas ficticios activos.

## Sala
- [ ] P1/P2 quedan a la izquierda y P3/P4 a la derecha.
- [ ] Las tarjetas reflejan conexión real de cada jugador.
- [ ] IP/puerto y pantallas reflejan FrameStreamServer real.
- [ ] 1/2/3/4 modifica el número configurado de jugadores.
- [ ] ACTIVAR inicia BLE + servidor de video.
- [ ] DETENER cierra ambos servicios.
- [ ] PROBAR CONTROLES abre la prueba real.
- [ ] INICIAR PARTIDA usa la ROM seleccionada.

## Ajustes
- [ ] Perfil gráfico cambia render real.
- [ ] RetroSR cambia modo real.
- [ ] Nitidez se guarda.
- [ ] Streaming AUTO/30/40/50/60 afecta FrameStreamServer.
- [ ] Calidad JPEG Q65-Q90 se guarda.
- [ ] Deadzone/sensibilidad/invertir Y funcionan.
- [ ] Mando Bluetooth/USB detectado se muestra realmente.
- [ ] Editar posiciones / probar controles funcionan.
- [ ] Recorte P1 cambia SplitScreenProfile.
- [ ] RESTABLECER no borra ROMs.

## Perfil
- [ ] Modelo Android y versión son reales.
- [ ] Conteo de juegos corresponde a la biblioteca local.
- [ ] Última partida es la ROM seleccionada real.
- [ ] Estado del core N64 depende del .so instalado.
- [ ] Mando físico muestra el dispositivo detectado o ausencia real.

## Regresión
- [ ] Smash Bros: stick arriba/abajo/izquierda/derecha correcto.
- [ ] DK64: sin ghosting/regresión visual.
- [ ] Mario Kart 2P: P1 sin borde de P2.
- [ ] Mover botones persiste tras reiniciar.
- [ ] Salir/minimizar/reabrir sin crash.
