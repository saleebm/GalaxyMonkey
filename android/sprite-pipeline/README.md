# Android Asset Pipeline

Converts iOS assets (Assets.xcassets imagesets, .spriteatlas bundles, .caf
audio) into the Android LibGDX format (TexturePacker game.atlas + .ogg sounds).

## Quick reference

```bash
# Full rebuild (from repo root):
android/sprite-pipeline/extract-stills.sh    # xcassets -> stage/stills/
android/sprite-pipeline/extract-anims.sh     # spriteatlas -> stage/anims/
android/sprite-pipeline/pack-atlas.sh        # stage/ -> app/assets/game.atlas
android/sprite-pipeline/convert-audio.sh     # ios Sounds/*.caf -> app/assets/sounds/*.ogg
```

Output lands in `android/app/assets/` (game.atlas, game.png, sounds/*.ogg).

## Adding a new still sprite

1. Drop the PNG named `<RegionName>.png` into `stage/stills/`, or add an
   imageset to `ios/GalaxyMonkey/Assets.xcassets/` and re-run
   `extract-stills.sh`.
2. Add a case to `Sprite` enum in
   `core/src/main/kotlin/dev/copt/galaxymonkey/SpriteCatalog.kt`:
   ```kotlin
   MY_SPRITE("MySprite"),
   ```
3. Re-run `pack-atlas.sh` to rebuild `game.atlas`.
4. Access via `SpriteCatalog.region(Sprite.MY_SPRITE)` — returns `null` if
   the region is missing, so no crash.

## Adding a new animation

1. Name frames `<base>_01.png`, `<base>_02.png`, ... and place them in
   `stage/anims/<base>/`. Or add a `.spriteatlas` bundle in xcassets and
   re-run `extract-anims.sh` (it converts PascalCase atlas names to
   snake_case and strips the `Anim` suffix).
2. Add a case to `AnimationSet` enum in
   `core/src/main/kotlin/dev/copt/galaxymonkey/AnimationCatalog.kt`:
   ```kotlin
   MY_ANIM("my_anim", 0.08f),
   ```
   TexturePacker groups frames by matching the base name + `_NN` index.
   This differs from iOS where `SKTextureAtlas.textureNames` are sorted
   alphabetically — LibGDX `findRegions(base)` returns them by atlas index.
3. Re-run `pack-atlas.sh`.

## L/R facing variants

Variants are **pre-baked separate PNGs** via `sips -f horizontal`. Never
runtime-mirror (SpriteBatch flip is fragile with rotation) and never
prompt-mirror (AI generation drifts details on flipped prompts).

```bash
sips -f horizontal MySprite.png --out MySpriteLeft.png
# or use the helper:
# ios/sprite-pipeline/flip-walk-atlas.sh <base>
```

Name them `<Name>Left.png` / `<Name>Right.png` and add both as separate
`Sprite` enum cases.

## Audio conversion (CAF -> OGG)

`convert-audio.sh` handles the pipeline:

```bash
# Manual single-file conversion:
afconvert input.caf -f WAVE -d LEI16 tmp.wav
ffmpeg -y -i tmp.wav -c:a libvorbis -q:a 4 output.ogg
```

- `-q:a 4` (libVorbis quality) balances file size vs fidelity for mobile.
- Output goes to `android/app/assets/sounds/`.
- iOS uses IMA4 mono `.caf`; Android uses Vorbis `.ogg`.

## TexturePacker settings

`pack-settings.json` configures the atlas build:

- `filterMin: Linear`, `filterMag: Linear` — matches iOS `.linear` sampling.
- `premultiplyAlpha: false` — LibGDX SpriteBatch expects straight alpha.
- `paddingX/Y: 2` with `duplicatePadding: true` — prevents bleed at edges.
- `maxWidth/Height: 4096` — safe for all target devices (minSdk 24).

The pack script auto-downloads `gdx-tools-1.12.1.jar` from Maven Central
into `.cache/` on first run.

## Graceful-missing contract

All catalogs return `null` (or empty) for absent assets — they never throw.

- `SpriteCatalog.region(sprite)` returns `TextureRegion?`
- `AnimationCatalog.animation(anim)` returns `Animation?`
- `AudioController.play(sfx)` no-ops if the sound file is missing
- `GameAssets.getSound(sfx)` returns `null` for missing SFX

This means art and audio can be added incrementally. Subsystems fall back
to primitive shape drawing when textures are absent.

## Source files

- `SpriteCatalog.kt` / `AnimationCatalog.kt` — enum-to-region/animation lookup
- `GameAssets.kt` — AssetManager bootstrap, loads atlas + sounds
- `AudioController.kt` — music + SFX with spatial attenuation
