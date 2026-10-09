// Main.java — Entry point. Opens the window and hosts the GamePanel.
// Compile:  javac -d out src/*.java
// Run:      java -cp out Main
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Build all Swing UI on the Event Dispatch Thread (thread-safe practice).
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Zombie Survival");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel panel = new GamePanel();
            frame.add(panel);
            frame.pack(); // sizes window to panel's preferred size (960x640)
            frame.setLocationRelativeTo(null); // center on screen
            frame.setVisible(true);

            panel.requestFocusInWindow();
            panel.startLoop(); // kicks off the 60 FPS fixed-timestep loop
        });
    }
}
