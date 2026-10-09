// Weapon.java — Data + logic for one gun (pistol / shotgun / rifle).
// Each weapon tracks its own magazine, reserve ammo, fire-rate cooldown and reload timer.
// GamePanel asks weapon.canFire() every frame while the mouse is held, then spawns
// one Bullet per pellet. Ammo pickups call addReserveAmmo().
public class Weapon {

    // Weapon identifiers used for the 1/2/3 keys and for pickup unlocks.
    public static final int PISTOL = 0;
    public static final int SHOTGUN = 1;
    public static final int RIFLE = 2;

    public final int id;               // PISTOL / SHOTGUN / RIFLE
    public final String name;          // Shown in the HUD ("PISTOL", ...)
    public final int damage;           // Damage per bullet
    public final double fireInterval;  // Seconds between shots (1 / fireRate)
    public final int magSize;          // Bullets per magazine
    public final double reloadTime;    // Seconds a reload takes
    public final int pellets;          // Bullets spawned per trigger pull (shotgun = 6)
    public final double spread;        // Random spread in radians applied to each pellet
    public final double bulletSpeed;   // Pixels per second
    public final double knockback;     // Knockback force applied to zombies
    public final double shake;         // Screen-shake trauma added per shot
    public final boolean infiniteReserve; // Pistol never runs out of reserve ammo

    public int magAmmo;      // Bullets currently in the magazine
    public int reserveAmmo;  // Bullets available for reloading (ignored if infinite)
    public boolean reloading = false;

    private double fireCooldown = 0;  // Counts down to 0; fire allowed at <= 0
    private double reloadTimer = 0;   // Counts down while reloading

    public Weapon(int id, String name, int damage, double fireRate,
                  int magSize, int startReserve, double reloadTime,
                  int pellets, double spread, double bulletSpeed,
                  double knockback, double shake, boolean infiniteReserve) {
        this.id = id;
        this.name = name;
        this.damage = damage;
        this.fireInterval = 1.0 / fireRate;
        this.magSize = magSize;
        this.magAmmo = magSize; // start with a full mag
        this.reserveAmmo = startReserve;
        this.reloadTime = reloadTime;
        this.pellets = pellets;
        this.spread = spread;
        this.bulletSpeed = bulletSpeed;
        this.knockback = knockback;
        this.shake = shake;
        this.infiniteReserve = infiniteReserve;
    }

    // ---- Factory presets (tweak game balance here) ----
    public static Weapon createPistol() {
        return new Weapon(PISTOL, "PISTOL", 25, 4.0,
                12, Integer.MAX_VALUE, 1.0,
                1, 0.02, 520.0, 120.0, 0.0, true);
    }

    public static Weapon createShotgun() {
        return new Weapon(SHOTGUN, "SHOTGUN", 14, 1.2,
                6, 24, 2.0,
                7, 0.28, 480.0, 260.0, 0.45, false);
    }

    public static Weapon createRifle() {
        return new Weapon(RIFLE, "RIFLE", 18, 9.0,
                30, 120, 1.6,
                1, 0.05, 620.0, 90.0, 0.05, false);
    }

    /** Advance cooldowns. Must be called every update with delta time. */
    public void update(double dt) {
        if (fireCooldown > 0) fireCooldown -= dt;
        if (reloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                finishReload();
            }
        }
    }

    /** True when the trigger can produce a shot right now. */
    public boolean canFire() {
        return !reloading && fireCooldown <= 0 && magAmmo > 0;
    }

    /** Consume one magazine bullet and reset the fire cooldown. Call after canFire(). */
    public void onFired() {
        magAmmo--;
        fireCooldown = fireInterval;
        // Auto-reload when the mag runs dry (if reserve ammo exists).
        if (magAmmo == 0) {
            startReload();
        }
    }

    /** Begin a reload if needed and possible. */
    public void startReload() {
        if (reloading || magAmmo >= magSize) return;
        if (!infiniteReserve && reserveAmmo <= 0) return; // nothing to reload with
        reloading = true;
        reloadTimer = reloadTime;
    }

    private void finishReload() {
        reloading = false;
        if (infiniteReserve) {
            magAmmo = magSize;
        } else {
            int needed = magSize - magAmmo;
            int taken = Math.min(needed, reserveAmmo);
            magAmmo += taken;
            reserveAmmo -= taken;
        }
    }

    /** Add reserve ammo (from pickups). Caps at a sane max to avoid overflow. */
    public void addReserveAmmo(int amount) {
        if (infiniteReserve) return; // pistol doesn't need it
        reserveAmmo = Math.min(reserveAmmo + amount, 999);
    }

    /** 0..1 progress of the current reload (for HUD reload bar). */
    public double getReloadProgress() {
        if (!reloading) return 1.0;
        return 1.0 - (reloadTimer / reloadTime);
    }
}
