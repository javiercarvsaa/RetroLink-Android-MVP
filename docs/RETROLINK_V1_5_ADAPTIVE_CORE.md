# RetroLink v1.5 — Adaptive Core RC1

## Scope

This increment is rule-based and reversible. It does **not** bundle a neural model and it does not label conventional filters as AI.

## Confirmed cores

| Platform | Core / engine | Initial adaptive treatment |
|---|---|---|
| Nintendo 64 | Mupen64Plus-Next | Startup render profile, ADPF hints, timing/thermal calibration |
| PlayStation | PCSX-ReARMed | Safe 2× selection, spatial filter, opt-in temporal path, timing/thermal calibration |
| PSP | PPSSPP 1.20.4 | 1×/2×/3× internal resolution, anisotropy/depth policy, Ad Hoc protection |
| SNES | Snes9x | Faithful pixel output, no blur, timing/thermal observation |
| Atari 2600 | Stella 2014 | Faithful pixel output, no blur, timing/thermal observation |

## Safety priorities

1. Compatibility and no crashes.
2. Original emulation speed and sustained stability.
3. Input and multiplayer latency.
4. Thermal/power behavior.
5. Graphics quality.

The module does not alter ROM loading, BIOS handling, save paths, input mappings, audio timing or multiplayer transport.

## Modes

Automatic, Performance, Balanced, Quality, Battery Saver, Disabled.

Automatic performs a twelve-second passive startup calibration, then records sustained checkpoints at sixty-second intervals with hysteresis before changing the next-launch profile. It stores only a local hashed per-game profile plus timing, thermal status and process PSS memory. It never uploads ROMs, BIOS files, saves or identifiable telemetry.

## Neural inference

Not included in v1.5 RC1. LiteRT or another local runtime may be evaluated later only with a compatible model license and demonstrated quality/performance benefit.
