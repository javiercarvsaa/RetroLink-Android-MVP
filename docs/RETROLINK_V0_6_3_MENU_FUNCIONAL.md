# RetroLink v0.6.3 RC1 — Menú funcional

## Objetivo
Aproximar Inicio, Biblioteca, Sala, Ajustes y Perfil al dashboard visual aprobado, manteniendo como regla que cada control visible tenga una función real o represente un estado real.

## Cambios

### Inicio
- Hero de última sesión con juego realmente seleccionado.
- Conteo real de ROM N64 importadas.
- Estado real de gamepad/Bluetooth.
- Accesos separados: Jugar ahora, Biblioteca, Host local, Unirse, Controles y Ajustes.

### Biblioteca
- Se deja de representar una única ROM como si fuera una biblioteca.
- `N64RomRepository` mantiene múltiples ROM N64 dentro del almacenamiento privado de RetroLink.
- El grid se construye desde archivos realmente presentes y con cabecera N64 válida.
- Seleccionar una tarjeta define el juego activo utilizado por Inicio y Sala.
- Solo N64 aparece como plataforma disponible en esta versión.

### Sala
- Distribución visual P1/P2 — juego — P3/P4 similar al diseño objetivo.
- Tarjetas de jugador conectadas a `SessionState`.
- Estado de red conectado a IP/puerto y conteo de clientes reales.
- Selector 1P-4P, activar/detener sala, cambiar juego, probar controles e iniciar partida son acciones reales.

### Ajustes
- Mantiene únicamente controles conectados a preferencias o subsistemas activos.
- PresentSync aparece como estado, no como switch falso.
- Restablecer elimina únicamente preferencias de usuario, no la biblioteca.

### Perfil
- Modelo Android, juegos importados, última ROM, core N64 y gamepad son datos obtenidos en tiempo real.

## Fuera de alcance
No se muestran como operativos SNES, PS1, Game Boy, Atari, favoritos, búsqueda, temperatura, latencia simulada, códigos de sala ficticios ni estadísticas de tiempo de juego que todavía no se registran.
