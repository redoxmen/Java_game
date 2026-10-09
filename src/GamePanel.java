// GamePanel.java — The heart of the game.
// Owns the fixed-timestep loop (60 UPS), all entities, input, collisions,
// rendering (double-buffered via Swing), screen shake, vignette, high score.
//
// LOOP: run() accumulates real time and steps update(1/60) a fixed amount so
// gameplay speed is identical on any machine. repaint() then draws once.
//
// STATE MACHINE: MENU -> PLAYING <-> PAUSED -> GAMEOVER -> (restart|menu).
// Rendering is split: drawWorld() (shaken camera) + HUD + Menu overlay.
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JPanel;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    // ---- Game states ----
    public enum GameState { MENU, PLAYING, PAUSED, GAMEOVER }
    private GameState state = GameState.MENU;

    // ---- Fixed timestep ----
    private static final double STEP = 1.0 / 60.0; // 60 updates/sec
    private Thread loopThread;
    private volatile boolean running = false;

    // ---- World ----
    private Player player;
    private final List<Zombie> zombies = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final ParticleSystem particles = new ParticleSystem();
    private final List<Rectangle> obstacles = new ArrayList<>();
    private final WaveManager waves = new WaveManager();
    private final Menu menu = new Menu();
    private final Random rand = new Random();

    // ---- Meta ----
    private int score = 0;
    private int kills = 0;
    private int highScore = 0;
    private boolean newBest = false;
    private static final String HIGHSCORE_FILE = "highscore.txt";

    // ---- Input ----
    private final boolean[] keys = new boolean[256];
    private int mouseX = 0, mouseY = 0;
    private boolean firing = false; // left mouse held

    // ---- Feel ----
    private double shakeTrauma = 0;   // 0..1, decays; offset = trauma^2 * max
    private double shakeX = 0, shakeY = 0; // last frame's offset (for mouse->world)
    private double hurtFlash = 0;     // 1 on hit, fades; drives red overlay
    private int lastWaveSeen = 0;     // detects wave changes for crate spawns

    public GamePanel() {
        setPreferredSize(new Dimension(960, 640));
        setBackground(new Color(18, 18, 22));
        setDoubleBuffered(true); // Swing double buffering: no flicker
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        buildObstacles();
        player = new Player(480, 320);
        highScore = loadHighScore();
    }

    /** Crates + walls. Bullets die on them; player & zombies slide around them. */
    private void buildObstacles() {
        obstacles.clear();
        obstacles.add(new Rectangle(190, 140, 80, 80));   // crate cluster TL
        obstacles.add(new Rectangle(690, 130, 90, 60));   // crate TR
        obstacles.add(new Rectangle(420, 290, 120, 28));  // middle wall
        obstacles.add(new Rectangle(140, 450, 70, 70));   // crate BL
        obstacles.add(new Rectangle(720, 450, 100, 70));  // crate BR
    }

    // ================= LOOP =================

    public void startLoop() {
        if (running) return;
        running = true;
        loopThread = new Thread(this, "game-loop");
        loopThread.start();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        double acc = 0;
        final double nanoStep = STEP * 1_000_000_000.0;
        while (running) {
            long now = System.nanoTime();
            double frame = (now - last) / 1_000_000_000.0;
            last = now;
            // Clamp huge gaps (e.g. window dragged) so we don't spiral.
            if (frame > 0.25) frame = 0.25;
            acc += frame;
            int steps = 0;
            while (acc >= STEP && steps < 5) {
                update(STEP);
                acc -= STEP;
                steps++;
            }
            repaint();
            // Yield to keep CPU reasonable; loop timing stays via accumulator.
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /** One fixed update. Only PLAYING advances the world. */
    private void update(double dt) {
        menu.layout(getWidth(), getHeight());
        // Decay feel timers in every state so overlays fade nicely.
        if (hurtFlash > 0) hurtFlash = Math.max(0, hurtFlash - dt * 1.8);
        shakeTrauma = Math.max(0, shakeTrauma - dt * 1.6);

        if (state != GameState.PLAYING) return;

        int w = Math.max(320, getWidth());
        int h = Math.max(240, getHeight());

        // World-space mouse (subtract last frame's shake so aim stays stable).
        double wmx = mouseX - shakeX;
        double wmy = mouseY - shakeY;
        player.setAim(wmx, wmy);
        player.update(dt, keys, obstacles, w, h);

        handleShooting();
        updateBullets(dt, w, h);
        updateZombies(dt);
        updatePickups(dt);
        particles.update(dt);

        // Waves spawn via callback into spawnZombie().
        waves.update(dt, zombies.size(), this::spawnZombie);
        // Boss entrance drama: shake + sound + shockwave, once per boss wave.
        if (waves.consumeBossFx()) {
            addShake(1.0);
            SoundManager.boss();
            Zombie boss = findBoss();
            if (boss != null) particles.spawnShockwave(boss.x, boss.y);
        }
        // Guaranteed weapon crates so progression never relies on luck:
        // wave 2 -> shotgun, wave 3 -> rifle (if not owned yet).
        if (waves.wave != lastWaveSeen) {
            lastWaveSeen = waves.wave;
            if (waves.wave == 2 && !player.ownsWeapon(Weapon.SHOTGUN)) {
                dropPickup(Pickup.Type.WEAPON_SHOTGUN, w / 2.0 + 80, h / 2.0 - 60);
            }
            if (waves.wave == 3 && !player.ownsWeapon(Weapon.RIFLE)) {
                dropPickup(Pickup.Type.WEAPON_RIFLE, w / 2.0 - 80, h / 2.0 + 60);
            }
            if (waves.wave > 0) SoundManager.wave();
        }

        if (player.isDead()) {
            gameOver();
        }
    }

    // ================= SHOOTING =================

    private void handleShooting() {
        Weapon gun = player.getWeapon();
        if (firing && gun.canFire()) {
            double tx = player.gunTipX(), ty = player.gunTipY();
            for (int i = 0; i < gun.pellets; i++) {
                // Each pellet gets independent random spread.
                double a = player.aimAngle + (rand.nextDouble() - 0.5) * 2 * gun.spread;
                bullets.add(new Bullet(tx, ty, a, gun.bulletSpeed, gun.damage, gun.knockback));
            }
            gun.onFired();
            player.muzzleFlash = 0.06;
            particles.spawnMuzzle(tx, ty, player.aimAngle);
            addShake(gun.shake);
            if (gun.id == Weapon.SHOTGUN) SoundManager.shotgun();
            else SoundManager.shoot();
            if (gun.reloading) SoundManager.reload();
        }
    }

    private void updateBullets(double dt, int w, int h) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(dt);
            boolean remove = b.dead;

            // Off-screen: despawn.
            if (!remove && (b.x < -20 || b.y < -20 || b.x > w + 20 || b.y > h + 20)) {
                remove = true;
            }
            // Obstacle hit: circle-vs-rectangle. Spark + despawn.
            if (!remove && hitsObstacle(b.x, b.y, b.radius)) {
                particles.spawnImpact(b.x, b.y);
                remove = true;
            }
            // Zombie hit: circle-vs-circle. First zombie hit consumes the bullet.
            if (!remove) {
                for (Zombie z : zombies) {
                    if (z.dead) continue;
                    double dx = z.x - b.x, dy = z.y - b.y;
                    double rr = z.radius + b.radius;
                    if (dx * dx + dy * dy < rr * rr) {
                        double blen = Math.hypot(b.vx, b.vy);
                        double nx = (blen > 0) ? b.vx / blen : 1;
                        double ny = (blen > 0) ? b.vy / blen : 0;
                        boolean killed = z.takeDamage(b.damage, nx * b.knockback, ny * b.knockback);
                        particles.spawnBlood(b.x, b.y, killed ? 26 : 10);
                        SoundManager.hit();
                        if (killed) {
                            onZombieKilled(z);
                        }
                        remove = true;
                        break;
                    }
                }
            }
            if (remove) {
                // swap-remove
                int last = bullets.size() - 1;
                bullets.set(i, bullets.get(last));
                bullets.remove(last);
            }
        }
        // Purge the dead zombies once per frame (keeps iteration safe above).
        for (int i = zombies.size() - 1; i >= 0; i--) {
            if (zombies.get(i).dead) {
                int last = zombies.size() - 1;
                zombies.set(i, zombies.get(last));
                zombies.remove(last);
            }
        }
    }

    private void onZombieKilled(Zombie z) {
        kills++;
        score += z.scoreValue;
        SoundManager.zombieDie();
        if (z.type == Zombie.Type.BOSS) {
            addShake(0.8);
            score += 500; // boss bonus
        }
        maybeDrop(z);
    }

    /** Roll pickup drops on kill. Weapon crates are rare; ammo/health common. */
    private void maybeDrop(Zombie z) {
        double r = rand.nextDouble();
        // Higher waves drop slightly more often (keeps long runs supplied).
        double bonus = Math.min(0.05, waves.wave * 0.004);
        if (r < 0.10 + bonus) {
            dropPickup(Pickup.Type.AMMO, z.x, z.y);
        } else if (r < 0.16 + bonus) {
            dropPickup(Pickup.Type.HEALTH, z.x, z.y);
        } else if (r < 0.185) {
            // Weapon unlocks: shotgun first, then rifle.
            if (!player.ownsWeapon(Weapon.SHOTGUN)) dropPickup(Pickup.Type.WEAPON_SHOTGUN, z.x, z.y);
            else if (!player.ownsWeapon(Weapon.RIFLE)) dropPickup(Pickup.Type.WEAPON_RIFLE, z.x, z.y);
            else dropPickup(Pickup.Type.AMMO, z.x, z.y);
        }
    }

    private void dropPickup(Pickup.Type t, double x, double y) {
        int w = Math.max(320, getWidth());
        int h = Math.max(240, getHeight());
        x = Math.max(24, Math.min(w - 24, x));
        y = Math.max(24, Math.min(h - 24, y));
        pickups.add(new Pickup(t, x, y));
    }

    // ================= ZOMBIES =================

    /** Spawn one zombie at a random screen edge (WaveManager callback). */
    private void spawnZombie(Zombie.Type type) {
        int w = Math.max(320, getWidth());
        int h = Math.max(240, getHeight());
        int edge = rand.nextInt(4);
        double x = 0, y = 0;
        int m = 24; // inset from the edge
        switch (edge) {
            case 0: x = m + rand.nextDouble() * (w - 2 * m); y = m; break;               // top
            case 1: x = m + rand.nextDouble() * (w - 2 * m); y = h - m; break;           // bottom
            case 2: x = m; y = m + rand.nextDouble() * (h - 2 * m); break;               // left
            default: x = w - m; y = m + rand.nextDouble() * (h - 2 * m); break;          // right
        }
        // Nudge out of obstacles so nothing spawns stuck inside a crate.
        for (int tries = 0; tries < 8 && pointInObstacles(x, y, 20); tries++) {
            x = m + rand.nextDouble() * (w - 2 * m);
            y = m + rand.nextDouble() * (h - 2 * m);
        }
        zombies.add(new Zombie(type, x, y, Math.max(1, waves.wave)));
    }

    private void updateZombies(double dt) {
        for (Zombie z : zombies) {
            z.update(dt, player.x, player.y, obstacles);
            // Contact damage with per-zombie cooldown + player mercy invulnerability.
            if (!z.dead && z.canAttack() && z.touches(player.x, player.y, player.radius)) {
                int applied = player.takeDamage(z.damage);
                z.resetAttackCooldown();
                if (applied > 0) {
                    hurtFlash = 1.0;
                    addShake(0.3);
                    SoundManager.hurt();
                    particles.spawnBlood(player.x, player.y, 8);
                }
            }
        }
    }

    private Zombie findBoss() {
        for (Zombie z : zombies) {
            if (z.type == Zombie.Type.BOSS && !z.dead) return z;
        }
        return null;
    }

    // ================= PICKUPS =================

    private void updatePickups(double dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            p.update(dt);
            boolean remove = p.isExpired();
            if (!remove && p.touches(player.x, player.y, player.radius)) {
                applyPickup(p);
                remove = true;
            }
            if (remove) {
                int last = pickups.size() - 1;
                pickups.set(i, pickups.get(last));
                pickups.remove(last);
            }
        }
    }

    private void applyPickup(Pickup p) {
        SoundManager.pickup();
        switch (p.type) {
            case HEALTH:
                player.heal(30);
                score += 10;
                particles.spawnPickupFx(p.x, p.y, new Color(80, 255, 120));
                break;
            case AMMO: {
                int given = 0;
                for (Weapon w : player.weapons) {
                    if (w.id == Weapon.SHOTGUN) { w.addReserveAmmo(12); given++; }
                    if (w.id == Weapon.RIFLE) { w.addReserveAmmo(45); given++; }
                }
                if (given == 0) score += 25; // pistol-only: ammo becomes score
                else score += 10;
                particles.spawnPickupFx(p.x, p.y, new Color(255, 210, 90));
                break;
            }
            case WEAPON_SHOTGUN:
                if (player.unlockWeapon(Weapon.SHOTGUN)) score += 100;
                else player.getWeapon().addReserveAmmo(12);
                particles.spawnPickupFx(p.x, p.y, new Color(140, 180, 255));
                break;
            case WEAPON_RIFLE:
                if (player.unlockWeapon(Weapon.RIFLE)) score += 100;
                else player.getWeapon().addReserveAmmo(30);
                particles.spawnPickupFx(p.x, p.y, new Color(140, 180, 255));
                break;
        }
    }

    // ================= HELPERS =================

    private boolean hitsObstacle(double x, double y, double r) {
        for (Rectangle rc : obstacles) {
            double cx = Math.max(rc.x, Math.min(x, rc.x + rc.width));
            double cy = Math.max(rc.y, Math.min(y, rc.y + rc.height));
            double dx = x - cx, dy = y - cy;
            if (dx * dx + dy * dy < r * r) return true;
        }
        return false;
    }

    private boolean pointInObstacles(double x, double y, double pad) {
        for (Rectangle rc : obstacles) {
            if (x > rc.x - pad && x < rc.x + rc.width + pad
             && y > rc.y - pad && y < rc.y + rc.height + pad) return true;
        }
        return false;
    }

    private void addShake(double amount) {
        shakeTrauma = Math.min(1.0, shakeTrauma + amount);
    }

    // ================= FLOW =================

    private void startGame() {
        player.reset(getWidth() / 2.0, getHeight() / 2.0);
        zombies.clear();
        bullets.clear();
        pickups.clear();
        particles.clear();
        waves.reset();
        score = 0;
        kills = 0;
        newBest = false;
        lastWaveSeen = 0;
        shakeTrauma = 0;
        hurtFlash = 0;
        firing = false;
        state = GameState.PLAYING;
        requestFocusInWindow();
    }

    private void gameOver() {
        state = GameState.GAMEOVER;
        firing = false;
        particles.spawnBlood(player.x, player.y, 40);
        SoundManager.gameover();
        if (score > highScore) {
            highScore = score;
            newBest = true;
            saveHighScore();
        }
    }

    private int loadHighScore() {
        try {
            File f = new File(HIGHSCORE_FILE);
            if (!f.exists()) return 0;
            String s = new String(Files.readAllBytes(f.toPath())).trim();
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0; // missing / corrupt file -> start at 0, never crash
        }
    }

    private void saveHighScore() {
        try {
            Files.write(new File(HIGHSCORE_FILE).toPath(),
                        String.valueOf(highScore).getBytes());
        } catch (Exception ignored) {
            // High score is best-effort; gameplay never depends on it.
        }
    }

    // ================= RENDER =================

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        menu.layout(w, h);

        // Camera shake: random offset scaled by trauma² (feels punchy, settles fast).
        double mag = 14 * shakeTrauma * shakeTrauma;
        shakeX = (rand.nextDouble() * 2 - 1) * mag;
        shakeY = (rand.nextDouble() * 2 - 1) * mag;

        // --- World (shaken) ---
        Graphics2D wg = (Graphics2D) g.create();
        wg.translate(shakeX, shakeY);
        drawGround(wg, w, h);
        drawObstacles(wg);
        for (Pickup p : pickups) p.draw(wg);
        drawBullets(wg);
        for (Zombie z : zombies) z.draw(wg, player.x, player.y);
        if (state != GameState.GAMEOVER) player.draw(wg);
        else {
            // Death marker: fading blood pool is already particles; keep gun visible briefly.
        }
        particles.draw(wg);
        wg.dispose();

        // --- Atmosphere (unshaken screen space) ---
        drawVignette(g, w, h);
        if (hurtFlash > 0) {
            g.setColor(new Color(200, 20, 20, (int) (110 * hurtFlash)));
            g.fillRect(0, 0, w, h);
        }
        // Low-HP heartbeat pulse.
        if (state == GameState.PLAYING && player.hp <= 30 && player.hp > 0) {
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0);
            g.setColor(new Color(180, 0, 0, (int) (40 + 40 * pulse)));
            g.fillRect(0, 0, w, h);
        }

        // --- UI ---
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            int remaining = zombies.size() + waves.queuedCount();
            HUD.draw(g, player, waves, score, kills, remaining, w);
            if (state == GameState.PAUSED) {
                menu.drawPaused(g, w, h, mouseX, mouseY);
            }
        } else if (state == GameState.MENU) {
            // Lively backdrop: draw the empty arena behind the title.
            menu.drawMain(g, w, h, highScore, mouseX, mouseY);
        } else if (state == GameState.GAMEOVER) {
            int waveReached = Math.max(1, waves.wave);
            menu.drawGameOver(g, w, h, waveReached, kills, score, highScore, newBest, mouseX, mouseY);
        }

        g.dispose();
    }

    /** Dark two-tone tiles + subtle grid. Cheap, atmospheric, no assets needed. */
    private void drawGround(Graphics2D g, int w, int h) {
        g.setColor(new Color(26, 26, 30));
        g.fillRect(-20, -20, w + 40, h + 40);
        int tile = 48;
        for (int y = 0; y < h + tile; y += tile) {
            for (int x = 0; x < w + tile; x += tile) {
                boolean alt = ((x / tile) + (y / tile)) % 2 == 0;
                g.setColor(alt ? new Color(30, 30, 36) : new Color(24, 24, 28));
                g.fillRect(x, y, tile - 1, tile - 1);
            }
        }
        // Faint blood stains persist? (skipped — particles fade; keeps perf flat)
    }

    private void drawObstacles(Graphics2D g) {
        for (Rectangle r : obstacles) {
            boolean isWall = r.width > 100 && r.height < 40;
            // Shadow
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(r.x + 3, r.y + 4, r.width, r.height);
            if (isWall) {
                g.setColor(new Color(70, 70, 80));
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(new Color(100, 100, 115));
                g.fillRect(r.x, r.y, r.width, 6);
            } else {
                // Crate: wooden box + cross planks + metal corners.
                g.setColor(new Color(122, 84, 48));
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(new Color(96, 64, 36));
                g.setStroke(new BasicStroke(3));
                g.drawLine(r.x, r.y, r.x + r.width, r.y + r.height);
                g.drawLine(r.x + r.width, r.y, r.x, r.y + r.height);
                g.setColor(new Color(60, 60, 66));
                g.drawRect(r.x, r.y, r.width, r.height);
            }
        }
    }

    /** Bullets as glowing dots + motion-trail lines. */
    private void drawBullets(Graphics2D g) {
        for (Bullet b : bullets) {
            g.setColor(new Color(255, 220, 130, 140));
            g.setStroke(new BasicStroke(3));
            g.drawLine((int) b.prevX, (int) b.prevY, (int) b.x, (int) b.y);
            g.setColor(new Color(255, 240, 180));
            g.fillOval((int) (b.x - 3), (int) (b.y - 3), 6, 6);
        }
    }

    private void drawVignette(Graphics2D g, int w, int h) {
        Point2D center = new Point2D.Float(w / 2f, h / 2f);
        float radius = (float) (Math.hypot(w, h) / 2);
        float[] dist = { 0.55f, 1.0f };
        Color[] colors = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 150) };
        RadialGradientPaint paint = new RadialGradientPaint(center, radius, dist, colors);
        g.setPaint(paint);
        g.fillRect(0, 0, w, h);
    }

    // ================= INPUT =================

    @Override
    public void keyPressed(KeyEvent e) {
        int c = e.getKeyCode();
        if (c >= 0 && c < keys.length) keys[c] = true;
        if (c == KeyEvent.VK_ESCAPE) {
            if (state == GameState.PLAYING) state = GameState.PAUSED;
            else if (state == GameState.PAUSED) { state = GameState.PLAYING; requestFocusInWindow(); }
        }
        if (state == GameState.PLAYING) {
            if (c == KeyEvent.VK_R) { player.getWeapon().startReload(); SoundManager.reload(); }
            if (c == KeyEvent.VK_1) player.switchWeapon(0);
            if (c == KeyEvent.VK_2 && player.weapons.size() > 1) player.switchWeapon(1);
            if (c == KeyEvent.VK_3 && player.weapons.size() > 2) player.switchWeapon(2);
        } else if (state == GameState.MENU && (c == KeyEvent.VK_ENTER || c == KeyEvent.VK_SPACE)) {
            startGame();
        } else if (state == GameState.GAMEOVER && (c == KeyEvent.VK_ENTER || c == KeyEvent.VK_SPACE)) {
            startGame();
        } else if (state == GameState.PAUSED && c == KeyEvent.VK_ENTER) {
            state = GameState.PLAYING;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c >= 0 && c < keys.length) keys[c] = false;
    }

    @Override public void keyTyped(KeyEvent e) { }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        mouseX = e.getX();
        mouseY = e.getY();
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (state == GameState.PLAYING) {
                firing = true;
            } else if (state == GameState.MENU) {
                if (menu.btnStart.contains(mouseX, mouseY)) startGame();
                else if (menu.btnQuit.contains(mouseX, mouseY)) System.exit(0);
            } else if (state == GameState.PAUSED) {
                if (menu.btnStart.contains(mouseX, mouseY)) state = GameState.PLAYING;
                else if (menu.btnQuit.contains(mouseX, mouseY)) System.exit(0);
            } else if (state == GameState.GAMEOVER) {
                if (menu.btnRestart.contains(mouseX, mouseY)) startGame();
                else if (menu.btnMenu.contains(mouseX, mouseY)) {
                    state = GameState.MENU;
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) firing = false;
    }

    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }
}
