// ParticleSystem.java — Tiny pooled particle engine for game feel.
// Used for: blood splatter (zombie hit/death), muzzle flash sparks,
// pickup sparkles, wall impact puffs and boss-spawn shockwaves.
// Particles have position, velocity, drag, life and shrinking size.
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem {

    /** One particle. Plain data object, updated by ParticleSystem. */
    public static class Particle {
        double x, y, vx, vy, life, maxLife, size;
        Color color;
        double drag;      // 0 = no drag, higher = slows faster
        boolean shrink;   // shrink as life runs out
        boolean glow;     // draw brighter (additive feel via lighter color)
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random rand = new Random();
    private static final int MAX_PARTICLES = 900; // cap so effects can't lag the game

    public void clear() {
        particles.clear();
    }

    public int count() {
        return particles.size();
    }

    private void add(double x, double y, double vx, double vy,
                     double life, double size, Color color,
                     double drag, boolean shrink, boolean glow) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.remove(0); // drop oldest to stay under budget
        }
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy;
        p.life = life; p.maxLife = life; p.size = size;
        p.color = color; p.drag = drag; p.shrink = shrink; p.glow = glow;
        particles.add(p);
    }

    /** Red blood burst when a zombie is hit or killed. */
    public void spawnBlood(double x, double y, int amount) {
        Color[] reds = { new Color(180, 20, 20), new Color(220, 40, 30),
                         new Color(140, 10, 10), new Color(255, 90, 70) };
        for (int i = 0; i < amount; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 40 + rand.nextDouble() * 220;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                0.3 + rand.nextDouble() * 0.5, 2 + rand.nextDouble() * 4,
                reds[rand.nextInt(reds.length)], 3.0, true, false);
        }
    }

    /** Yellow-white flash + sparks at the gun muzzle. */
    public void spawnMuzzle(double x, double y, double angle) {
        for (int i = 0; i < 6; i++) {
            double a = angle + (rand.nextDouble() - 0.5) * 0.9;
            double sp = 120 + rand.nextDouble() * 260;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                0.08 + rand.nextDouble() * 0.12, 2 + rand.nextDouble() * 3,
                (i % 2 == 0) ? new Color(255, 220, 120) : new Color(255, 150, 40),
                4.0, true, true);
        }
    }

    /** Grey puff when a bullet hits a wall / crate. */
    public void spawnImpact(double x, double y) {
        for (int i = 0; i < 5; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 30 + rand.nextDouble() * 120;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                0.2 + rand.nextDouble() * 0.25, 1.5 + rand.nextDouble() * 2.5,
                new Color(180, 180, 170, 220), 4.0, true, false);
        }
    }

    /** Green cross sparkle when a pickup is collected. */
    public void spawnPickupFx(double x, double y, Color color) {
        for (int i = 0; i < 12; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 50 + rand.nextDouble() * 150;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                0.3 + rand.nextDouble() * 0.3, 2 + rand.nextDouble() * 3,
                color, 2.5, true, true);
        }
    }

    /** Expanding ring of debris for boss spawns. */
    public void spawnShockwave(double x, double y) {
        for (int i = 0; i < 40; i++) {
            double a = (i / 40.0) * Math.PI * 2;
            double sp = 180 + rand.nextDouble() * 120;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                0.5 + rand.nextDouble() * 0.4, 3 + rand.nextDouble() * 4,
                new Color(255, 80, 60), 2.0, true, true);
        }
    }

    /** Advance all particles. Removes dead ones (swap-remove for speed). */
    public void update(double dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                // swap-remove: cheap O(1) delete
                int last = particles.size() - 1;
                particles.set(i, particles.get(last));
                particles.remove(last);
                continue;
            }
            // drag: exponential slow-down
            double d = 1.0 - Math.min(1.0, p.drag * dt);
            p.vx *= d;
            p.vy *= d;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
    }

    /** Draw every particle as a fading circle. Called in world space (after camera shake). */
    public void draw(Graphics2D g) {
        for (Particle p : particles) {
            float alpha = (float) Math.max(0, Math.min(1, p.life / p.maxLife));
            double s = p.shrink ? p.size * alpha + 0.5 : p.size;
            Color c = p.color;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)));
            g.fillOval((int) (p.x - s / 2), (int) (p.y - s / 2), (int) s, (int) s);
        }
    }
}
