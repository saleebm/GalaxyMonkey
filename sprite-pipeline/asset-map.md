# Galaxy Monkey — Legacy Asset Map

Generated 2026-05-15 from `/Users/minasaleeb/workspaces/galaxy_monkey_assets` for the TS/Phaser port at `/Users/minasaleeb/workspaces/gma`. Full machine-readable form: [`asset-map.json`](./asset-map.json).

## Totals

| Bucket | Count |
| --- | ---: |
| `Assets/sprites/` in-use | 16 |
| `Assets/sprites/unused/` | 8 |
| `res/drawable/` non-animation | 18 |
| Animation frame PNGs (`e1-e25`, `b14-b16`) | 28 |
| Dead/unwired `b*.png` frames | 17 |
| Launcher icon families (across DPI buckets) | 6 |
| Layouts | 3 |

## In-use sprites (`Assets/sprites/`)

| Sprite | Dims | Bytes | Role | Loaded by |
| --- | --- | ---: | --- | --- |
| `player.png` | 275×133 | 31,054 | Player ship | `Monkey.java` (string ctor) |
| `bullet.png` | 48×46 | 4,315 | Player bullet | `Monkey.fireWeapon` |
| `enemybullet.png` | 48×46 | 4,049 | Enemy bullet | `Enemy.fireWeapon` |
| `bomb.png` | 75×78 | 2,360 | Gorilla bomb | `Gorilla.fireWeapon` |
| `banana.png` | 135×105 | 18,873 | Pickup (primary) | `Juice.java` (string ctor) |
| `rb.png` | 135×105 | 18,851 | Pickup (rotten variant) | `Juice.java` (string ctor) |
| `enemy.png` / `enemyr.png` | 150×225 | ~34 KB | **PREPPY** L/R | `Enemy.java` |
| `enemyb.png` / `enemyb1.png` | 156×226 | ~37 KB | **WHITE** L/R (1 = RIGHT) | `Enemy.java` |
| `enemys1.png` / `enemysr.png` | 163×159 | ~24 KB | **SHADY** L/R (1 = LEFT) | `Enemy.java` |
| `gorilla.png` | 214×217 | 54,527 | Gorilla frame 1 (0 ms) | `Gorilla.java` |
| `gorillat1l.png` | 274×261 | 70,245 | Gorilla frame 2 (750 ms) | `Gorilla.java` |
| `gorillat2r.png` | 213×217 | 54,308 | Gorilla frame 3 (1250 ms) | `Gorilla.java` |
| `gorillar.png` | 225×219 | 61,012 | Gorilla frame 4 (1750 ms) | `Gorilla.java` |

Enemy `L/R` swaps on `velX` sign. Gorilla 4-frame cycle loops every 2,250 ms with center-anchored draw.

## Animation sequences

| Id | Source | Frames | Frame range | Anchor | Loop | Notes |
| --- | --- | ---: | --- | --- | --- | --- |
| `explosion` | `BombExplosion.java` | 25 | `e1-e25` (67-108 px wide, 67-105 tall) | top-left in Java, use `setOrigin(0.5)` in Phaser | once | `setDelay(1)` → renderer-tick paced |
| `thrust` | `SpaceShipThrust.java` | 3 | `b14-b16` (24-25 × 33-36) | top-left | once | `setDelay(10)`, pre-rotated by matrix |

Frame sizes are NOT uniform — pack as a trim-aware atlas (TexturePacker JSON), not a fixed-grid sheet.

## Celestial / FX / UI (`res/drawable/`)

- **Planets (9):** `venus`, `uranus`, `mars`, `jupiter`, `neptune`, `mercury`, `saturn` (365×147 wide for rings), `sun`, `earth` — all ≈200×200.
- **Special FX:** `blackhole.png` (1230×1264, 2.6 MB — downscale), `glow.png` (35×20 exhaust mote), `pink_ball.png` (joystick thumb).
- **UI:** `pause_btn`, `pausefloat` (paused-state monkey float), `life`, `joystick_background`, `title`.
- **Background:** `back.jpg` (1920×1080, 572 KB — convert to WebP).
- **Vector drawables:** `ic_launcher_background.xml` (teal grid), `drawable-v24/ic_launcher_foreground.xml` (stock Android robot — not branded).
- **`anim/rotate.xml`:** generic linear infinite spin (0→359° / 16 s / pivot 50/50). Unrelated to frame sequences.

## Launcher icons

5 PNG DPI variants × 4 families (`ic_launcher`, `ic_launcher_round`, `ic_launcher_background`, `ic_launcher_foreground`) plus 2 adaptive-icon XMLs and the single branded `ic_launcher_web.png` (512×512). `ic_launcher` and `ic_launcher_round` are byte-identical per DPI — dedupe.

## Layouts → Phaser scenes

| Android layout | Phaser scene | Drawables wired |
| --- | --- | --- |
| `activity_splash_screen.xml` | `MenuScene` | `title` |
| `activity_main.xml` | `PlayScene` + `HUDScene` | `back`, `joystick_background`, `pink_ball`, `pause_btn`, `blackhole`, `glow`, `pausefloat`, `life` (×3) |
| `activity_game_over.xml` | `GameOverScene` | `back`, `title` |

## Values (notable)

- Strings carry formatted score/level templates (`Score: %1$d`, `Level: %1$d`).
- A **Sentry DSN is hard-coded in `strings.xml`** — strip before shipping a web build.
- Material palette: `#3F51B5`, `#303F9F`, `#FF4081`, `#ADD8E6` spaceBlue, `#66000000` overlay.

## Unused / dead weight

- `sprites/unused/`: 8 files (astronaut, vexels helmet, enemya, enemyteam, missile, pauseMonkey, spaceshipMonkeys, ufo-spaceship) — mostly hi-res concept art and stock images.
- `b1-b13`, `b17-b20` (17 PNGs) — exist on disk, referenced by nothing. Two size clusters (~34×50 vs ~24×35), likely a deprecated thrust family.

## Suggested Phaser atlases

1. **`monkey`** — `player`, `bullet`, `enemybullet`, `bomb`, `banana`, `rb`, `glow`, `life`, `pause_btn`, `pink_ball`
2. **`enemies`** — all 6 directional enemies + 4 gorilla frames
3. **`explosion`** — `e1-e25` (trim-aware)
4. **`thrust`** — `b14-b16`
5. **`celestial`** — 9 planets (preserve saturn aspect)

Loose: `back.jpg` (WebP), `title.png`, `blackhole.png` (downscaled), `joystick_background.png`, `pausefloat.png`.
Drop: `sprites/unused/*`, dead `b*.png`, stock `ic_launcher_foreground.xml`, duplicate round-launcher mipmaps.

## Critical port traps

1. **Enemy L/R naming is inconsistent.** WHITE uses `1` for RIGHT; SHADY uses `1` for LEFT. Wire from `Enemy.java`'s `velX >= 0` mapping, not from filename intuition.
2. **Gorilla frames are non-uniform PNGs** and the legacy code re-decodes them every tick. Preload all four in Phaser and use `setTexture` / `setOrigin(0.5)`.
3. **Anchor flip.** Java blits with top-left; Phaser defaults to 0.5/0.5. Pick one and recompute positions consistently.
4. **`ImageGameObject` re-decodes PNGs on every directional swap.** Don't replicate — preload everything.
5. **Animation frame sizes are non-uniform**, so use TexturePacker atlas + trim, not a grid sheet.
