# RetroLink 1.6.0 RC1 — Inicio y biblioteca unificada

## Base y alcance
Base fijada: `9379168238fbc3d78fc284744536c95a0ef88718`, rama `codex/v1.5-adaptive-optimizer`.
Es la base de código v1.5 compilada. No se presupone que haya sido validada en un teléfono.
Incremento nuevo: UI nativa Android horizontal. No se reintegran núcleos ni se cambian ROM, BIOS, guardados, renderizadores, opciones adaptativas o transportes multijugador.

## Cambios implementados
- Nuevo `RetroHubActivity`: Inicio, Biblioteca, Multijugador y Ajustes.
- Registro de adaptadores en `HubCatalog`, sin inferir PS1/PSP a partir de extensiones ambiguas.
- Lectura de las seis bibliotecas existentes en un hilo de trabajo; no se migran ni copian juegos.
- Cuadrícula reciclada, búsqueda con tolerancia a tildes, favoritos locales y aperturas recientes.
- El historial nuevo se registra cuando Android acepta un inicio desde el hub. No certifica que el núcleo llegue al primer fotograma, no reconstruye un historial previo y no crea ni restaura save states.
- Inicio directo de juegos con los mismos repositorios, actividades y extras usados por las bibliotecas anteriores.
- Añadir/gestionar abre la biblioteca clásica de la consola elegida. Los selectores e importadores existentes no fueron reescritos.
- Lectura de carátulas ya cacheadas, decodificación reducida en segundo plano, caché de 4 MiB y cola limitada a 24 tareas. No se consultan servicios de carátulas desde el hub. Las pantallas clásicas mantienen su comportamiento previo.
- Scroll de plataformas, columnas automáticas, estado de búsqueda guardado, versión compacta en pantallas bajas y objetivos táctiles mínimos de 48 dp. El botón Atrás vuelve al inicio; se registra la API de retroceso de Android 13+ sin alterar las actividades clásicas.
- Ícono aprobado por el usuario: mitad izquierda pixelada y mitad derecha moderna. Se incluyen recursos de lanzador Android y una versión pequeña para el hub; no se agregan fuentes.
- Ajustes permite abrir el inicio clásico durante la sesión. La pantalla antigua se conserva íntegra. La próxima apertura de la aplicación vuelve al hub.

## Matriz real de rutas
| Plataforma | Núcleo existente | Biblioteca | Inicio individual | Sala conservada |
|---|---|---|---|---|
| PSP | PPSSPP 1.20.4 | PspLibraryActivity | PspLauncher, Mode.SINGLE | PspRoomActivity, Ad Hoc |
| PS1 | PCSX-ReARMed | Ps1LibraryActivity | Ps1GameActivity, host=false | Ps1RoomActivity |
| N64 | Mupen64Plus-Next | LibraryActivity | IntegratedN64Activity, 1 jugador | HostActivity / ControllerActivity |
| SNES | Snes9x | SnesLibraryActivity | SnesGameActivity, host=false | SnesRoomActivity |
| Atari 2600 | Stella 2014 | Atari2600LibraryActivity | Atari2600GameActivity, host=false | Atari2600RoomActivity |
| GB/GBC | Gambatte | GameBoyLibraryActivity | IntegratedGameActivity, core GB | GameBoyLinkActivity |

No se muestran Wii ni GameCube como plataformas disponibles. Los juegos 2D/3D conservan sus opciones previas; no hay filtros ni modelos nuevos.

## Validaciones locales realizadas
1. 38/38 comprobaciones JVM del modelo de biblioteca (identidad, títulos, búsqueda, favoritos, recencia, orden, 1200 entradas).
2. Análisis sintáctico Java 17 de cinco archivos nuevos; no equivale a compilación Android.
3. Parche aplicado a copias exactas de los dos archivos de configuración inspeccionados, verificadas contra sus hashes Git originales.
4. XML de los recursos nuevos, integridad del ZIP y sintaxis del workflow y scripts.
5. 77 contratos de métodos, campos y actividades contrastados con el código inspeccionado y los símbolos del APK v1.5.0d existente.

El workflow añade barreras reales: verificación de archivos originales contra el commit fijo, precompilación Java completa de Android antes de los núcleos, enlace de recursos, ensamblado, firma, integridad APK y presencia de bibliotecas nativas.
Ninguna comprobación de tokens o hashes sustituye una prueba de carga de juegos, mandos P2, audio o guardados.

## Firma e instalación: pendiente importante
Los APK anteriores recibidos en esta conversación tienen certificados distintos:
- v1.4.0d: `1a870dd02005ac3b0f98526dce106d20876310eddd6f88b2ea86c4e5e0feb36e`
- v1.5.0d: `ee2728a92ffe08ace0c8663fb948d25fb5d03866a2e4c500e46fdd339887723c`

No se dispone aquí de sus claves privadas. La continuidad de firma NO está resuelta simplemente por aumentar versionCode. El workflow registra la firma del nuevo APK y compara con ambas referencias; no lo presenta automáticamente como actualización compatible.
Opcionalmente puede recibir un keystore de depuración existente mediante el secreto `RETROLINK_DEBUG_KEYSTORE_BASE64` (alias/password estándar Android). No se publica ni almacena ese keystore en el repositorio o en los artefactos. Un secreto ausente no se sustituye por una clave que se finja compatible.
No desinstalar la aplicación anterior ni borrar datos para forzar una actualización. Si la firma difiere, revisar primero la clave/migración de datos; una instalación en un equipo de pruebas vacío es un caso distinto.

## Pendiente después de compilar
- Verificar certificado e instalación sin desinstalar la versión conservada.
- Pantallas reales a 640×360 dp y 800×360 dp, una tableta, y texto ampliado.
- Abrir un juego de cada sistema, volver al hub, relanzar y confirmar audio/controles/guardados.
- Confirmar que PSP y PS1 con nombres ISO iguales nunca se cruzan.
- Salas remotas N64/PS1/SNES/Atari: jugador 2, botones, ejes e imagen. PSP: dos emulaciones y su sala Ad Hoc. GB/GBC: sala Link existente.
- Cancelar permisos Bluetooth, habilitarlos, volver del sistema, comprobar que P2 no se convierte en host.
- Reanudar tras salir a otra aplicación; filtro y posición de cuadrícula.
- Comparación sostenida A/B del juego idéntico frente a v1.5, misma temperatura inicial, modo, ROM y escena. No se afirma mejora de FPS, latencia, temperatura ni consumo en este incremento de UI.

Dispositivos físicos probados con v1.6 en esta sesión: ninguno.

## Compilación y reversibilidad
El workflow se sube a `.github/workflows/build-retrolink-v1.6.0-library-hub.yml` en main.
Compila desde el SHA fijado y guarda el código en `codex/v1.6-library-hub` sin force-push y sin modificar la rama v1.5. Si la rama destino contiene otro árbol, se detiene sin sobrescribirlo.
Las funciones existentes siguen en sus archivos originales; el validador compara su contenido con la base.
La reversión del código es descartar la rama v1.6 o revertir su commit. Esto NO implica recomendar instalar APK antiguos sobre nuevos ni desinstalar aplicaciones.

## Referencias técnicas consultadas
- Android Developers: Build responsive navigation (Views).
  https://developer.android.com/develop/ui/views/layout/build-responsive-navigation
- Android Developers: Accessibility / Material Design touch targets.
  https://developer.android.com/guide/topics/ui/accessibility/apps
  https://m1.material.io/usability/accessibility.html
- Android Developers: Sign your app, signing considerations.
  https://developer.android.com/studio/publish/app-signing

Sin nuevas dependencias externas de UI o IA. Se conservan los avisos y licencias de los núcleos incluidos en la base. La revisión general de distribución/licencias del proyecto no se sustituye por este cambio visual.
