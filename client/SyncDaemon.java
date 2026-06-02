import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.HttpURLConnection;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.*;

public class SyncDaemon {
    
    // ⚠️ UPDATE THIS IF YOUR WSL IP CHANGED
    private static final String SERVER_LIST_URL = "http://172.22.54.83:8080/api/sync/list";
    private static final Path SYNC_DIR = Paths.get(System.getProperty("user.home"), "Desktop", "SyncFolder");
    
    private static TrayIcon trayIcon;

    public static void main(String[] args) {
        System.out.println("🚀 Starting SyncVault Daemon...");
        
        // 1. Initialize the UI
        if (SystemTray.isSupported()) {
            setupSystemTray();
        } else {
            System.out.println("⚠️ System Tray not supported on this OS.");
        }

        // 2. Start the Heartbeat Thread
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    syncFromServer();
                    Thread.sleep(10000); 
                } catch (InterruptedException e) { break; }
            }
        });
        heartbeat.start();

        // 3. Start the OS File Watcher
        FolderWatcher.startWatching();
    }

    // ==========================================
    // 🖥️ THE SYSTEM TRAY UI
    // ==========================================
    private static void setupSystemTray() {
        SystemTray tray = SystemTray.getSystemTray();
        Image image = createDefaultIcon();

        PopupMenu popup = new PopupMenu();
        
        MenuItem statusItem = new MenuItem("☁️ SyncVault: Active");
        statusItem.setEnabled(false); // Just for display
        
        MenuItem forceSyncItem = new MenuItem("🔄 Force Sync Now");
        forceSyncItem.addActionListener(e -> {
            System.out.println("⚡ Manual sync triggered from tray.");
            // Run the sync on a quick background thread so it doesn't freeze the menu
            new Thread(SyncDaemon::syncFromServer).start();
        });

        MenuItem exitItem = new MenuItem("❌ Exit SyncVault");
        exitItem.addActionListener(e -> {
            System.out.println("🛑 Shutting down SyncVault...");
            System.exit(0);
        });

        popup.add(statusItem);
        popup.addSeparator();
        popup.add(forceSyncItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "SyncVault", popup);
        trayIcon.setImageAutoSize(true);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("❌ TrayIcon could not be added.");
        }
    }

    // Draws a beautiful little Blue app icon dynamically
    private static Image createDefaultIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(0, 120, 215)); // Windows Accent Blue
        g2d.fillRoundRect(0, 0, 16, 16, 4, 4);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("S", 4, 13);
        g2d.dispose();
        return img;
    }

    // ==========================================
    // 🫀 THE HEARTBEAT (Cloud -> PC)
    // ==========================================
    private static void syncFromServer() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(SERVER_LIST_URL).toURL().openConnection();
            conn.setRequestMethod("GET");
            
            if (conn.getResponseCode() != 200) {
                System.out.println("⚠️ Heartbeat failed: Server returned Code " + conn.getResponseCode());
                return;
            }

            Set<String> cloudFiles = new HashSet<>();
            try (Scanner scanner = new Scanner(conn.getInputStream())) {
                while (scanner.hasNextLine()) {
                    String file = scanner.nextLine().trim();
                    if (!file.isEmpty()) cloudFiles.add(file);
                }
            }
            
            System.out.println("🫀 Heartbeat check... Cloud has " + cloudFiles.size() + " files.");

            for (String cloudFile : cloudFiles) {
                if (cloudFile.contains("Zone.Identifier") || cloudFile.contains(":")) continue;

                Path localPath = SYNC_DIR.resolve(cloudFile);
                if (!Files.exists(localPath)) {
                    System.out.println("🔎 Heartbeat found new cloud file: " + cloudFile);
                    FileReceiver.downloadFileFromServer(cloudFile);
                }
            }

            if (Files.exists(SYNC_DIR)) {
                Files.walk(SYNC_DIR)
                     .filter(Files::isRegularFile)
                     .forEach(localFile -> {
                         String relative = SYNC_DIR.relativize(localFile).toString().replace("\\", "/");
                         if (relative.endsWith(".tmp") || relative.startsWith("~") || relative.contains("Zone.Identifier")) return;

                         if (!cloudFiles.contains(relative)) {
                             System.out.println("🔎 Heartbeat noticed cloud deleted: " + relative);
                             FileReceiver.deleteFileLocally(relative);
                         }
                     });
            }
        } catch (Exception e) {
            System.out.println("❌ Heartbeat Network Error: " + e.getMessage());
        }
    }
}