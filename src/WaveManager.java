// WaveManager.java — Decides WHAT spawns and WHEN.
// Wave N: total = 5 + N*3 zombies; every 5th wave appends a boss.
// Composition shifts toward fast/tank later. Spawn interval shrinks each wave.
// Between waves there is a short shop/breather break with a "WAVE N" banner.
// The manager only QUEUES spawns — GamePanel does the actual edge placement
// (so spawning respects obstacles/screen shake code in one place).
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WaveManager {
    public int wave = 0;                 // Current wave number (0 = game not started)
    public boolean inBreak = true;       // True during the breather between waves
    public double breakTimer = 2.0;      // Countdown to next wave
    public double bannerTimer = 0;       // "WAVE N" banner display time left
    public boolean bossWave = false;     // True if current wave contains a boss

    private final List<Zombie.Type> spawnQueue = new ArrayList<>(); // not yet spawned
    private double spawnTimer = 0;
    private double spawnInterval = 1.2;
    private final Random rand = new Random();
    private boolean bossSpawnedFx = false; // ensures shake/sound fire once per boss

    public static final double BREAK_BETWEEN_WAVES = 4.0; // seconds of rest
    public static final double BANNER_TIME = 2.5;

    public WaveManager() {}

    public void reset() {
        wave = 0;
        inBreak = true;
        breakTimer = 2.0;
        bannerTimer = 0;
        bossWave = false;
        bossSpawnedFx = false;
        spawnQueue.clear();
        spawnTimer = 0;
    }

    /** Build the zombie list for wave N. Called the moment a break ends. */
    private void startWave(int n) {
        wave = n;
        spawnQueue.clear();
        bossWave = (n % 5 == 0);
        bossSpawnedFx = false;

        int total = 5 + n * 3; // wave 1 = 8, wave 5 = 20, wave 10 = 35 ...
        for (int i = 0; i < total; i++) {
            double r = rand.nextDouble();
            // Later waves skew toward fast + tank.
            double fastChance = Math.min(0.35, 0.15 + n * 0.02);
            double tankChance = Math.min(0.30, 0.08 + n * 0.02);
            if (r < fastChance) spawnQueue.add(Zombie.Type.FAST);
            else if (r < fastChance + tankChance) spawnQueue.add(Zombie.Type.TANK);
            else spawnQueue.add(Zombie.Type.NORMAL);
        }
        if (bossWave) {
            spawnQueue.add(Zombie.Type.BOSS); // boss always enters last — dramatic!
        }
        // Fast spawns later (min 0.35s gap so the player isn't insta-swamped).
        spawnInterval = Math.max(0.35, 1.3 - n * 0.08);
        spawnTimer = 0.3;
        inBreak = false;
        bannerTimer = BANNER_TIME;
    }

    /**
     * Tick the spawner.
     * @param dt seconds since last update
     * @param aliveCount zombies currently alive (wave ends when 0 + queue empty)
     * @param spawner callback that materializes one zombie of the given type
     */
    public void update(double dt, int aliveCount, Spawner spawner) {
        if (bannerTimer > 0) bannerTimer -= dt;

        if (inBreak) {
            breakTimer -= dt;
            if (breakTimer <= 0) {
                startWave(wave + 1);
                // Spawn the first few instantly so the wave opens with action.
                for (int i = 0; i < Math.min(3, spawnQueue.size()); i++) {
                    spawner.spawn(nextFromQueue());
                }
            }
            return;
        }

        // Mid-wave: drip-feed the queue.
        if (!spawnQueue.isEmpty()) {
            spawnTimer -= dt;
            if (spawnTimer <= 0) {
                spawnTimer = spawnInterval;
                // Spawn 1, or 2 at once on later waves for pressure.
                int batch = (wave >= 4 && spawnQueue.size() > 3) ? 2 : 1;
                for (int i = 0; i < batch && !spawnQueue.isEmpty(); i++) {
                    spawner.spawn(nextFromQueue());
                }
            }
        } else if (aliveCount == 0) {
            // Wave cleared → breather + banner next frame.
            inBreak = true;
            breakTimer = BREAK_BETWEEN_WAVES;
        }
    }

    private Zombie.Type nextFromQueue() {
        return spawnQueue.remove(0);
    }

    /** Zombies still waiting to spawn (shown in HUD as part of "remaining"). */
    public int queuedCount() { return spawnQueue.size(); }

    /** True once per boss wave — GamePanel uses it to trigger shake + sound. */
    public boolean consumeBossFx() {
        if (bossWave && !bossSpawnedFx && !inBreak && wave > 0) {
            // Only fire after the boss has actually spawned (queue drained of boss).
            boolean bossStillQueued = spawnQueue.contains(Zombie.Type.BOSS);
            if (!bossStillQueued) {
                bossSpawnedFx = true;
                return true;
            }
        }
        return false;
    }

    /** Callback interface so WaveManager stays decoupled from GamePanel lists. */
    public interface Spawner {
        void spawn(Zombie.Type type);
    }
}
