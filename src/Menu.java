// Menu.java — Title / pause / game-over screens.
// GamePanel owns the GameState; this class only draws the overlays and the
// clickable buttons (hit-tested by GamePanel's mouse handler).
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class Menu {
    // Buttons are exposed so GamePanel can hit-test clicks.
    public final Rectangle btnStart = new Rectangle(0, 0, 240, 52);
    public final Rectangle btnQuit = new Rectangle(0, 0, 240, 52);
    public final Rectangle btnRestart = new Rectangle(0, 0, 260, 52);
    public final Rectangle btnMenu = new Rectangle(0, 0, 260, 44);

    private final Font title = new Font("SansSerif", Font.BOLD, 64);
    private final Font sub = new Font("SansSerif", Font.PLAIN, 18);
    private final Font btn = new Font("SansSerif", Font.BOLD, 22);
    private final Font small = new Font("SansSerif", Font.PLAIN, 14);

    /** Layout buttons relative to current window size (call each frame before draw/click). */
    public void layout(int w, int h) {
        btnStart.setLocation(w / 2 - 120, h / 2 + 20);
        btnQuit.setLocation(w / 2 - 120, h / 2 + 84);
        btnRestart.setLocation(w / 2 - 130, h / 2 + 60);
        btnMenu.setLocation(w / 2 - 130, h / 2 + 124);
    }

    public void drawMain(Graphics2D g, int w, int h, int highScore, int mouseX, int mouseY) {
        drawDim(g, w, h);
        g.setFont(title);
        String t = "ZOMBIE SURVIVAL";
        int tw = g.getFontMetrics().stringWidth(t);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(t, w / 2 - tw / 2 + 3, 173);
        g.setColor(new Color(180, 30, 30));
        g.drawString(t, w / 2 - tw / 2, 170);

        g.setFont(sub);
        g.setColor(new Color(220, 220, 220));
        center(g, "WASD move • Mouse aim • Click shoot • R reload • 1/2/3 guns • Esc pause", w, 210);
        center(g, "Survive the waves. Shotgun (wave 2) and Rifle (wave 3+) drop from zombies.", w, 234);
        g.setColor(new Color(255, 220, 120));
        center(g, "HIGH SCORE: " + highScore, w, 262);

        drawButton(g, btnStart, "START", mouseX, mouseY);
        drawButton(g, btnQuit, "QUIT", mouseX, mouseY);
        g.setFont(small);
        g.setColor(new Color(150, 150, 150));
        center(g, "No assets needed — everything is drawn with shapes.", w, h - 24);
    }

    public void drawPaused(Graphics2D g, int w, int h, int mouseX, int mouseY) {
        drawDim(g, w, h);
        g.setFont(title);
        g.setFont(g.getFont().deriveFont(48f));
        String t = "PAUSED";
        int tw = g.getFontMetrics().stringWidth(t);
        g.setColor(Color.WHITE);
        g.drawString(t, w / 2 - tw / 2, h / 2 - 60);
        g.setFont(sub);
        g.setColor(new Color(220, 220, 220));
        center(g, "Esc resume • Click START to resume • QUIT to exit", w, h / 2 - 24);
        drawButton(g, btnStart, "RESUME", mouseX, mouseY);
        drawButton(g, btnQuit, "QUIT", mouseX, mouseY);
    }

    public void drawGameOver(Graphics2D g, int w, int h, int wave, int kills,
                             int score, int highScore, boolean newBest, int mouseX, int mouseY) {
        drawDim(g, w, h);
        g.setFont(title);
        g.setFont(g.getFont().deriveFont(54f));
        String t = "YOU DIED";
        int tw = g.getFontMetrics().stringWidth(t);
        g.setColor(new Color(200, 30, 30));
        g.drawString(t, w / 2 - tw / 2, h / 2 - 90);

        g.setFont(sub);
        g.setColor(new Color(230, 230, 230));
        center(g, "Wave reached: " + wave + "    Kills: " + kills + "    Score: " + score, w, h / 2 - 48);
        g.setColor(new Color(255, 220, 120));
        center(g, "High score: " + highScore + (newBest ? "   ★ NEW BEST! ★" : ""), w, h / 2 - 20);

        drawButton(g, btnRestart, "RESTART (Enter)", mouseX, mouseY);
        drawButton(g, btnMenu, "MENU", mouseX, mouseY);
    }

    private void drawDim(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, w, h);
    }

    private void center(Graphics2D g, String s, int w, int y) {
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, w / 2 - sw / 2, y);
    }

    private void drawButton(Graphics2D g, Rectangle r, String label, int mx, int my) {
        boolean hover = r.contains(mx, my);
        g.setColor(hover ? new Color(170, 40, 40) : new Color(90, 25, 25));
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(hover ? Color.WHITE : new Color(220, 220, 220));
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setFont(btn);
        int sw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, r.x + r.width / 2 - sw / 2, r.y + 34);
    }
}
