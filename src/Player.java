// Player.java — The survivor: WASD movement, mouse aiming, weapon inventory.
// Movement is normalized so diagonal speed == straight speed. Collides against
// obstacle rectangles by resolving X and Y separately (allows sliding along walls).
// Aiming angle is computed from screen mouse -> world coordinates in GamePanel.
//
// SPRITE SWAP: draw() currently uses shapes only. To use images later, drop
//   assets/player.png (+ player_gun.png) next to the JAR and uncomment the
//   image branch marked below — shapes remain as the fallback.
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class Player {
    public double x, y;              // World center
    public double radius = 14;       // Circle collision radius
    public double speed = 230;       // Pixels per second
    public int maxHp = 100;
    public int hp = 100;
    public double aimAngle = 0;      // Radians, 0 = facing right
    public double muzzleFlash = 0;   // Seconds of muzzle flash left (rendered by GamePanel)

    // Owned guns. Index 0 is always the pistol. Shotgun/rifle are added by pickups.
    public final List<Weapon> weapons = new ArrayList<>();
    public int currentWeapon = 0;

    // Contact-damage invulnerability window so one zombie can't drain HP instantly.
    private double hurtCooldown = 0;
    // Footstep bob for a subtle walk animation.
    private double walkPhase = 0;
    private boolean moving = false;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
        weapons.add(Weapon.createPistol());
    }

    public Weapon getWeapon() {
        return weapons.get(currentWeapon);
    }

    public boolean ownsWeapon(int id) {
        for (Weapon w : weapons) {
            if (w.id == id) return true;
        }
        return false;
    }

    /** Give shotgun/rifle (from a crate). Returns false if already owned (gives ammo instead). */
    public boolean unlockWeapon(int id) {
        if (ownsWeapon(id)) return false;
        if (id == Weapon.SHOTGUN) weapons.add(Weapon.createShotgun());
        else if (id == Weapon.RIFLE) weapons.add(Weapon.createRifle());
        else return false;
        currentWeapon = weapons.size() - 1; // auto-equip the new toy
        return true;
    }

    public void switchWeapon(int index) {
        if (index >= 0 && index < weapons.size()) {
            currentWeapon = index;
        }
    }

    /** Cycle with mouse wheel if you add a wheel listener later. */
    public void cycleWeapon(int dir) {
        currentWeapon = (currentWeapon + dir + weapons.size()) % weapons.size();
    }

    /** Move with WASD / arrows. keys[] comes from GamePanel (KeyEvent codes). */
    public void update(double dt, boolean[] keys, List<Rectangle> obstacles, int worldW, int worldH) {
        double dx = 0, dy = 0;
        // NOTE: we check both WASD and arrow key codes.
        if (keys[java.awt.event.KeyEvent.VK_W] || keys[java.awt.event.KeyEvent.VK_UP]) dy -= 1;
        if (keys[java.awt.event.KeyEvent.VK_S] || keys[java.awt.event.KeyEvent.VK_DOWN]) dy += 1;
        if (keys[java.awt.event.KeyEvent.VK_A] || keys[java.awt.event.KeyEvent.VK_LEFT]) dx -= 1;
        if (keys[java.awt.event.KeyEvent.VK_D] || keys[java.awt.event.KeyEvent.VK_RIGHT]) dx += 1;

        moving = (dx != 0 || dy != 0);
        if (moving) {
            double len = Math.hypot(dx, dy); // normalize diagonal
            dx /= len;
            dy /= len;
            walkPhase += dt * 10;
            moveWithCollision(dx * speed * dt, 0, obstacles);
            moveWithCollision(0, dy * speed * dt, obstacles);
        }
        // Stay inside the arena.
        x = Math.max(radius, Math.min(worldW - radius, x));
        y = Math.max(radius, Math.min(worldH - radius, y));

        if (hurtCooldown > 0) hurtCooldown -= dt;
        if (muzzleFlash > 0) muzzleFlash -= dt;
        getWeapon().update(dt);
    }

    /** Move then push out of any overlapping obstacle (X and Y handled separately). */
    private void moveWithCollision(double dx, double dy, List<Rectangle> obstacles) {
        x += dx;
        y += dy;
        for (Rectangle r : obstacles) {
            // Closest point on rect to circle center:
            double cx = Math.max(r.x, Math.min(x, r.x + r.width));
            double cy = Math.max(r.y, Math.min(y, r.y + r.height));
            double ox = x - cx, oy = y - cy;
            double distSq = ox * ox + oy * oy;
            if (distSq < radius * radius) {
                if (distSq == 0) {
                    // Center inside rect: push out along smallest penetration axis.
                    double left = x - r.x, right = r.x + r.width - x;
                    double top = y - r.y, bottom = r.y + r.height - y;
                    double m = Math.min(Math.min(left, right), Math.min(top, bottom));
                    if (m == left) x = r.x - radius;
                    else if (m == right) x = r.x + r.width + radius;
                    else if (m == top) y = r.y - radius;
                    else y = r.y + r.height + radius;
                } else {
                    double dist = Math.sqrt(distSq);
                    x = cx + ox / dist * radius;
                    y = cy + oy / dist * radius;
                }
            }
        }
    }

    public void setAim(double worldMouseX, double worldMouseY) {
        aimAngle = Math.atan2(worldMouseY - y, worldMouseX - x);
    }

    public boolean canTakeDamage() {
        return hurtCooldown <= 0;
    }

    /** Returns actual damage applied (0 if invulnerable). */
    public int takeDamage(int dmg) {
        if (hurtCooldown > 0) return 0;
        hp -= dmg;
        if (hp < 0) hp = 0;
        hurtCooldown = 0.6; // brief mercy invulnerability
        return dmg;
    }

    public void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public void reset(double x, double y) {
        this.x = x;
        this.y = y;
        hp = maxHp;
        weapons.clear();
        weapons.add(Weapon.createPistol());
        currentWeapon = 0;
        hurtCooldown = 0;
        muzzleFlash = 0;
    }

    /** Gun tip in world space (bullets + muzzle flash spawn here). */
    public double gunTipX() { return x + Math.cos(aimAngle) * (radius + 12); }
    public double gunTipY() { return y + Math.sin(aimAngle) * (radius + 12); }

    /** Draw the player: shadow, body circle, direction gun, muzzle flash. */
    public void draw(Graphics2D g) {
        // --- SPRITE SWAP POINT ---
        // Image img = loadSprite("assets/player.png"); if (img != null) { draw rotated img; return; }
        // Shapes below are the no-asset fallback and always work.

        // Shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval((int) (x - radius + 2), (int) (y - radius + 4),
                   (int) (radius * 2), (int) (radius * 2));

        // Body (dark outline + teal fill, red ring flashes while invulnerable)
        double bob = moving ? Math.sin(walkPhase) * 1.5 : 0;
        g.setColor(new Color(20, 90, 90));
        g.fillOval((int) (x - radius), (int) (y - radius + bob * 0.3),
                   (int) (radius * 2), (int) (radius * 2));
        g.setColor(new Color(80, 220, 200));
        g.fillOval((int) (x - radius + 3), (int) (y - radius + 3 + bob * 0.3),
                   (int) (radius * 2 - 6), (int) (radius * 2 - 6));
        if (hurtCooldown > 0.35) {
            g.setColor(new Color(255, 60, 60, 180));
            g.setStroke(new BasicStroke(2));
            g.drawOval((int) (x - radius - 1), (int) (y - radius - 1),
                       (int) (radius * 2 + 2), (int) (radius * 2 + 2));
        }

        // Gun (rotates with aim)
        double gx = x + Math.cos(aimAngle) * (radius + 6);
        double gy = y + Math.sin(aimAngle) * (radius + 6);
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(30, 30, 35));
        g.drawLine((int) x, (int) y, (int) gx, (int) gy);
        // Hands / facing dot
        g.setColor(new Color(240, 230, 200));
        g.fillOval((int) (x + Math.cos(aimAngle) * 6 - 4), (int) (y + Math.sin(aimAngle) * 6 - 4), 8, 8);

        // Muzzle flash: bright star at the gun tip, lasts ~0.05s after each shot
        if (muzzleFlash > 0) {
            double tx = gunTipX(), ty = gunTipY();
            g.setColor(new Color(255, 230, 130, 230));
            int r = 12;
            g.fillOval((int) (tx - r / 2), (int) (ty - r / 2), r, r);
            g.setColor(new Color(255, 150, 40, 200));
            g.setStroke(new BasicStroke(2));
            for (int i = 0; i < 4; i++) {
                double a = aimAngle + (i - 1.5) * 0.5;
                g.drawLine((int) tx, (int) ty,
                           (int) (tx + Math.cos(a) * (r + 6)), (int) (ty + Math.sin(a) * (r + 6)));
            }
        }
    }
}
