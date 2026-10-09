// Zombie.java — Enemy that chases the player.
// Four types: NORMAL (balanced), FAST (weak, quick), TANK (slow bruiser),
// BOSS (every 5th wave: huge, high HP, big score). Uses circle collision.
// takeDamage() applies knockback velocity that decays in update().
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Random;

public class Zombie {
    public enum Type { NORMAL, FAST, TANK, BOSS }

    public Type type;
    public double x, y;
    public double radius;
    public int hp, maxHp;
    public double speed;
    public int damage;          // Contact damage per hit
    public int scoreValue;      // Score awarded on kill
    public boolean dead = false;

    // Knockback velocity added on bullet hit, decays exponentially.
    private double kbx = 0, kby = 0;
    // Per-zombie attack cooldown so contact DPS stays fair.
    private double attackTimer = 0;
    // Wobble animation phase so movement looks alive.
    private double wobble;
    private static final Random RAND = new Random();

    public Zombie(Type type, double x, double y, int wave) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.wobble = RAND.nextDouble() * Math.PI * 2;
        // Wave scaling: +4% speed and +8% HP per wave (tuned to stay fair).
        double speedScale = 1.0 + (wave - 1) * 0.04;
        double hpScale = 1.0 + (wave - 1) * 0.08;
        switch (type) {
            case FAST:
                radius = 11;
                maxHp = hp = (int) (35 * hpScale);
                speed = 165 * speedScale;
                damage = 8;
                scoreValue = 150;
                break;
            case TANK:
                radius = 20;
                maxHp = hp = (int) (140 * hpScale);
                speed = 70 * speedScale;
                damage = 20;
                scoreValue = 250;
                break;
            case BOSS:
                radius = 30;
                maxHp = hp = (int) (900 * (1 + (wave / 5 - 1) * 0.35));
                speed = 62 * speedScale;
                damage = 30;
                scoreValue = 1000;
                break;
            case NORMAL:
            default:
                radius = 14;
                maxHp = hp = (int) (60 * hpScale);
                speed = 105 * speedScale;
                damage = 12;
                scoreValue = 100;
                break;
        }
    }

    /** Chase the player, apply knockback decay, resolve obstacle collision. */
    public void update(double dt, double px, double py, List<Rectangle> obstacles) {
        if (dead) return;
        wobble += dt * 6;
        if (attackTimer > 0) attackTimer -= dt;

        double dx = px - x, dy = py - y;
        double dist = Math.hypot(dx, dy);
        if (dist > 1) {
            double vx = dx / dist * speed + kbx;
            double vy = dy / dist * speed + kby;
            // X then Y so zombies slide along crates instead of sticking.
            x += vx * dt;
            resolveObstacles(obstacles);
            y += vy * dt;
            resolveObstacles(obstacles);
        } else {
            x += kbx * dt;
            y += kby * dt;
        }
        // Knockback fades fast (~87% lost per second at 2.0 drag... tuned by feel).
        double d = Math.exp(-6.0 * dt);
        kbx *= d;
        kby *= d;
    }

    private void resolveObstacles(List<Rectangle> obstacles) {
        for (Rectangle r : obstacles) {
            double cx = Math.max(r.x, Math.min(x, r.x + r.width));
            double cy = Math.max(r.y, Math.min(y, r.y + r.height));
            double ox = x - cx, oy = y - cy;
            double dsq = ox * ox + oy * oy;
            if (dsq < radius * radius && dsq > 0) {
                double d = Math.sqrt(dsq);
                x = cx + ox / d * radius;
                y = cy + oy / d * radius;
            }
        }
    }

    /** Deal damage; knockX/Y is the bullet direction * weapon knockback. Returns true if killed. */
    public boolean takeDamage(int dmg, double knockX, double knockY) {
        if (dead) return false;
        hp -= dmg;
        // Cap knockback so shotguns shove but bosses barely budge.
        double mass = (type == Type.BOSS) ? 0.25 : (type == Type.TANK ? 0.5 : 1.0);
        kbx += knockX * mass;
        kby += knockY * mass;
        if (hp <= 0) {
            hp = 0;
            dead = true;
            return true;
        }
        return false;
    }

    public boolean canAttack() { return attackTimer <= 0; }
    public void resetAttackCooldown() { attackTimer = 1.0; }

    /** Circle-vs-circle overlap test with the player. */
    public boolean touches(double px, double py, double pr) {
        double dx = px - x, dy = py - y;
        double rr = pr + radius;
        return dx * dx + dy * dy < rr * rr;
    }

    /** Draw body, glowing eyes facing the player, HP bar if hurt, boss crown. */
    public void draw(Graphics2D g, double px, double py) {
        // --- SPRITE SWAP POINT ---
        // Drop assets/zombie_normal.png etc. next to the JAR and draw rotated
        // images here; the shape code below stays as fallback.

        double wob = Math.sin(wobble) * 2;
        Color body, dark;
        switch (type) {
            case FAST:  body = new Color(160, 220, 90);  dark = new Color(90, 140, 40); break;
            case TANK:  body = new Color(150, 90, 180);  dark = new Color(95, 55, 115); break;
            case BOSS:  body = new Color(200, 50, 45);   dark = new Color(120, 25, 22); break;
            default:    body = new Color(110, 170, 80);  dark = new Color(60, 105, 45); break;
        }
        // Shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval((int) (x - radius + 2), (int) (y - radius + 5),
                   (int) (radius * 2), (int) (radius * 2));
        // Body + belly
        g.setColor(dark);
        g.fillOval((int) (x - radius), (int) (y - radius + wob * 0.3),
                   (int) (radius * 2), (int) (radius * 2));
        g.setColor(body);
        g.fillOval((int) (x - radius + 3), (int) (y - radius + 3 + wob * 0.3),
                   (int) (radius * 2 - 6), (int) (radius * 2 - 6));
        // Eyes look at the player
        double ang = Math.atan2(py - y, px - x);
        double ex = Math.cos(ang) * radius * 0.4, ey = Math.sin(ang) * radius * 0.4;
        double perpX = -Math.sin(ang) * radius * 0.32, perpY = Math.cos(ang) * radius * 0.32;
        g.setColor(new Color(255, 240, 180));
        int er = Math.max(3, (int) (radius * 0.22));
        g.fillOval((int) (x + ex + perpX - er / 2), (int) (y + ey + perpY - er / 2), er, er);
        g.fillOval((int) (x + ex - perpX - er / 2), (int) (y + ey - perpY - er / 2), er, er);
        g.setColor(new Color(160, 10, 10));
        g.fillOval((int) (x + ex + perpX - 2), (int) (y + ey + perpY - 2), 4, 4);
        g.fillOval((int) (x + ex - perpX - 2), (int) (y + ey - perpY - 2), 4, 4);

        if (type == Type.BOSS) {
            // Spikes / crown
            g.setColor(new Color(60, 10, 10));
            g.setStroke(new BasicStroke(3));
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0 * Math.PI * 2 + wobble * 0.2;
                g.drawLine((int) (x + Math.cos(a) * radius * 0.8), (int) (y + Math.sin(a) * radius * 0.8),
                           (int) (x + Math.cos(a) * radius * 1.25), (int) (y + Math.sin(a) * radius * 1.25));
            }
        }

        // HP bar above head (only when damaged)
        if (hp < maxHp) {
            int w = (int) (radius * 2.4);
            int h = type == Type.BOSS ? 7 : 5;
            int bx = (int) (x - w / 2), by = (int) (y - radius - h - 7);
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(bx, by, w, h);
            double f = (double) hp / maxHp;
            g.setColor(f > 0.5 ? new Color(90, 220, 90) : f > 0.25 ? new Color(240, 200, 60) : new Color(230, 60, 50));
            g.fillRect(bx + 1, by + 1, (int) ((w - 2) * f), h - 2);
        }
    }
}
