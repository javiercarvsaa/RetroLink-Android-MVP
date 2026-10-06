# RetroLink v0.6.4 RC1 — Automatic Cover Search

## Objetivo

Mejorar la identificación visual de la biblioteca sin introducir opciones decorativas ni exigir una API key.

## Carátulas automáticas

- Fuente inicial: Libretro Thumbnails, catálogo Nintendo - Nintendo 64 / Named_Boxarts.
- La búsqueda se ejecuta en segundo plano y nunca bloquea jugar una ROM.
- RetroLink descarga únicamente el índice público de nombres y la imagen seleccionada; no sube ni envía la ROM.
- El índice se cachea por 7 días.
- Las imágenes se almacenan en caché local por ROM.
- Si no hay red o no existe una coincidencia con confianza suficiente, se mantiene el placeholder del sistema.
- El nombre original del archivo importado se conserva para mejorar coincidencias futuras; las ROM ya importadas usan el título de cabecera como fallback.
- Hay una acción manual `↻ CARÁTULAS` para reintentar el catálogo cuando falten imágenes.

## Matching

La coincidencia combina:
- título original del archivo;
- título N64 de cabecera;
- región USA/Europa/Japón;
- normalización de signos/acentos;
- comparación por tokens y bigramas;
- equivalencias básicas como Brothers -> Bros;
- umbral conservador para evitar carátulas incorrectas.

## UI

- Biblioteca: tarjetas con carátula, título y región.
- Detalle seleccionado: carátula grande y estado real.
- Estado `Carátulas X/Y` basado en archivos realmente cacheados.
- Perfil: se reduce la ficha de dispositivo a modelo, Android y disponibilidad N64.
- Los dispositivos virtuales de Android dejan de mostrarse como `mando físico`.
- Sala: selector 1P/2P/3P/4P pasa a ancho proporcional para evitar recorte del botón 4.

## Alcance

v0.6.4 solo habilita carátulas automáticas para N64 porque es el único core actualmente integrado. La arquitectura de `CoverArtManager` queda separada para ampliar el catálogo cuando SNES, PS1, Game Boy y Atari estén realmente disponibles.
