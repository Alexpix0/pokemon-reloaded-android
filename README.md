# Pokemon Reloaded en Android

Port no oficial de **Pokemon Reloaded** a Android, hecho por la comunidad y para la comunidad, por
[@kliseex](https://github.com/Klisee).

Corre sobre [OpenGMK](https://github.com/OpenGMK/OpenGMK), una reimplementación nativa en Rust del
runner de Game Maker 8, así que el juego se ejecuta de verdad: no hay ningún Windows emulado por
medio y el rendimiento es prácticamente el de un juego nativo.

Sigue en beta, y eso se nota: **puede tener muchos errores y fallos**. Si tienes algún bug, mínimo o
grave, guarda un log desde la app (Ajustes → Logs) y repórtalo.

## Qué necesitas

Los archivos originales del juego, que están en el blog de **F3R**:
[pokemon-reloaded.blogspot.com](https://pokemon-reloaded.blogspot.com/)

Este repositorio no incluye ningún asset del juego, y eso es justo lo que lo mantiene limpio.

## Cómo se usa

1. Instala el APK de la última release.
2. Consigue el juego en un `.zip` y dale a **Importar** (o usa **Descargar** para abrir el blog).
3. **Jugar**. Si ya tenías partida en el PC, puedes traerla desde Ajustes → Partidas.

## Créditos

- **Pokemon Reloaded** es obra de **F3R Productions**: [el blog del juego](https://pokemon-reloaded.blogspot.com/)
- **OpenGMK**, la reimplementación del runner sin la que esto no existiría: [github.com/OpenGMK/OpenGMK](https://github.com/OpenGMK/OpenGMK)
- **Google Sans Flex**, la tipografía de la app

Pokemon es una marca de Nintendo, Game Freak y Creatures. Este proyecto no está afiliado ni
respaldado por ellos.

## Compilar

```sh
# El emulador (Rust) y su .so para Android
cd src/OpenGMK
cargo ndk -t arm64-v8a --platform 29 build -p gm8emulator --release

# La app
cd ../../android
./build.sh :app:assembleRelease
```

La firma se lee de `android/keystore.properties`, que no está en el repositorio.
