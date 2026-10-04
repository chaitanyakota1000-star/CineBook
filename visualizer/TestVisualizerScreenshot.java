package visualizer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class TestVisualizerScreenshot {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                CineBookDsaVisualizer viz = new CineBookDsaVisualizer();
                viz.setVisible(true);

                // Wait 2.5 seconds for data sync, then take a snapshot
                javax.swing.Timer timer = new javax.swing.Timer(2500, e -> {
                    try {
                        BufferedImage img = new BufferedImage(viz.getWidth(), viz.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = img.createGraphics();
                        viz.paint(g2d);
                        g2d.dispose();

                        File out = new File("visualizer_screenshot.png");
                        ImageIO.write(img, "png", out);
                        System.out.println("SNAPSHOT_SAVED: " + out.getAbsolutePath());
                        System.exit(0);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        });
    }
}
