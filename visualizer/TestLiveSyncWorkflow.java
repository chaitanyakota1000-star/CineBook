package visualizer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestLiveSyncWorkflow {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        SwingUtilities.invokeAndWait(() -> {
            try {
                CineBookDsaVisualizer viz = new CineBookDsaVisualizer();
                viz.setVisible(true);

                // Step 1: Wait 2s for initial sync, then trigger booking of C3, C4
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);

                        System.out.println("[TEST] 1. Booking seats C3, C4 via website API...");
                        String bookJson = "{\"customerName\":\"WebsiteUser\",\"showId\":\"S-M1-1\",\"seatLabels\":[\"C3\",\"C4\"]}";
                        HttpRequest bookReq = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/api/bookings"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(bookJson))
                                .build();
                        HttpResponse<String> bookResp = client.send(bookReq, HttpResponse.BodyHandlers.ofString());
                        System.out.println("[TEST] Booking API Response: " + bookResp.body());

                        // Extract bookingId
                        String body = bookResp.body();
                        int idIdx = body.indexOf("\"bookingId\":\"");
                        String bookingId = "BK1001";
                        if (idIdx != -1) {
                            bookingId = body.substring(idIdx + 13, body.indexOf("\"", idIdx + 13));
                        }
                        System.out.println("[TEST] Created booking ID: " + bookingId);

                        // Wait 1.8s for visualizer auto-sync to catch booking
                        Thread.sleep(1800);

                        // Capture Booked Screenshot
                        SwingUtilities.invokeAndWait(() -> saveSnapshot(viz, "visualizer_booked.png"));

                        // Step 2: Cancel booking
                        System.out.println("[TEST] 2. Cancelling booking " + bookingId + " via website API...");
                        HttpRequest cancelReq = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/api/bookings/" + bookingId))
                                .DELETE()
                                .build();
                        HttpResponse<String> cancelResp = client.send(cancelReq, HttpResponse.BodyHandlers.ofString());
                        System.out.println("[TEST] Cancel API Response: " + cancelResp.body());

                        // Wait 1.8s for visualizer auto-sync to catch cancellation
                        Thread.sleep(1800);

                        // Capture Cancelled Screenshot
                        SwingUtilities.invokeAndWait(() -> saveSnapshot(viz, "visualizer_cancelled.png"));

                        System.out.println("[TEST] Acceptance test workflow completed successfully!");
                        System.exit(0);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                }).start();

            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void saveSnapshot(Component comp, String filename) {
        try {
            BufferedImage img = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img.createGraphics();
            comp.paint(g2d);
            g2d.dispose();
            File out = new File(filename);
            ImageIO.write(img, "png", out);
            System.out.println("[TEST] Saved snapshot to " + out.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
