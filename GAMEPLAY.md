# Galaxy Monkey — Gameplay, Navigation & FX Guide

A field manual for the next engineer who picks this codebase up. Two voices live in this document:

- **For Players** — short blocks describing what the game *feels* like.
- **For Developers** — code paths, class names, and the exact maths behind the feel, so you can rebuild or port the game without guessing.

> **Stack note (read this first):** Galaxy Monkey is an **Android Java** game rendered with the **2D `android.graphics.Canvas`** API. It is **not** JavaFX, OpenGL, or LibGDX. There are no shaders, no GL surface, and no audio engine wired in today. If you are porting to JavaFX / OpenGL / LibGDX, the sections below tell you *what* must be reproduced; the *how* is up to your target stack.

---

## 1. Overview

Galaxy Monkey is a **landscape, fullscreen, twin-stick arcade shooter** for Android (min/target/compile SDK 26, Java 1.8). The player flies a monkey-piloted ship through procedurally-spawned waves of enemy ships and gorilla bosses, picking up bananas, dodging bullets, and chaining levels.

Three `Activity` screens:

| Activity | File | Purpose |
| --- | --- | --- |
| `MenuActivity` | `app/src/main/java/com/theGalaxyMonkey/Activities/MenuActivity.java` | Launcher (`MAIN` / `LAUNCHER` intent filter) |
| `MainActivity` | `app/src/main/java/com/theGalaxyMonkey/Activities/MainActivity.java` | Gameplay screen — joysticks, HUD, pause overlay |
| `GameOverActivity` | `app/src/main/java/com/theGalaxyMonkey/Activities/GameOverActivity.java` | End screen, score persistence |

All three are declared `sensorLandscape` + `Theme.NoTitleBar.Fullscreen` in `app/src/main/AndroidManifest.xml`.

Hardware acceleration is enabled at the application level (`android:hardwareAccelerated="true"`), so the `Canvas` is GPU-backed on supported devices even though no GL code is written by hand.

The **render loop** lives in `GameView` (`app/src/main/java/com/theGalaxyMonkey/Scene/GameView.java`): `onDraw(Canvas)` calls `run()` (which computes delta time from `System.nanoTime()`), then `controller.update(deltaTime)` and `controller.draw(canvas, paint)`, then `invalidate()` to schedule the next frame. The UI HUD updates on a separate `Handler`-driven `FrameUpdate` in `MainActivity` posted with `frame.postDelayed(frameRunnable, FPS)` where `FPS = 60`.

---

## 2. For Players: Flying the Ship

You pilot the monkey from the centre of the screen. Two virtual thumbsticks live on the bottom left and bottom right:

- **Left stick — Move.** Push in any direction to fire the thrusters and accelerate the ship that way. Let go and you coast: the ship keeps its velocity until you nudge it the other way. Push lightly and nothing happens — you need to commit to the direction past about half-deflection.
- **Right stick — Aim & Fire.** Push the right stick toward a target and the weapon shoots a bullet that way. Same half-deflection threshold — light flicks are ignored so you don't waste shots.

While you thrust, a glowing engine plume flickers out behind the ship in three quick frames, rotated to match the direction you're moving. The ship itself rotates to point along its velocity vector. When you stop pushing, the plume fades and the ship drifts.

Hit an enemy — or get hit — and a quick white particle puff sprays from the impact. Detonate a bomb pickup and a 25-frame explosion sprite flashes through. The background is a quiet starfield of static planets (Venus, Mars, Jupiter, Uranus) hanging in space — no parallax, no scroll, just a calm cosmic backdrop while your dogfight plays out in the foreground.

The HUD across the top shows your **health bar** (green progress bar, 100 max), **three life icons** (one disappears each death), **current level**, and **score**. The pause button on the corner swaps the game out for a black-hole portal animation and a floating monkey while you decide whether to resume or start over.

---

## 3. Navigation Deep Dive

This section follows a single joystick push from glass to pixels.

### 3.1 Input — virtual joystick

`MainActivity.initUI()` (line ~224) binds two `JoystickView`s from the third-party `io.github.controlwear:virtualjoystick:1.9.2` library:

```java
joystickLeft.setOnMoveListener(new JoystickView.OnMoveListener() {
    @Override
    public void onMove(int angle, int strength) {
        getGameView().getController().moveMonkey(angle, strength);
    }
});

joystickRight.setOnMoveListener(new JoystickView.OnMoveListener() {
    @Override
    public void onMove(int angle, int strength) {
        getGameView().getController().shootMonkey(angle, strength);
    }
});
```

`angle` is degrees (0–360, 0 = east, counter-clockwise per the controlwear convention) and `strength` is a percentage 0–100.

### 3.2 Controller dispatch

`Controller.moveMonkey(double angle, double strength)` and `Controller.shootMonkey(double angle, double strength)` (file `app/src/main/java/com/theGalaxyMonkey/Scene/Controller.java`, lines 153 and 164) gate input on a **strength threshold**:

```java
public void moveMonkey(double angle, double strength) {
    ...
    if (isPressed() && strength > 50) {
        monkey.moveThatA$$(angle);
    }
}

public void shootMonkey(double angle, double strength) {
    if (isCanShoot() && strength > 50) {
        ...
    }
}
```

Below 50% deflection nothing happens — this is the dead-zone that gives the controls their deliberate feel. If you tune nothing else when porting, tune this number.

### 3.3 Velocity accumulation

`Monkey.moveThatA$$(double angle)` (file `app/src/main/java/com/theGalaxyMonkey/GameObjects/Monkey.java`, line 87) accumulates an additive cosine/sine impulse and spawns a thrust particle:

```java
public void moveThatA$$(double angle) {
    if (!isDoneMoving()) {
        setVelX(getVelX() + (float) Math.cos(angle * (Math.PI / 180)));
        setVelY(getVelY() + (float) Math.sin(angle * (Math.PI / 180)));

        spaceShipThrustList.add(new SpaceShipThrust(view, this, getX(), getY() + 20,
                getVx(), getVy(), getAngularVel(), 40, 40));
    }
}
```

Notes:
- The velocity components are **accumulated, not replaced** — each `onMove` callback adds another unit-vector impulse. That's the source of the coast-y, momentum-y feel.
- The thrust spawn is offset `+20px` on Y, which is why the plume reads as coming from under the ship.

### 3.4 Integration & rendering

`Monkey.update(float deltaTime)` (same file, line 44) integrates position with a fixed damping factor and clamps to screen bounds:

```java
public void update(float deltaTime) {
    setX(getX() + velX * deltaTime * 0.05f);
    setY(getY() - velY * deltaTime * 0.05f);

    angularVel = getAngularVel(getVelX(), getVelY(), getX(), getY());
    ...
    if (x > screenX) setX(screenX);
    if (x < 0)       setX(0);
    if (y > screenY) setY(screenY);
    if (y < 0)       setY(0);
    ...
}
```

The `0.05f` constant is the global ship-speed knob — increase it and the ship gets twitchier, decrease it and it gets cinematic. Y is inverted because Android screen-Y grows downward.

`angularVel` is computed from velocity via `atan2(velY - y, velX - x)` (normalized to 0–360°) — slightly idiosyncratic because it mixes velocity with position, but it produces a usable rotation angle for the sprite. The boundary handling is hard clamp, **no bounce, no wrap**.

The actual draw happens in the base class `ImageGameObject` (`app/src/main/java/com/theGalaxyMonkey/GameObjects/Abstract/ImageGameObject.java`) via a `Matrix` pipeline: `preTranslate(x - w/2, y - h/2)` → `preRotate(angle, w/2, h/2)` → `canvas.drawBitmap(bitmap, matrix, null)`.

---

## 4. Visual Effects Catalogue

All effects use `Canvas` primitives. No shaders, no GL. Most assets live under `app/src/main/res/drawable/` and are decoded with `BitmapFactory.decodeResource`; the gameplay sprites live under `app/src/main/assets/sprites/` and are decoded via `AssetManager`.

### 4.1 Engine thrust plume

| Field | Value |
| --- | --- |
| Class | `SpaceShipThrust` (`app/src/main/java/com/theGalaxyMonkey/GameObjects/SpaceShipThrust.java`) |
| Frames | `R.drawable.b14`, `b15`, `b16` (3 PNGs in `res/drawable/`) |
| Frame delay | `animation.setDelay(10)` — frame swaps every 10 update ticks |
| Lifetime | One-shot — `isFinished` flips true after `animation.playedOnce()` |
| Size | 40 × 40 px |
| Rotation | `atan2(vy - y, vx - x)`, normalized 0–360°, baked into each frame via `Matrix` at construction |
| Trigger | One new instance per `moveThatA$$` call (i.e. every accepted `onMove` tick above the 50% threshold) |

Important quirk: rotation is **baked into the bitmap** in the constructor (`Bitmap.createBitmap(..., matrix, false)`), not applied at draw time. So each thrust puff captures the heading at spawn and stays at that angle until it expires. That's cheap, but it's why fast turns produce a fan of differently-angled plumes — which actually looks great.

### 4.2 Procedural explosion (small impact)

| Field | Value |
| --- | --- |
| Class | `Explosion` + `Particle` (`GameObjects/Explosion.java`, `GameObjects/Abstract/Particle.java`) |
| Particle count | Caller-chosen (game uses 10) |
| Particle shape | Solid rectangle, side ∈ `[1, 15]` px (random) |
| Colour | `Color.argb(alpha, 255, 255, 255)` — pure white with alpha fade |
| Velocity | Random ±7 px per axis |
| Lifetime | `TEMPO_DE_VIDA = 2` (≈ 2 seconds) |
| Fade | `alpha -= ((float) 2 / 10) * deltaTime` per update — linear to zero |
| Draw | `canvas.drawRect(x, y, x+w, y+h, paint)` (no textures) |

This is the cheapest and most reusable FX in the game — re-use it for any new "something popped" moment.

### 4.3 Bomb explosion (sprite sequence)

| Field | Value |
| --- | --- |
| Class | `BombExplosion` (`app/src/main/java/com/theGalaxyMonkey/GameObjects/BombExplosion.java`) |
| Frames | `R.drawable.e1` … `R.drawable.e25` (25 PNGs in `res/drawable/`) |
| Frame delay | `animation.setDelay(1)` — one tick per frame |
| Lifetime | One-shot, fires `setFinished(true)` after `playedOnce()` |
| Position | Fixed at construction `(x, y)`; no movement, no rotation |
| Loop | One-shot only — to chain, instantiate a new one |

### 4.4 Projectiles

| Field | Value |
| --- | --- |
| Class | `Bullet` (`app/src/main/java/com/theGalaxyMonkey/GameObjects/Arms/Bullet.java`) |
| Player sprite | `assets/sprites/bullet.png` |
| Enemy sprite | `assets/sprites/enemybullet.png` |
| Ownership | `ID` enum (`MONKEY` vs enemy variants), shared class |
| Rotation | Rotated by trajectory angle via the `ImageGameObject` matrix pipeline |
| Weapon plumbing | `Monkey.fireWeapon(double angle, View view)` → `Weapon.shoot(...)` — see `GameObjects/Arms/Weapon.java` |

### 4.5 Backdrop

| Field | Value |
| --- | --- |
| Class | `Scene` (`app/src/main/java/com/theGalaxyMonkey/Scene/Scene.java`) + `CelestialObject` |
| Contents | Static planet bitmaps (Venus, Mars, Jupiter, Uranus) |
| Motion | None — fixed positions, no parallax, no scroll |
| Other | Also owns level/wave spawning logic |

### 4.6 HUD overlay

Defined in `app/src/main/res/layout/activity_main.xml`, populated in `MainActivity.initUI()` and refreshed in the `FrameUpdate` runnable (`MainActivity.java` line ~178):

- `ProgressBar` health bar (max 100, drawn green)
- Three `ImageView` life icons (`R.id.lifeone/lifetwo/lifethree`), hidden as lives drop
- `TextView`s for level and score, formatted via `R.string.level_prefix`
- `ImageButton` pause → swaps gameplay out for a black-hole portal animation (`R.anim.rotate`) and a floating monkey (`TranslateAnimation`)

### 4.7 Things that **don't** exist (and a rebuild may want)

The current build has **no**:

- camera or viewport — every entity uses raw screen coordinates
- screen shake or hit-stop
- parallax / scrolling starfield
- post-processing (bloom, motion blur, chromatic aberration)
- particle pooling — `Particle`/`Explosion` allocate fresh on each spawn
- texture atlasing — each sprite is its own PNG
- frame-independent physics in the strict sense — `deltaTime` scaling exists, but constants like `0.05f` and `0.1f` are picked for the current frame cadence

If you are rebuilding from scratch, please at least add **screen shake on explosion**, **parallax starfield**, and **object pooling for particles**. They are cheap and they multiply the perceived production value.

---

## 5. Styling & Assets

### 5.1 Palette

From `app/src/main/res/values/colors.xml`:

| Name | Hex | Used for |
| --- | --- | --- |
| `colorPrimary` | `#3F51B5` | Material primary |
| `colorPrimaryDark` | `#303F9F` | Status-bar tint |
| `colorAccent` | `#FF4081` | Material accent |
| `darkGrey` | `#A9A9A9` | Subdued UI |
| `whiteSmoke` | `#F5F5F5` | Light surfaces |
| `spaceBlue` | `#ADD8E6` | HUD text (referenced in `Controller`) |
| `black_overlay` | `#66000000` | Translucent overlays (pause veil) |

Particle FX hard-code white (255, 255, 255) with alpha fade — they don't read from `colors.xml`. If you want themed explosions, that's the line to touch (`Particle.java` ~47, ~72).

### 5.2 Sprite inventory

Two locations, two loading APIs:

**`app/src/main/assets/sprites/` — loaded via `AssetManager.open(...)`** (gameplay entities):

```
banana.png         bomb.png           bullet.png
enemy.png          enemyb.png         enemyb1.png       enemybullet.png
enemyr.png         enemys1.png        enemysr.png
gorilla.png        gorillar.png       gorillat1l.png    gorillat2r.png
player.png         rb.png
unused/            (← reference / archive material)
```

**`app/src/main/res/drawable/` — loaded via `R.drawable.*`** (FX frames + UI chrome):

```
b1.png … b20.png   ← thrust / misc frames (b14, b15, b16 used by SpaceShipThrust)
e1.png … e25.png   ← bomb-explosion frames (all 25 used by BombExplosion)
back.jpg           ← background
blackhole.png      ← pause-overlay portal
```

### 5.3 Other styling bits

- **Orientation:** `sensorLandscape`, fullscreen, immersive sticky (see UI flags in `MainActivity.onCreate`).
- **Pause overlay:** uses `R.anim.rotate` (a single rotate `Animation`) for the black-hole portal and `TranslateAnimation` for the floating monkey.
- **Score persistence:** `ScoreManagement/SharedPrefManager` + `GameScoreController` (SharedPreferences).
- **Theme:** `@android:style/Theme.NoTitleBar.Fullscreen` on every gameplay activity.

---

## 6. Proposed Audio Layer (NOT IMPLEMENTED)

> **Current state:** A repo-wide grep for `SoundPool`, `MediaPlayer`, `AudioManager`, `playSound` returns **zero hits**. There are no audio files under `app/src/main/res/raw/` or `app/src/main/assets/`. The game ships silent.

When you rebuild this, please add audio. This section is a starting spec — feel free to disagree, but disagree on purpose.

### 6.1 Engine recommendation (Android-native)

- **Short SFX (< 1 s, played often, possibly overlapping):** Android's `SoundPool`. Load assets into a pool on `Activity.onCreate`, call `pool.play(id, ...)` from game-thread callbacks. Built-in mixing, low latency, no GC churn after warm-up.
- **Music & long loops (menu BGM, game BGM, boss intro):** `MediaPlayer`. One instance per track, `setLooping(true)`, pause/resume on `Activity` lifecycle.
- **Ducking:** when a `MediaPlayer` track is playing and a `SoundPool` SFX fires, drop the music volume to ~0.4 for 200 ms then ramp back. Cheap and dramatic.

If you are porting off Android, swap the implementations:

| Target | SFX | Music |
| --- | --- | --- |
| JavaFX desktop | `javafx.scene.media.AudioClip` | `javafx.scene.media.MediaPlayer` |
| LibGDX | `com.badlogic.gdx.audio.Sound` | `com.badlogic.gdx.audio.Music` |
| LWJGL / raw OpenGL | OpenAL via `org.lwjgl.openal` | Same, with streaming buffers |

### 6.2 Event → asset table

| Event | Asset (suggested) | Engine | Trigger point in current code |
| --- | --- | --- | --- |
| Thruster loop | `thrust_loop.ogg` (0.4 s loop) | `SoundPool` with `setLoop(-1)` | `Monkey.moveThatA$$(...)` — start on first call after idle, stop after N ms of no calls |
| Player weapon fire | `laser_player.ogg` (0.15 s) | `SoundPool` | `Weapon.shoot(...)` for `ID.MONKEY` |
| Enemy weapon fire | `laser_enemy.ogg` (0.15 s) | `SoundPool` | `Weapon.shoot(...)` for non-`MONKEY` IDs |
| Bullet hit (any) | `hit_soft.ogg` (0.1 s) | `SoundPool` | `Controller` collision handler — bullet ↔ entity |
| Monkey takes damage | `hit_player.ogg` (0.2 s) | `SoundPool` | `Controller` collision branch where monkey health drops |
| Small explosion | `explode_small.ogg` (0.5 s) | `SoundPool` | `Explosion` constructor (or wherever you instantiate it) |
| Bomb explosion | `explode_big.ogg` (1.0 s) | `SoundPool` | `BombExplosion` constructor |
| Banana pickup | `pickup_banana.ogg` (0.2 s, pitched up) | `SoundPool` | `Controller` collision — monkey ↔ banana |
| Level up | `level_up_sting.ogg` (1.5 s) | `SoundPool` | `Controller.levelUp` / `Scene.advanceLevel` (find by grep — current method name is in `Scene`) |
| Boss spawn | `boss_roar.ogg` (1.5 s) | `SoundPool` | Wherever a gorilla is added to `gameObjectList` |
| Game over | `game_over_sting.ogg` (2 s) | `SoundPool` | `GameView.changeSceneToGameOver` (line ~82) |
| Menu music | `bgm_menu.ogg` (2 min loop) | `MediaPlayer` | `MenuActivity.onCreate` / `onResume`, stop in `onPause` |
| Gameplay music | `bgm_game.ogg` (2 min loop) | `MediaPlayer` | `MainActivity.onCreate` / `onResume`, stop in `onPause` |
| Pause sting | `pause_swoosh.ogg` (0.3 s) | `SoundPool` | `MainActivity.pauseGame(View)` |

### 6.3 Asset format guidance

- Use **`.ogg` Vorbis** for SFX — small, royalty-free decoder, supported on every Android version since API 9.
- Use **`.ogg` or `.mp3`** for music. Loops sound better in `.ogg` (sample-accurate seek).
- Drop everything in `app/src/main/res/raw/` (referenced as `R.raw.thrust_loop`) so the build pipeline compresses and indexes them.

### 6.4 Wiring sketch (drop-in starting point)

```java
// In MainActivity (or a dedicated AudioManager singleton):
private SoundPool sfx;
private int sfxLaser, sfxHit, sfxExplodeSmall;
private MediaPlayer bgm;

@Override
protected void onCreate(Bundle s) {
    super.onCreate(s);
    AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    sfx = new SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build();
    sfxLaser        = sfx.load(this, R.raw.laser_player, 1);
    sfxHit          = sfx.load(this, R.raw.hit_soft, 1);
    sfxExplodeSmall = sfx.load(this, R.raw.explode_small, 1);

    bgm = MediaPlayer.create(this, R.raw.bgm_game);
    bgm.setLooping(true);
    bgm.setVolume(0.6f, 0.6f);
    bgm.start();
}

public void playLaser()        { sfx.play(sfxLaser, 1f, 1f, 1, 0, 1f); }
public void playHit()          { sfx.play(sfxHit, 1f, 1f, 1, 0, 1f); }
public void playExplodeSmall() { sfx.play(sfxExplodeSmall, 1f, 1f, 1, 0, 1f); }
```

Expose that manager to `Controller` (constructor injection or a `View.getContext()` cast) and call from the trigger points in the table.

---

## 7. Build & Run

```bash
# from repo root
./gradlew installDebug          # build + install to a connected device/emulator
./gradlew assembleDebug         # build APK only
adb shell am start -n com.galaxymonkey.galaxymonkeyxxx/com.theGalaxyMonkey.Activities.MenuActivity
```

- **Min/target/compile SDK:** all 26 (Android 8.0). Bump these on rebuild.
- **Java:** source/target 1.8.
- **Device:** any Android phone/tablet in landscape. Emulator works but joysticks feel sloppy with a mouse — use a touch device for feel work.
- **Dependencies:** see `app/build.gradle`. The only runtime third-party is `io.github.controlwear:virtualjoystick:1.9.2`.

---

## 8. Notes for a Future Rebuild

If you are reimplementing this game in JavaFX, LibGDX, Unity, Godot, or anywhere else, here is the short list of what is worth preserving and what is worth fixing.

**Preserve (the feel):**

- The two-stick layout (left = move, right = aim/fire). Don't collapse it to one stick + auto-aim.
- The **50%-strength dead-zone** — it's why the controls feel deliberate.
- **Additive velocity with light damping** (`+= cos/sin`, integrate at `0.05f`). It's why the ship coasts.
- **Bake rotation into thrust frames at spawn** — it's why fast turns produce a fan of plumes and looks better than it should.
- The **white-rectangle particle puff** for small impacts. Cheap and readable.
- The **calm static planet backdrop** — resist the urge to fill the screen.

**Fix (the gaps):**

- Add **audio**, per Section 6. The game is currently silent and it shouldn't be.
- Add **screen shake** on bomb explosions and player damage. Two-line addition, massive felt impact.
- Add **parallax** to the planet backdrop — even just two layers at 0.3× and 0.6× scroll.
- **Pool particles** instead of allocating fresh `Particle` objects per explosion (GC pressure on long runs).
- Decouple **physics from frame cadence** — promote the `0.05f` and `0.1f` constants to named, tuneable fields and integrate with a fixed step.
- Replace the **string-based asset lookup** (`"sprites/bullet.png"` passed around) with an enum or asset registry.
- Add a **camera / viewport** abstraction so you can later add a larger play-field, follow shots, or zoom on boss intros.
- Consider **texture atlasing** the 25 explosion frames and 3 thrust frames; on Android Canvas it's a marginal win, on GL it's a huge one.

Have fun rebuilding. The bones are sound — give them sound.
