# RetroLink — Rediseño integral de UI/UX

**Estado:** propuesta de arquitectura y diseño para implementación; no es una nueva compilación.
**Base inspeccionada:** `codex/v1.6-library-hub`, commit `6b543a5564ad85064a5c1f3e0643b0a87721d15a`.
**Referencias:** capturas reales de Biblioteca unificada y biblioteca PSP, y conceptual oscuro/rojo aportado por el usuario.
**Decisión central:** una biblioteca, una navegación y una identidad visual. Reemplazar las pantallas de gestión anteriores, no colocar otra portada delante de ellas. Conservar los servicios, repositorios, formatos, guardados y ejecutores de emulación.

## A. Diagnóstico del diseño actual

| Área | Evidencia observada | Corrección propuesta |
|---|---|---|
| Navegación | `chooseImportPlatform()` abre la biblioteca antigua y pide volver a pulsar Importar. Una pulsación larga también abre esa biblioteca. | Importador único dentro de la nueva experiencia; ninguna biblioteca anterior en el recorrido habitual. |
| Jerarquía | Título, contador, búsqueda, filtros y gestión ocupan mucho espacio antes de los juegos. | Encabezado y herramientas compactos; catálogo como protagonista. |
| Legibilidad | Negritas extendidas y títulos largos con región/idiomas dentro del título. | Tipografía por roles y separación entre título de presentación y metadatos. |
| Distribución | Carátulas pequeñas centradas en tarjetas anchas. El componente actual fija la imagen a 66 dp de alto. | Componente de carátula proporcional, con variantes para cuadrícula y poca altura. |
| Consistencia | El hub oscuro/rojo conduce a pantallas azul/violeta con otra navegación. | Un solo sistema de componentes para todas las pantallas administradas por RetroLink. |
| Foco | Añadir y Gestionar/importar duplican funciones; se exponen detalles del núcleo junto a acciones habituales. | Un único punto de importación por contexto y herramientas avanzadas en menú secundario. |
| Saturación | Bordes en casi todos los controles y numerosos paneles informativos. | Separación principalmente por espacio, tipografía y tono de superficie. |

El problema no consiste solo en una paleta equivocada. `HubCatalog` vincula cada plataforma con las actividades de biblioteca y sala anteriores; `RetroHubActivity` las abre desde importación, pulsación larga y multijugador. `SettingsActivity` conserva además navegación hacia Biblioteca y Host de N64. La transición entre dos estructuras distintas está en el código.

Las capturas muestran acciones situadas fuera de la primera zona visible; por sí solas no demuestran que sea imposible acceder a ellas mediante desplazamiento. Tampoco permiten identificar con certeza el tamaño de fuente configurado ni el modelo del teléfono. No se debe atribuir el problema al usuario ni resolverlo encogiendo todo.

## B. Dirección visual global

**Concepto:** biblioteca de juegos premium, oscura y sobria, con acento arcade. Las carátulas aportan riqueza visual; los controles aportan claridad.

Se conserva el ícono aprobado del mando mitad pixelado/mitad moderno. En navegación se usa pequeño, sin una gran cabecera lateral. La marca RetroLink aparece una vez por pantalla. Negro, grafito, texto claro y rojo controlado sustituyen a la combinación de cajas neón.

El conceptual sirve como dirección estética, no como especificación funcional. No incorporar Wii/GameCube, nombres de teléfonos, tiempos jugados, códigos de sala o estados «Compatible/Excelente» solo porque aparezcan en esa imagen. Las plataformas inspeccionadas son PSP/PPSSPP, PS1/PCSX-ReARMed, N64/Mupen64Plus-Next, SNES/Snes9x, Atari 2600/Stella 2014 y GB/GBC/Gambatte.

La composición horizontal de teléfono es el caso prioritario de validación. El mismo sistema deberá adaptarse a ventanas verticales y grandes, sin modificar la orientación ni el ciclo de vida de una emulación en marcha.

## C. Sistema de diseño base

### Navegación

Cuatro destinos globales, siempre con el mismo orden: **Inicio · Biblioteca · Multijugador · Ajustes**. Una consola es un filtro/contexto de Biblioteca, no una segunda app ni un quinto destino global.

Propuesta de política adaptativa:

- Ventana compacta y alta: barra inferior.
- Ventana horizontal de teléfono: barra lateral estrecha, 80 dp de referencia, con icono y etiqueta; no el panel ancho con marca grande de v1.6.
- Ventana amplia y suficientemente alta: la misma barra lateral y posibilidad de catálogo/detalle simultáneos.

La decisión usa ancho y alto de la ventana; el espacio de contenido se obtiene además descontando insets y navegación. No se decide por marca o tamaño físico del teléfono. El umbral propuesto para mostrar catálogo y detalle simultáneamente es ancho de ventana >=1000 dp y alto >=600 dp, sujeto al espacio real y escala de fuente; no es un requisito de Android.

En ventanas de altura extrema se elimina primero la marca decorativa. Si los cuatro destinos no caben con sus áreas táctiles, se ofrece un menú de navegación en la barra superior. No reducir los blancos táctiles para forzar su encaje.

### Tipografía

Familia sans-serif del sistema. Texto en sp, dimensiones de layout en dp. No añadir una fuente de descarga para este incremento.

| Rol | Tamaño / interlínea propuestos | Uso |
|---|---|---|
| Título de pantalla | 24 / 30 sp, semibold | Inicio, Biblioteca, PSP |
| Título compacto | 20 / 26 sp, semibold | Teléfono horizontal |
| Sección | 18 / 24 sp, semibold | Plataformas, Últimas aperturas |
| Título de juego | 15 / 20 sp, medium | Dos líneas en tarjeta; completo en detalle |
| Cuerpo | 14 / 20 sp, regular | Descripciones breves |
| Metadatos | 12 / 16 sp, regular | Plataforma, formato, región |
| Acción | 14 / 20 sp, medium | Jugar, Añadir, Crear sala |

Mayúsculas reservadas para siglas. No usar tipografía pixelada en información funcional. Validar escalas de fuente 1.0, 1.3 y 2.0; a mayor escala, disminuir columnas o pasar a lista, no reducir el texto esencial.

### Paleta semántica

| Token | Color | Uso |
|---|---|---|
| background | `#0B0D12` | Fondo |
| surface | `#141820` | Tarjetas y grupos |
| surfaceRaised | `#1C222D` | Hojas y diálogos |
| textPrimary | `#F5F7FA` | Texto principal |
| textSecondary | `#A7AFBD` | Información secundaria |
| primary | `#C62D44` | Acción principal con texto blanco |
| accent | `#FF6176` | Foco y selección |
| outlineDecorative | `#2A313E` | Separación no esencial |
| outlineEssential | `#7D8798` | Indicadores que necesitan contraste |
| success / warning / error | `#45D59A` / `#F2BE55` / `#FF8491` | Estados con icono y texto |

Contraste objetivo: texto normal >=4.5:1, texto grande >=3:1 e indicadores esenciales >=3:1. Los bordes decorativos no son el único indicador de una acción o un estado. Se calcularon pares planos de esta paleta: blanco sobre primary 5.44:1; textSecondary sobre surface 8.05:1; accent sobre surface 6.11:1. No equivale a validar el contraste de toda una pantalla: las imágenes, transparencias y estados deben comprobarse por separado.

### Geometría y componentes

Escala de espaciado: 4, 8, 12, 16, 24 y 32 dp. Márgenes de 16 dp en teléfono y 24 dp en ventana amplia; separaciones de tarjetas de 12 dp. Radios de 12 dp para tarjetas y 20 dp para hojas de detalle. Son valores de partida, no alturas rígidas.

Blancos táctiles mínimos de 48×48 dp. Un chip puede verse más pequeño, siempre que su zona de interacción alcance ese tamaño y no se superponga a la de otro. Los botones primarios usan rojo sólido; los secundarios, superficie neutra o texto. No convertir todos los filtros en botones primarios.

Una acción principal dominante por pantalla. En un catálogo, cada tarjeta puede tener su acceso rápido a Jugar, pero sin una cuadrícula de grandes rectángulos rojos. Iconos vectoriales consistentes, de 24 dp visuales, con nombres accesibles. No usar emojis como sustituto de una familia de iconos.

### Estados y veracidad

Distinguir **selección**, **foco de mando/teclado**, **pulsación**, **deshabilitado**, **cargando** y **error**. El foco no equivale a seleccionar un juego ni provoca su ejecución.

La disponibilidad de un archivo, la presencia de un núcleo y la compatibilidad del juego son cosas distintas. «Motor incluido» solo acredita presencia; «Archivo disponible» solo lectura; «Probado» exige una evidencia con juego, núcleo, versión y entorno. En ausencia de prueba, usar «Sin evaluar», nunca un check verde de compatibilidad. No inferir restauración de partida porque exista una carpeta de guardados.

## D. Rediseño por pantalla

### 1. Inicio

Orden: encabezado compacto; tarjeta de última apertura; plataformas; recientes; acceso discreto a crear/unirse a multijugador.

La tarjeta destacada usa carátula/arte local, nombre del juego, consola y **Jugar de nuevo**. «Continuar» queda reservado para un estado de reanudación realmente restaurable. No inventar tiempo jugado ni historial anterior. Excluir el juego destacado de la fila de recientes para evitar duplicación.

Plataformas: fila horizontal de icono, nombre corto y número real de juegos. Tocar PSP abre la misma Biblioteca filtrada por PSP. Si la biblioteca está vacía, reemplazar el destacado por «Añade tu primer juego» con un único CTA. No mostrar paneles de rendimiento global, versión del motor ni slogans largos entre el usuario y sus juegos.

En teléfono horizontal, el destacado se vuelve una composición compacta con carátula a la izquierda y texto/acción a la derecha. No debe convertirse en un banner que obligue a desplazarse para llegar a las plataformas.

### 2. Biblioteca

Encabezado con título, contador discreto y un único **Añadir**. Búsqueda por nombre, selector de consola y Favoritos como filtro. **Recientes es un criterio de orden**, no otra biblioteca: «Últimos abiertos». Los filtros activos se ven y se pueden quitar.

Con poca altura, búsqueda y herramientas se integran en dos bandas compactas como máximo. La búsqueda puede expandirse en la barra superior. No conservar simultáneamente títulos decorativos, contadores repetidos, filas de filtros altos y Gestión/importación.

Dos variantes del mismo componente:

- **PosterGameCard**, para suficiente altura: marco de carátula protagonista y proporcional; título de hasta dos líneas; consola; menú secundario y acceso rápido a Jugar.
- **CompactGameCard**, para teléfono horizontal: carátula de 80–96 dp de alto en un marco proporcionado a la izquierda; título/metadatos/acción a la derecha. Altura resultante mínima, con crecimiento por contenido, no fija.

El número de columnas se calcula con el ancho disponible y un ancho mínimo; no depende de tener dos juegos. No estirar dos tarjetas para rellenar toda la pantalla. No estirar ni recortar la información esencial de las portadas; utilizar FIT_CENTER dentro de un marco bien dimensionado, no una imagen diminuta dentro de una gran caja.

Tocar carátula/título abre Detalle. El botón Jugar lanza directamente. El menú de tres puntos hace visibles las acciones secundarias: no depender de una pulsación larga para acceder a gestión. El favorito sigue siendo una acción independiente que no inicia el juego.

Para nombres como «CTR - Crash Team Racing (USA)», presentar título y región por separado cuando la separación sea inequívoca. Conservar siempre el nombre original y el archivo; no renombrar, truncar internamente ni deduplicar discos por su título visual.

**Importación directa:** con consola seleccionada, Añadir abre el selector Android. En Todas las consolas, se pregunta primero la plataforma. Después se usa el importador existente, se muestra progreso real y se vuelve a la misma lista. ISO/CHD/PBP compartidos entre plataformas no se clasifican solo por extensión. PS1 mantiene agrupación CUE/BIN, M3U y selección múltiple; no convertir cada pista en un juego independiente.

### 3. Consola / plataforma

Es `Library(platformId)`, no una nueva biblioteca. Encabezado con icono pequeño, nombre corto y contador. Mismos filtros y tarjetas; una sola acción Añadir ya contextualizada.

En el menú secundario: ajustes de la consola, carátulas, información del núcleo y gestión de BIOS cuando corresponda. No mostrar en paralelo un gran encabezado de PSP, dos banners de estado y un panel permanente de juego seleccionado.

Un aviso persistente solo aparece cuando requiere acción. «Falta un archivo necesario» debe estar basado en una comprobación real; «3× interno» se muestra únicamente si procede de la configuración efectiva y en Ajustes/Detalle, no como texto promocional fijo.

El panel lateral de selección se habilita solo en la variante amplia/alta. En teléfonos se abre el detalle dentro de la misma navegación.

### 4. Juego seleccionado

Carátula, título completo, consola, formato y metadatos disponibles. Acción primaria **Jugar**. Acciones secundarias **Multijugador** cuando proceda, favorito y ajustes del juego compatibles con el backend. El archivo original y los detalles técnicos van en «Información».

En teléfono horizontal el detalle puede distribuir carátula y texto/acciones en dos columnas, sin mantener además el catálogo a un lado. En vertical usa una vista desplazable. La barra de acción conserva espacio reservado y respeta teclado, gestos y recortes de pantalla.

La posibilidad técnica de multijugador del núcleo no acredita que ese título tenga un modo multijugador. Si no hay metadatos fiables, se explica la condición antes de entrar a la sala.

Si el archivo desapareció: **Localizar archivo** o Volver. No enviar al usuario a una biblioteca antigua. Al iniciar: estado «Abriendo…», bloqueo de doble toque y recuperación explicada ante fallo. El registro de una apertura solicitada no debe convertirse automáticamente en una partida jugada con éxito.

### 5. Multijugador

Flujo común: **consola → Crear/Unirse → preparación de sesión**. La pantalla muestra solo la plataforma y modalidad seleccionadas, no seis grandes tarjetas con explicaciones repetidas.

La modalidad la determina el adaptador. En PS1/N64/SNES/Atari, distinguir anfitrión que ejecuta y teléfonos de control remoto. En PSP/GB Link, dispositivos que ejecutan su emulación. No ofrecer un selector que habilite una modalidad inexistente.

La sala incluye encabezado de juego/plataforma; anfitrión y participantes en filas compactas; estado real; configuración de control; una acción primaria dependiente del rol. El cliente de control remoto puede unirse sin una ROM local. En PSP se requiere su juego local compatible; no se exige igualdad de resolución o potencia gráfica.

**PSP actual:** `PspRoomActivity` usa IP local, entrada de IP del host y `PspLauncher.launchGame()` en modos HOST/CLIENT. Este código no demuestra un servicio de códigos de sesión ni un callback que confirme a todos los participantes dentro del juego. Por tanto, la primera UI conserva IP/copiar/pegar y muestra «Configuración de red preparada; entra al multijugador del juego». No dibujar «Jugador 2 conectado» a partir de pulsar Unirse.

Estados diferenciados: sin conexión, preparando, esperando, transporte conectado, sesión dentro del juego confirmada si existe señal, desconectado, error. Solo se muestran métricas disponibles. El código alfanumérico del conceptual se omite hasta que exista un mecanismo verificable de resolución; QR de configuración queda como ampliación, no como conectividad ya implementada.

Si el permiso se deniega, explicar para qué se requiere y permitir reintentar. Negarlo no bloquea la biblioteca ni el juego individual. Cambiar de pestaña no destruye la sesión; salir de una sala activa requiere confirmar su cierre.

### 6. Ajustes

Un solo árbol con filas de título, resumen y valor: **General; Imagen y rendimiento; Controles; Multijugador; Archivos y guardados; Por consola; Información y licencias**. No abrir una segunda pantalla de «configuración completa» con otra barra de navegación.

Los controles habituales incluyen valor actual, alcance y comportamiento: por ejemplo, «Aplicar al iniciar el próximo juego» cuando sea necesario. Conservar las claves de preferencias y su semántica; no reiniciar valores por una migración visual.

Adaptive Core se integra en Imagen y rendimiento, conservando sus modos existentes y su identificación como sistema de reglas. No atribuir IA, mejoras porcentuales o resolución efectiva sin evidencia. Los ajustes por juego se muestran solo donde el adaptador soporte ese alcance; no simular una precedencia global/consola/juego que el backend no implemente.

Los ajustes avanzados dibujados por PPSSPP pueden seguir abriendo su propia herramienta, mediante una acción explícita «Ajustes avanzados de PPSSPP» y con retorno al contexto previo. Es una excepción técnica delimitada, no otra biblioteca obligatoria. La navegación normal de importar, explorar y jugar no pasa por ella.

Restablecer solo la categoría elegida, con confirmación. No eliminar partidas ni ROM. Copia/restauración de guardados es un módulo a implementar y validar, no una función que se presupone disponible.

## E. Arquitectura de componentes reutilizables

### Decisión técnica

Mantener Java y Android Views/XML en este incremento. Proponer AndroidX Fragments/Navigation, ViewModel y RecyclerView con Material Components para Views, fijando versiones compatibles tras verificar el build. No introducir simultáneamente una migración completa a Compose y una nueva navegación.

**RetroShellActivity** sustituye al hub como raíz; no lo envuelve. Hospeda Inicio, Biblioteca, Detalle, Sala y Ajustes. Las actividades de ejecución de N64, PS1, SNES, Atari, GB/GBC y PPSSPP permanecen como frontera de ejecución, fuera del contenedor de navegación. No incrustar superficies de emulación dentro de tarjetas ni recrearlas al cambiar la interfaz.

### Componentes propuestos

| Componente | Responsabilidad |
|---|---|
| AppChrome / TopBar | Título, acción contextual, un único manejo de insets |
| AdaptiveNavigation | Barra inferior/lateral; mismos destinos y estado |
| LibraryScreen | Catálogo general y plataforma filtrada |
| GameCard / CoverFrame | Variantes compacta/póster y tratamiento proporcional |
| GameDetail | Información y acciones del juego |
| SearchFilters | Búsqueda, plataforma, favoritos, orden |
| ImportFlow | Selector Android, progreso, errores, retorno |
| SessionScreen / ParticipantRow | Una sala y estados proporcionados por adaptador |
| SettingRow / SettingsCategory | Valor, alcance, descripción y restauración |
| EmptyState / ErrorState / StatusBanner | Ausencia de datos y problemas que requieren acción |

### Separación de responsabilidades

`UI → ViewModel/estado → coordinadores/adaptadores → repositorios y motores actuales`.

`HubCatalog` conserva su trabajo útil de agregación, pero debe separar lectura de catálogo de navegación. Proponer CatalogRepository, ImportCoordinator, GameLaunchCoordinator, SessionCoordinator y SettingsRepository. Los nombres son contratos del diseño; no archivos ya implementados.

Extraer de las Activities antiguas la preparación de importación, gestión y sesión antes de retirar sus pantallas. Conservar las mismas rutas de archivo, flags de lanzamiento, guardados y protocolos. No basta con ocultar una Activity y dejar sus acciones apuntando a otras pantallas antiguas.

El ciclo de vida de conexiones no pertenece a la vista. Extraer la lógica que hoy dependa de una Activity con tests de recreación, desconexión y cierre. ViewModel no debe retener Activity ni Views. La actividad iniciada para emular devuelve al mismo filtro, consulta y desplazamiento.

Para fluidez: RecyclerView/ListAdapter con listas inmutables y actualizaciones diferenciales; imágenes reducidas al tamaño de visualización; caché acotada; cancelación de tareas de carátulas al reciclar; lectura e importación fuera del hilo principal. No reconstruir la pantalla completa al marcar un favorito. No ejecutar animaciones perpetuas ni decodificación del hub durante una partida.

La cancelación de importación se implementará con límites explícitos: archivos temporales propios y commit final validado cuando el repositorio lo permita. No prometer cancelación instantánea de un importador síncrono existente ni borrar archivos anteriores al cancelar. El indicador usa porcentaje solo cuando el total es conocido.

### Datos de interfaz

GameUiModel: identidad estable, platformId, título mostrado y original, metadatos conocidos, carátula local, favorito, última apertura, estado de archivo, disponibilidad de motor y evidencia de prueba opcional.

LibraryUiState: plataforma, consulta, favoritos, orden, selección, ancla de desplazamiento, estado de carga y errores recuperables.

SessionUiState: modalidad real, rol, endpoint real, participantes confirmados, estado de transporte, estado de sesión del juego si está disponible y acción permitida. No serializar conexiones activas como si fueran una preferencia.

## F. Flujo y navegación

**Jugar:** Inicio → Biblioteca filtrada o Detalle → ejecutor existente → regresar al origen.

**Importar:** Añadir → plataforma solo cuando falte contexto → selector Android → validación/importación → misma Biblioteca con el resultado visible.

**Multijugador:** Crear/Unirse → plataforma cuando falte → sala única → juego/controles existentes.

**Ajustes:** categoría → ajuste con alcance → volver a categoría, sin abrir otro inicio.

Mantener pilas/estado de los cuatro destinos. Cambiar de pestaña no borra búsqueda, filtro, posición ni sala. Volver desde Detalle conserva el catálogo; Volver durante búsqueda cierra primero teclado; Volver en diálogo cierra diálogo; Volver en sala activa pide confirmación. Durante juego, Volver pertenece al flujo de pausa/salida del ejecutor, no a la navegación de biblioteca.

Sistema Android: gestionar barras, recortes y teclado mediante insets una sola vez por contenedor; reservar espacio real para acciones fijas. Pantalla inmersiva durante juego cuando corresponda, no ocultar controles del sistema como parche a un layout que no cabe. Validar TalkBack, D-pad, mando y teclado; diferenciar foco de selección y proporcionar alternativas a gestos.

## G. Prioridad de implementación y aceptación

| Etapa | Alcance | Criterio de cierre |
|---|---|---|
| 1. Estructura | Raíz única, rutas, estado y extracción de importación/sesión | Ningún recorrido común abre bibliotecas antiguas |
| 2. Sistema visual | Tokens, tipografía, componentes, insets | Componentes iguales con variantes adaptativas y accesibles |
| 3. Pantallas críticas | Inicio, Biblioteca, plataforma, detalle y flujo de importación; luego Sala/Ajustes | Recorridos completos coherentes, no solo portada nueva |
| 4. Refinamiento | Carátulas, listas grandes, errores, progresos, mando, títulos largos | Sin saltos de layout, bloqueos ni acciones ocultas |
| 5. Consistencia final | Todas las plataformas, preferencias, guardados, regresión y firma | Evidencia automática + teléfonos reales + actualización segura |

El primer incremento funcional debe incluir **Biblioteca única + filtro por plataforma + importación directa + detalle + retorno correcto** para las seis plataformas. No aprobar un APK que vuelva a abrir `PspLibraryActivity`, `Ps1LibraryActivity` o equivalentes desde Añadir/Gestionar como solución provisional.

Las otras pantallas pueden migrarse en incrementos sucesivos, pero su estado incompleto debe declararse. El retorno clásico, si se conserva como medida de recuperación durante desarrollo, estará en herramientas de diagnóstico; no será un destino normal de la nueva aplicación.

### Pruebas necesarias

Automáticas: pruebas de filtros/orden/favoritos; contratos de launch y extras por plataforma; importación/cancelación/error/discos múltiples; estado de navegación; selección de juego preservada al crear sala; cobertura de accesibilidad y capturas de pantallas vacías y pobladas. Reutilizar las pruebas anteriores como base, no como evidencia de que la nueva interfaz se ve bien.

En dispositivo: horizontal y vertical; altura reducida; escala de fuente 1.0/1.3/2.0; navegación por gestos/tres botones; biblioteca de 0, 2 y muchos juegos; imágenes ausentes y nombres largos; audio/controles/guardados; abrir-cerrar-reabrir cada consola; mando P2 con imagen y botones; PSP Ad Hoc con dos teléfonos.

Medición de UI: arranque hasta contenido utilizable, respuesta visual al toque, tiempo de búsqueda, p50/p95/p99 de presentación durante scroll y memoria de imágenes, en condiciones equivalentes. Objetivo inicial: feedback visible en menos de 100 ms y ausencia de bloqueos de I/O en el hilo principal. Son objetivos, no resultados medidos. Separar pruebas del frontend de velocidad de emulación y conexión.

No se identifica un teléfono probado a partir de estas imágenes. No hay una prueba nueva de juegos ni un APK nuevo producido por este documento.

### Puerta de distribución

Antes de entregar una actualización, usar una clave persistente y verificar el certificado contra el APK efectivamente instalado. Tener un APK compilado no resuelve la continuidad de firma. No recomendar desinstalar o borrar datos para forzar la actualización. Si falta la clave original, tratar recuperación/migración de datos como trabajo separado y confirmado.

## Referencias verificadas

Proyecto inspeccionado en GitHub, commit indicado: `HubCatalog.java`, `RetroHubActivity.java`, `SettingsActivity.java`, `PspRoomActivity.java`. En particular: rutas a bibliotecas anteriores, imagen de 66 dp, delegación a salas anteriores, ajustes y PSP mediante IP.

Documentación oficial consultada (no se incorporan dependencias por este documento):

- Android, window size classes: `https://developer.android.com/develop/ui/views/layout/use-window-size-classes`
- Android, layouts y navegación: `https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns`
- Android, accesibilidad: `https://developer.android.com/guide/topics/ui/accessibility/apps`
- W3C, contraste no textual: `https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html`
- Android, RecyclerView: `https://developer.android.com/develop/ui/views/layout/recyclerview`
- Android, ListAdapter: `https://developer.android.com/reference/kotlin/androidx/recyclerview/widget/ListAdapter`
- Android, pilas de navegación: `https://developer.android.com/guide/navigation/backstack/multi-back-stacks`
- Android, insets y edge-to-edge: `https://developer.android.com/develop/ui/views/layout/edge-to-edge`

## Estado de esta entrega

Se entregan diagnóstico, arquitectura objetivo, especificación visual, flujos, componentes, tokens y criterios de aceptación. No se ha modificado el repositorio, iniciado una compilación ni producido un APK para este rediseño.
