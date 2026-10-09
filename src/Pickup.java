// Pickup.java — Collectibles on the ground.
// Types: AMMO (reserve bullets), HEALTH (+30 HP), WEAPON_SHOTGUN / WEAPON_RIFLE crates.
// Bob and pulse so they catch the eye. Lifetime expires so the map can't fill up.
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class Pickup {
    public enum Type { AMMO, HEALTH, WEAPON_SHOTGUN, WEAPON_RIFLE }

    public Type type;
    public double x, y;
    public double radius = 12;
    public double life = 25;   // seconds on the ground before despawning
    private double bob = 0;    // animation timer
    private static final java.util.Random RAND = new java.util.Random();

    public Pickup(Type type, double x, double y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.bob = RAND.nextDouble() * Math.PI * 2;
    }

    public void update(double dt) {
        bob += dt * 4;
        life -= dt;
    }

    public boolean isExpired() { return life <= 0; }

    public boolean touches(double px, double py, double pr) {
        double dx = px - x, dy = py - y;
        double rr = pr + radius + 4; // generous magnet radius
        return dx * dx + dy * dy < rr * rr;
    }

    /** Blinking when about to expire warns the player. */
    public boolean shouldBlink() {
        return life < 5 && ((int) (bob * 4) % 2 == 0);
    }

    public void draw(Graphics2D g) {
        if (shouldBlink()) return; // skip frames to blink
        double by = Math.sin(bob) * 3;
        float pulse = (float) (0.75 + 0.25 * Math.sin(bob * 1.5));

        // Glow ring
        Color glow;
        switch (type) {
            case HEALTH: glow = new Color(80, 255, 120); break;
            case AMMO: glow = new Color(255, 210, 90); break;
            default: glow = new Color(140, 180, 255); break;
        }
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int) (70 * pulse)));
        g.fillOval((int) (x - 18), (int) (y - 18 + by), 36, 36);

        // Crate / box body
        int s = 22;
        int bx = (int) (x - s / 2), bYY = (int) (y - s / 2 + by);
        g.setColor(new Color(35, 32, 28));
        g.fillRect(bx - 1, bYY - 1, s + 2, s + 2);
        switch (type) {
            case HEALTH:
                g.setColor(new Color(230, 235, 230));
                g.fillRect(bx, bYY, s, s);
                g.setColor(new Color(220, 50, 50));
                g.fillRect(bx + s / 2 - 3, bYY + 4, 6, s - 8);
                g.fillRect(bx + 4, bYY + s / 2 - 3, s - 8, 6);
                break;
            case AMMO:
                g.setColor(new Color(200, 170, 90));
                g.fillRect(bx, bYY, s, s);
                g.setColor(new Color(60, 50, 30));
                g.setStroke(new BasicStroke(2));
                g.drawRect(bx + 3, bYY + 3, s - 6, s - 6);
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.drawString("AM", bx + 4, bYY + 15);
                break;
            default:
                // Weapon crates: blue steel box + gun glyph letter
                g.setColor(new Color(70, 100, 150));
                g.fillRect(bx, bYY, s, s);
                g.setColor(new Color(220, 230, 255));
                g.setStroke(new BasicStroke(2));
                g.drawRect(bx + 3, bYY + 3, s - 6, s - 6);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString(type == Type.WEAPON_SHOTGUN ? "S" : "R", bx + 7, bYY + 16);
                break;
        }
    }
}
