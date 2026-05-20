# SpriteCook — First-Principles Starter

How to generate new sprites and animations for Galaxy Monkey using the SpriteCook MCP, from zero.

Skills in play:
- `/spritecook-workflow-essentials` — shared rules (credits, manifest, downloads, defaults)
- `/spritecook-generate-sprites` — still images via `generate_game_art`
- `/spritecook-animate-assets` — animations via `animate_game_art`

Always load `spritecook-workflow-essentials` alongside whichever of the other two you need.

## The mental model in one paragraph

SpriteCook lives behind two MCP tools. **`generate_game_art`** takes a text prompt and gives you a still image plus an `asset_id`. **`animate_game_art`** takes that `asset_id` plus a motion prompt and gives you an animated WebP/GIF/spritesheet. The `asset_id` is the unit of identity — it is how you keep a character consistent across an idle, walk, attack, and hurt. Generate the canonical still **once**, then animate **that same id** N times. Do not regenerate the character for each motion.

## The four tools you'll actually call

| Tool | Purpose | Returns |
| --- | --- | --- |
| `get_credit_balance` | Check before a batch | balance |
| `generate_game_art` | Make a still from a prompt | `asset_id` + `sprite_url` |
| `animate_game_art` | Animate an existing `asset_id` | animation `url` + `job_id` |
| `list_recent_assets` | Recover lost `asset_id`s | recent asset records |

`check_job_status(job_id)` if an animation is taking long. `get_asset_metadata(asset_id)` if you need to re-fetch the URL.

## Preflight (do this every time)

1. `get_credit_balance` — never start a multi-asset batch blind.
2. Look at `spritecook-assets.json` (manifest, see below) — reuse `asset_id`s before generating new ones.
3. Never paste an API key into chat, code, or a file. The MCP handles auth.

## Recipe 1 — Generate one still

The minimum useful call:

```
generate_game_art(
  prompt: "A chunky cartoon spaceship shaped like a smiling monkey face, side view, banana-yellow hull, glass cockpit visor, two thruster nozzles at the back",
  width: 256, height: 128,
  pixel: false,                  // detailed/HD; flip to true for retro
  bg_mode: "transparent",
  smart_crop_mode: "tightest",   // the default we want
  model: "gemini-3.1-flash-image-preview"
)
```

Be specific about **subject, pose, view angle, and key materials**. Vague prompts produce vague art.

Save the returned `asset_id` to the manifest immediately. That's now your canonical "player ship".

## Recipe 2 — Generate matched variations (consistency)

For "same character, different state" (e.g., gorilla holding a bomb vs gorilla without), pass the previous `asset_id` as `reference_asset_id`:

```
generate_game_art(
  prompt: "Same cartoon space gorilla, now winding up to throw a bomb, side view",
  reference_asset_id: "<gorilla_canonical_asset_id>",
  pixel: false, width: 256, height: 256,
  smart_crop_mode: "tightest"
)
```

For tweaking the same asset (recolor, fix detail), use `edit_asset_id` instead. The two are mutually exclusive.

## Recipe 3 — Animate an existing SpriteCook still

```
animate_game_art(
  asset_id: "<gorilla_canonical_asset_id>",
  prompt: "Idle",                 // auto_enhance_prompt fills it in
  output_frames: 8,
  output_format: "spritesheet",   // pick this for Phaser
  removebg: "Basic"
)
```

For higher quality motion, write one short paragraph describing **what moves, what stays still, what visible props do**:

> The cartoon space gorilla hovers in place, shoulders rising and falling with a slow breathing rhythm. Its tail sways gently behind it, the bomb in its left paw bobs slightly, and its eyes blink once mid-cycle. Camera stays still, character stays centered.

Source-size rule from the skill:
- ≤256×256 → use **pixel** animation
- 256-2048 px → use **detailed** animation
- Don't force a small source into detailed mode

Default `edge_margin: 6` is fine; don't tweak unless cropping is biting the sprite.

## Recipe 4 — Animate a legacy Galaxy Monkey PNG

`animate_game_art` requires a SpriteCook `asset_id` — it cannot ingest a raw local file directly through the MCP. To animate one of our existing PNGs (e.g. `gorilla.png` from `galaxy_monkey_assets`):

1. Import it via the SpriteCook HTTP API: `POST /v1/api/assets/import` with the same Bearer key the MCP uses. This returns an `asset_id`. **Ask the user to run this through whatever authenticated helper is already configured** — never construct the curl yourself with the key.
2. Save the new `asset_id` + label in the manifest.
3. Call `animate_game_art` against that `asset_id` exactly like Recipe 3.

If a helper isn't configured, stop and ask. Do not try to inline the key.

## Manifest convention for this repo

We store SpriteCook assets in `tools/sprite-pipeline/spritecook-assets.json` (create on first use). Minimal entry shape per the workflow skill:

```json
{
  "assets": [
    { "asset_id": "ast_xxx", "sha12": "ab12cd34ef56", "label": "player_ship_v1" },
    { "asset_id": "ast_yyy", "sha12": "11223344aabb", "label": "gorilla_canonical" }
  ]
}
```

- `asset_id` is the primary key.
- `sha12` = first 12 chars of the local file's SHA-256, so you can match a PNG on disk back to its SpriteCook id.
- `label` is freeform — match it to entries in [`asset-map.json`](./asset-map.json) where possible (e.g. `player_ship`, `gorilla_anim_frame_1`).

Before generating, **check this file first**. Before reusing a local PNG, compute its `sha12` and look it up. If you lose ids, run `list_recent_assets(limit=20)` to recover.

## Galaxy Monkey starting points

Concrete first jobs that map to assets in [`asset-map.md`](./asset-map.md):

| Goal | Approach |
| --- | --- |
| Replace `player.png` with HD ship | Recipe 1, `pixel: false`, `width: 275, height: 133` |
| Add a third gorilla pose | Recipe 2 with `reference_asset_id` = gorilla canonical |
| Make gorilla idle animation (replaces 4-frame cycle) | Recipe 3, `output_frames: 8`, `output_format: spritesheet` |
| Animate legacy `gorilla.png` directly | Recipe 4 import → Recipe 3 |
| Generate a new collectible (e.g. golden banana) | Recipe 2 with `reference_asset_id` = banana canonical |
| Re-make the 25-frame explosion as one polished spritesheet | Generate one canonical explosion still, then animate with `output_frames: 16, output_format: "spritesheet"` |

Match the legacy dimensions from `asset-map.json` so the Phaser port doesn't have to rescale everything.

## Defaults worth remembering

- `smart_crop_mode: "tightest"` (always, unless user asks for `"power_of_2"`).
- `model: "gemini-3.1-flash-image-preview"` is the recommended default. Drop to `gemini-2.5-flash-image` to save credits on throwaways; reach for `gemini-3-pro-image-preview` only for hero assets.
- `pixel: true` for retro, `pixel: false` for the smooth/HD direction the legacy game already leans toward.
- `output_format: "spritesheet"` is the right call for Phaser — line it up with a TexturePacker-style JSON or slice on a uniform grid.

## Common pitfalls

1. **Generating a new still for every motion.** Don't. Generate once, animate N times against the same `asset_id`.
2. **Forgetting to save the `asset_id`.** Write it to `spritecook-assets.json` the moment it returns. Recovery via `list_recent_assets` is a fallback, not a plan.
3. **Forcing a 48×46 bullet into detailed animation mode.** Tiny sprites → `pixel: true` (or omit `pixel` and let SpriteCook infer).
4. **Vague prompts.** "Cool monkey ship" → mush. "Side-view cartoon monkey-face spaceship, banana-yellow hull, two rear thrusters, glass visor, transparent background" → usable.
5. **Pasting the API key anywhere.** Stop, ask. The MCP and any preconfigured helper exist precisely so this never has to happen inline.
6. **Re-decoding at runtime.** SpriteCook output goes into a Phaser atlas at build time; never load PNGs per-frame the way the legacy Java does.
