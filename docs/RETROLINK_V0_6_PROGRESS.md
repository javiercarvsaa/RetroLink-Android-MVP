# RetroLink v0.6.0 — Progress

## Baseline
- Base: RetroLink v0.5.1 — PresentSync + Clean HUD.
- Core N64 fijado: Mupen64Plus-Next SHA `12edd2c74a517ff86dfa8cfc71ad75e4c10486d5`.
- Donkey Kong 64: corrección de ghosting/persistencia validada en dispositivo y debe preservarse.

## RC1 — Implementado en working copy

### UI / Navegación
- Inicio, Biblioteca y Sala conservan el dashboard neón ya preparado.
- Nueva pantalla Perfil con dispositivo, última partida y acceso al editor de controles.
- Navegación inferior unificada en Inicio, Biblioteca, Sala, Perfil y Ajustes.

### UI / Ajustes
- Se mantiene la línea visual neón existente en Inicio, Biblioteca, Sala y Ajustes.
- Ajustes deja de depender de una fila rígida de 238 dp.
- Las tarjetas de Ajustes crecen según contenido y la pantalla conserva scroll vertical.
- Nuevo acceso `EDITAR POSICIONES`.
- Nuevo ajuste `SPLIT: AUTOMÁTICO / ESTÁNDAR / RECORTE SUAVE / RECORTE REFORZADO`.

### Editor de controles táctiles
- Nueva `ControlLayoutActivity`.
- Arrastrar joystick, A, B, Z, L, R, START y C-buttons.
- Tamaño individual 55–165%.
- Opacidad individual 25–100%.
- Inversión horizontal rápida para distribución zurdo/diestro.
- Guardado con coordenadas normalizadas independientes de resolución.
- Restauración a layout predeterminado.
- Layout aplicado automáticamente al volver al juego.

### Split-screen / Mario Kart 64
- Se agrega contenedor `emulatorCrop` 4:3 con clipping real.
- RACE P1 ya no depende solo de escalar el SurfaceView dentro de un viewport de pantalla completa.
- 2P: P1 toma la mitad superior y el resto queda fuera del clip.
- 3P/4P: P1 toma el cuadrante superior izquierdo y el resto queda fuera del clip.
- Overscan configurable para ocultar seams sin deformar la imagen.

### Estabilidad / cierre
- Nuevo shutdown bloqueante e idempotente del core.
- El cierre normal intenta descargar el core antes de pausar el hilo GL.
- `nativeShutdown()` protegido contra dobles llamadas.
- Etapas de teardown registradas en `nativeGetStage()`.
- Captura PixelCopy no se inicia cuando la Activity está terminando/destruida.

### Versionado
- versionCode: 15
- versionName: `0.6.0-rc1`

## Validaciones estructurales locales
- XML: PASS (35 archivos + Manifest, sin errores de parseo).
- Referencias `R.id`: PASS (0 referencias faltantes).
- Balance de llaves Java/C++: PASS en chequeo estructural.

### CI/CD normalizado
- Nuevo workflow único `.github/workflows/build-retrolink.yml` para repositorio con source normal.
- Ya no necesita payload Base64 cuando la migración se publique.

## Pendiente de CI
- Gradle/Android compile.
- CMake/NDK compile.
- Compilación del core N64.
- Validación de APK y librerías arm64.

## Pendiente de validación física
1. Donkey Kong 64: confirmar que PresentSync/ghosting siguen corregidos.
2. Mario Kart 64 P1–P3/P4: confirmar que no aparece franja de P2.
3. Editor: mover, guardar, cerrar y reabrir.
4. Ajustes: confirmar que ningún bloque queda inaccesible.
5. Entrar/salir del emulador varias veces y verificar que Android no muestre el aviso de cierre por error.

## Bloqueo de publicación actual
La sesión puede leer el repositorio GitHub, pero las operaciones de escritura (`create_branch`, `create_file`) responden HTTP 403 `Resource not accessible by integration`. La working copy RC1 queda preparada para publicar en cuanto el conector tenga escritura efectiva sobre `javiercarvsaa/RetroLink-Android-MVP`.

## v0.6.1 RC1 — Input Foundation

- Corregida doble inversión del eje Y analógico que afectaba arriba/abajo en Smash Bros.
- VirtualStick v2: deadzone radial, sensibilidad, curva suave, multitouch y visual neón.
- Soporte Android GAMEPAD/JOYSTICK HID para mandos Bluetooth y USB.
- Stick izquierdo físico -> stick N64; D-pad -> D-pad; stick derecho -> C-buttons.
- A/B, L1/R1, L2/R2, Start mapeados a controles N64.
- Ajustes de deadzone, sensibilidad e inversión Y.
- Editor de posiciones v0.6 preservado y compatible.
- Pendiente validación física: Smash Bros arriba/abajo, mando BT/USB y multitouch.

## v0.6.2 RC1 — Functional UI

- Auditoría de menú/submenús: se retiraron Favoritos, búsqueda, sistemas no integrados, código de sala y métricas simuladas.
- Inicio, Biblioteca, Sala, Ajustes y Perfil muestran solo acciones/estados conectados a código real.
- Streaming, input, recorte P1 y reset de preferencias expuestos como controles reales.

## v0.6.3 RC1 — Menu Functional Visual

- Rediseño visual siguiendo el dashboard RetroLink aprobado.
- Inicio con hero de última sesión y accesos separados por función.
- Biblioteca N64 real con múltiples ROM importadas y selección persistente.
- Sala reorganizada P1/P2 — juego — P3/P4, con red y jugadores reales.
- Perfil renovado con dispositivo, biblioteca, core y gamepad reales.
- Ajustes conserva únicamente opciones funcionales.
- versionCode 18 / versionName 0.6.3-rc1.

## v0.6.4 RC1 — Automatic Cover Search
- Búsqueda automática de carátulas N64 desde Libretro Thumbnails/Named_Boxarts.
- Índice remoto cacheado 7 días y carátulas cacheadas localmente.
- Matching por nombre original, cabecera, región y similitud; fallback sin bloquear el juego.
- Biblioteca visual con carátulas y detalle seleccionado.
- Selector 1P–4P corregido para evitar recortes.
- Perfil simplificado; gamepads virtuales excluidos del resumen físico.
- versionCode 19 / versionName 0.6.4-rc1.
