# Pantalla negra en Android (GPU PowerVR) — hallazgos y bloqueos

Documento de trabajo. El contexto completo, los archivos de diagnóstico y los diffs de la sesión
quedan **en local**, fuera de git, en `game/pantalla-negra-android/` (la carpeta `game/` está ignorada
por `.gitignore`).

## Síntoma

En un móvil con **PowerVR Rogue GE8320** (MediaTek MT6765): el juego **arranca con audio y responde a
los toques, pero la pantalla se queda negra**. En PC (NVIDIA) y en otro Android (Mali) se ve bien.

## Conclusión

En esta GPU el motor **no rasteriza la geometría**: la etapa de vértices/atributos no produce salida,
aunque **todo el estado del render está correcto**. El resto de hipótesis quedaron descartadas con
pruebas (ver abajo).

## Qué se descartó (con prueba)

- **El blit/present no es la causa.** `glBlitFramebuffer` no copia nada en esta PowerVR, pero sustituirlo
  por dibujar la textura como cuadro (con el shader del motor y con GL directo) tampoco se ve.
- El FBO offscreen está completo (`0x8cd5`); destino y ventana/escala correctos.
- El **bloque de uniforms (`std140`) no es la causa**: entregar la matriz por `uniform` normal tampoco pinta.
- El scissor/viewport/depth/blend están correctos.

## Qué se descubrió (medido desde el motor)

Los datos se volcaron a un fichero legible por adb
(`/sdcard/Android/data/com.klisee.reloaded/files/diag.txt`), porque `eprintln` se pierde en logcat aquí.

- El juego **sí dibuja al offscreen correcto** (`fb_draw=0x6 = offscreen_fbo`), pero el offscreen
  **lee negro puro** (`[0,0,0,255]`).
- Los draws **se ejecutan sin errores GL** (p. ej. 2040 vértices).
- Las **matrices** son correctas (ortho 2/320, 2/180).
- Los **atributos** están bien mapeados y en el draw: `vao=1 array_buf=1 prog=3`.
- Los **atlases tienen datos reales** (4096² RGBA; texel `[131,131,148,255]`).
- El color de mezcla del vértice es blanco `[1,1,1,1]`.
- **Prueba decisiva:** con el fragment shader escupiendo un **color fijo (magenta)**, el offscreen
  **sigue negro** → **no se rasteriza ni un fragmento**.

## Bugs reales encontrados de paso (pendientes de corregir)

1. **`std140` desajustado:** el bloque del shader mide 592 y el struct `RenderState` de Rust 716
   (`fog_colour` vec4 debe alinearse a 16: offset 576 en GLSL vs 564 en Rust).
2. **Bool desplazado:** el shader tiene `alpha_test` entre `lerp` y `fog_enabled`; Rust pone ahí `depth_test`.

## Bloqueo actual

No se puede seguir diagnosticando a ciegas en remoto sin provocar un ciclo de compilar/instalar/arrancar
(~90 s por intento en este móvil, y el arranque pesa). **Siguiente prueba concreta:** un shader que genere
un triángulo a pantalla completa **sin atributos** (posición por `gl_VertexID`) con color plano. Si eso
se pinta, queda confirmado que PowerVR no entrega los atributos de vértice; el arreglo iría por
re-establecer atributos/VAO en GLES estándar, condicionándolo a PowerVR si hace falta, para **no romper
NVIDIA/Mali**.

## Aparte (rendimiento)

El arranque tarda ~85–90 s en este móvil porque el motor sube los atlases a la GPU **sprite a sprite**
(`push_atlases`). Candidato claro a optimizar.
