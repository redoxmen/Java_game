// Bullet.java — A single projectile fired by the player.
// Uses circle collision (x, y, radius). Stores its previous position so
// GamePanel can draw a motion trail. Dies on wall hit, range expiry or zombie hit.
public class Bullet {
    public double x, y;       // Current center
    public double prevX, prevY; // Position last frame (for trail rendering)
    public double vx, vy;     // Velocity in px/sec
    public int damage;        // Damage dealt on hit
    public double knockback;  // Knockback passed through to the zombie
    public double radius = 4;
    public double life = 1.6; // Seconds before the bullet fizzles out
    public boolean dead = false;

    public Bullet(double x, double y, double angle, double speed, int damage, double knockback) {
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
        this.damage = damage;
        this.knockback = knockback;
    }

    /** Move the bullet. Wall / obstacle checks happen in GamePanel. */
    public void update(double dt) {
        prevX = x;
        prevY = y;
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        if (life <= 0) dead = true;
    }
}
