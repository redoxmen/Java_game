# Zombie Survival — 2D Top-Down Shooter (plain Java + Swing)

No libraries, no Maven/Gradle. Just a JDK 17+ and the files in `src/`.

## How to run (dev)

```bat
javac -d out src\*.java
java -cp out Main
```

## How to package a single runnable JAR

```bat
javac -d out src\*.java
echo Main-Class: Main> manifest.txt
jar cfm ZombieSurvival.jar manifest.txt -C out .
java -jar ZombieSurvival.jar
```

The JAR contains only the compiled classes — no assets required.
`highscore.txt` is created next to the JAR / working dir on your first game over.

## Controls

| Key | Action |
|-----|--------|
| W A S D / Arrows | Move |
| Mouse | Aim (player + gun rotate to cursor) |
| Left click (hold) | Shoot current weapon |
| R | Reload |
| 1 / 2 / 3 | Switch to pistol / shotgun / rifle (once unlocked) |
| Esc | Pause / resume |
| Enter / Space | Start (menu) • Restart (game over) |
| Mouse click | Menu buttons (Start/Quit/Restart) |

## How the game works

- **Waves:** zombies spawn at the screen edges. Wave N spawns `5 + N*3`
  zombies, slightly faster (+4%) and tougher (+8% HP) than N-1. A
  `WAVE N` banner opens each wave, then a ~4s breather before the next.
- **Zombie types:** NORMAL (balanced), FAST (quick, fragile),
  TANK (slow bruiser), BOSS (every 5th wave, huge HP, 1000 pts + bonus).
- **Weapons:**
  - Pistol (default, infinite reserve): 25 dmg, 4 shots/s, 12-mag.
  - Shotgun (spread x7, slow): 14 dmg/pellet, 1.2 shots/s, 6-mag.
  - Rifle (fast): 18 dmg, 9 shots/s, 30-mag.
  - Shotgun crate is guaranteed on wave 2, rifle crate on wave 3
    (plus rare random drops). `AMMO` pickups refill shotgun/rifle reserves.
- **Pickups:** AMMO (yellow), HEALTH +30 (white/red cross),
  weapon crates S/R (blue). They bob, glow, blink before expiring (25s).
- **Damage:** zombies hurt on contact (per-zombie 1s cooldown + 0.6s player
  mercy invulnerability). HP 0 = game over screen with wave/kills/score.
- **Score:** 100/150/250/1000 per kill (+boss bonus). High score persists
  in `highscore.txt`.

## Game feel

Muzzle flash + spark particles, yellow bullet tracers, red blood bursts,
knockback on every hit, red screen flash + shake on hurt, big shake on
shotgun fire and boss spawn, dark tiled floor, crates/walls that block
movement AND bullets, vignette + low-HP pulse.

## Sounds (optional)

Drop `.wav` files into `sounds/` next to the working dir:

```
sounds/shoot.wav  sounds/shotgun.wav  sounds/hit.wav
sounds/zombiedie.wav  sounds/hurt.wav  sounds/pickup.wav
sounds/reload.wav  sounds/wave.wav  sounds/boss.wav  sounds/gameover.wav
```

Missing files are silently ignored — the game runs mute. Any 16-bit PCM
`.wav` works (e.g. export from Audacity / freesound.org).

## Sprites (optional)

Everything is drawn with shapes so there are zero required assets. To swap
in art later, put PNGs in `assets/` (e.g. `player.png`, `zombie_normal.png`)
and hook them into `Player.draw()` / `Zombie.draw()` — the code marks the
exact `SPRITE SWAP POINT` with a fallback to shapes.

## Files

```
src/
  Main.java            window + entry point
  GamePanel.java       loop (60 FPS fixed step), input, collisions, render
  Player.java          movement, aiming, inventory
  Zombie.java          4 types, chase AI, knockback
  Bullet.java          projectile + trail data
  Weapon.java          damage/rate/mag/reload presets
  Pickup.java          ammo/health/weapon crates
  WaveManager.java     wave composition + spawn queue + breaks
  ParticleSystem.java  blood/muzzle/impact/pickup/shockwave particles
  HUD.java             health/ammo/wave/score overlay
  Menu.java            title/pause/game-over screens + buttons
  SoundManager.java    javax.sound.sampled .wav player (silent if missing)
```

Each file has header + inline comments explaining the "why" so you can
present how every part works.
