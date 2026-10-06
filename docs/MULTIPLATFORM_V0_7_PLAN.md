# RetroLink v0.7.0 — MultiPlatform Foundation

La siguiente versión mayor comienza la transición de RetroLink desde un frontend N64 específico a una arquitectura multicore.

## Sistemas objetivo iniciales
1. Nintendo 64 — Mupen64Plus-Next (baseline validada, no romper).
2. Game Boy / Game Boy Color — Gambatte.
3. SNES — Snes9x.
4. Atari 2600 — Stella.
5. PlayStation 1 — PCSX-ReARMed inicialmente.

## Refactor mínimo necesario
- `SystemRegistry`: sistema, extensiones, core, nombre visible, controles y requisitos.
- `CoreRegistry`: dejar de exponer solo N64 y permitir múltiples cores libretro.
- Carga dinámica de librerías core en lugar de `System.loadLibrary("retro_n64")` fijo.
- `EmulatorActivity` común con `systemId`, `coreId`, `contentPath` y `playerCount`.
- Biblioteca con metadata por sistema y filtros solo para cores instalados/operativos.
- Layout de controles por sistema usando el editor ya construido.
- Directorios separados de saves/system por plataforma.
- Mantener PresentSync como frontend común, leyendo FPS del core.

## Orden de implementación
### v0.7.0
Arquitectura multicore + N64 funcionando sobre la nueva capa.

### v0.7.1
Game Boy/GBC + SNES.

### v0.7.2
Atari 2600.

### v0.8.0
PlayStation 1: BIOS del usuario, BIN/CUE/CHD, memory cards y controles PS1.

## Regla
No mostrar una plataforma en la Biblioteca hasta que su core realmente compile y pueda ejecutar contenido en RetroLink.
