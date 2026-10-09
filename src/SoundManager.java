// SoundManager.java — Optional sound effects via javax.sound.sampled.
// Looks for .wav files in the /sounds folder (next to the working dir or JAR).
// If a file is missing or broken, it silently does nothing — the game never
// crashes because of audio. Sounds are played on a background thread so the
// game loop never hitches.
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundManager {
    // Daemon thread pool: at most 4 overlapping sounds, threads don't block exit.
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sound");
        t.setDaemon(true);
        return t;
    });

    /** Play sounds/shoot.wav etc. name = filename without extension ("shoot"). */
    public static void play(String name) {
        POOL.execute(() -> {
            try {
                // Try working-dir sounds/ first, then parent-relative fallback.
                File f = new File("sounds/" + name + ".wav");
                if (!f.exists()) f = new File("src/../sounds/" + name + ".wav");
                if (!f.exists()) return; // silent if missing — by design
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.start();
                    // Let the clip finish before closing (else sound cuts off).
                    Thread.sleep(1200);
                    clip.close();
                }
            } catch (Exception ignored) {
                // Any audio failure is swallowed: game runs silently.
            }
        });
    }

    // Named helpers so GamePanel reads clearly.
    public static void shoot() { play("shoot"); }
    public static void shotgun() { play("shotgun"); }
    public static void hit() { play("hit"); }
    public static void zombieDie() { play("zombiedie"); }
    public static void hurt() { play("hurt"); }
    public static void pickup() { play("pickup"); }
    public static void reload() { play("reload"); }
    public static void wave() { play("wave"); }
    public static void boss() { play("boss"); }
    public static void gameover() { play("gameover"); }
}
