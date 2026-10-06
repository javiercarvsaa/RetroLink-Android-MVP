# RetroLink v0.6.2 RC1 — Functional UI

Objetivo: acercar Inicio, Biblioteca, Sala, Ajustes y Perfil a la referencia visual premium de RetroLink sin mostrar funciones simuladas.

## Regla de producto
Toda opción visible debe cumplir una de estas condiciones:
1. ejecuta una acción real de RetroLink;
2. modifica un ajuste que el motor usa realmente; o
3. muestra un estado leído desde la aplicación/dispositivo.

No se muestran filtros, favoritos, plataformas, códigos de sala, ping, temperaturas, dispositivos en red ni switches que no tengan implementación real.

## Cambios
- Inicio: Continuar abre directamente la última ROM real en 1P; Jugar ahora abre Biblioteca con selector; Host y Unirse siguen rutas BLE/Wi-Fi reales; Controles y Ajustes son accesos directos reales.
- Biblioteca: solo Nintendo 64 mientras sea el único core validado. Selector de ROM integrado, Jugar 1P directo, Host local y prueba de controles. Se eliminan Favoritos, búsqueda y filtros decorativos.
- Sala: se elimina código RL7K3 y el control de vista demo. Se muestran IP/puerto/clientes reales, P1-P4 reales, selección explícita 1-4 jugadores, activar/detener sala y lanzar partida.
- Ajustes: solo Video, Streaming, Control táctil, Control físico y Multijugador conectados a preferencias/servidor/input reales. FPS de streaming y calidad JPEG ahora son controles directos. Restablecer es funcional.
- Perfil: capacidades y mando físico se informan dinámicamente; se elimina el falso check de control físico.
- ROM N64: importación centralizada en `N64RomRepository` para evitar rutas duplicadas entre Biblioteca y Sala.

## No incluido todavía
SNES, PS1, Game Boy, Atari, favoritos, buscador de biblioteca, carátulas automáticas, código de sala, ping, telemetría térmica y temas visuales seleccionables. Esas opciones solo aparecerán cuando exista implementación real.
