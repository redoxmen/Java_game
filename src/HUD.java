// HUD.java — All in-game overlay drawing (health, ammo, wave, score...).
// Pure static helpers: GamePanel calls HUD.draw(...) after the world render.
// Keeps UI layout code out of the game-logic class.
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class HUD {
    private static final Font BIG = new Font("SansSerif", Font.BOLD, 16);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font HUGE = new Font("SansSerif", Font.BOLD, 52);

    public static void draw(Graphics2D g, Player player, WaveManager waves,
                            int score, int kills, int zombiesRemaining, int w) {
        g.setFont(BIG);

        // --- Top-left: HP bar + weapon + ammo ---
        int hx = 14, hy = 14, hw = 220, hh = 20;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(hx - 4, hy - 4, hw + 8, 64);
        // HP bar (green→yellow→red)
        double f = (double) player.hp / player.maxHp;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(hx, hy, hw, hh);
        Color hpColor = f > 0.5 ? new Color(80, 220, 90) : f > 0.25 ? new Color(240, 200, 60) : new Color(230, 60, 50);
        g.setColor(hpColor);
        g.fillRect(hx + 1, hy + 1, (int) ((hw - 2) * f), hh - 2);
        g.setColor(Color.WHITE);
        g.setFont(SMALL);
        g.drawString("HP " + player.hp + "/" + player.maxHp, hx + 6, hy + 15);

        // Weapon + ammo line
        Weapon gun = player.getWeapon();
        String ammo = gun.infiniteReserve
                ? gun.magAmmo + " / ∞"
                : gun.magAmmo + " / " + gun.reserveAmmo;
        g.setFont(BIG);
        g.setColor(Color.WHITE);
        String owned = buildOwnedString(player);
        g.drawString(gun.name + "  [" + ammo + "]   " + owned, hx, hy + 40);
        // Reload progress bar
        if (gun.reloading) {
            int rw = (int) ((hw) * gun.getReloadProgress());
            g.setColor(new Color(255, 200, 80));
            g.fillRect(hx, hy + 46, rw, 5);
            g.setColor(Color.WHITE);
            g.setFont(SMALL);
            g.drawString("RELOADING...", hx + hw - 90, hy + 40);
        } else {
            g.setFont(SMALL);
            g.setColor(new Color(200, 200, 200));
            g.drawString("R reload • 1/2/3 weapons", hx, hy + 58);
        }

        // --- Top-right: wave / remaining / score / kills ---
        g.setFont(BIG);
        String waveStr = waves.wave == 0 ? "GET READY" : "WAVE " + waves.wave;
        drawRight(g, waveStr, w - 14, 28, Color.WHITE);
        g.setFont(SMALL);
        String rem = waves.inBreak ? "Next wave soon..." : "Zombies left: " + zombiesRemaining;
        drawRight(g, rem, w - 14, 46, new Color(255, 150, 120));
        drawRight(g, "Score: " + score, w - 14, 64, new Color(255, 230, 130));
        drawRight(g, "Kills: " + kills, w - 14, 80, new Color(200, 200, 200));

        // --- Wave banner (big center text at wave start) ---
        if (waves.bannerTimer > 0 && waves.wave > 0) {
            float a = (float) Math.min(1.0, waves.bannerTimer); // fade out
            g.setFont(HUGE);
            String msg = "WAVE " + waves.wave;
            int sw = g.getFontMetrics().stringWidth(msg);
            g.setColor(new Color(0, 0, 0, (int) (150 * a)));
            g.drawString(msg, w / 2 - sw / 2 + 3, 153);
            g.setColor(new Color(255, 70, 60, (int) (255 * a)));
            g.drawString(msg, w / 2 - sw / 2, 150);
            if (waves.bossWave) {
                g.setFont(BIG);
                String sub = "⚠ BOSS INCOMING ⚠";
                int sw2 = g.getFontMetrics().stringWidth(sub);
                g.setColor(new Color(255, 200, 60, (int) (255 * a)));
                g.drawString(sub, w / 2 - sw2 / 2, 180);
            }
        }
    }

    private static String buildOwnedString(Player p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.weapons.size(); i++) {
            if (i > 0) sb.append(" ");
            if (i == p.currentWeapon) sb.append("[");
            sb.append(i + 1).append(":").append(p.weapons.get(i).name);
            if (i == p.currentWeapon) sb.append("]");
        }
        return sb.toString();
    }

    private static void drawRight(Graphics2D g, String s, int rightX, int y, Color c) {
        int sw = g.getFontMetrics().stringWidth(s);
        g.setColor(c);
        g.drawString(s, rightX - sw, y);
    }
}
