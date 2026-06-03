import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.HttpURLConnection;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.*;
import javax.swing.SwingUtilities;

public class SyncDaemon {

    private static TrayIcon trayIcon;

    public static void main(String[] args) {
        System.out.println("🚀 Starting SyncVault Daemon...");
        
        // 1. Initialize the UI
        if (SystemTray.isSupported()) {
            setupSystemTray();
        } else {
            System.out.println("⚠️ System Tray not supported on this OS.");
        }

        // 2. Start the Heartbeat Thread (Cloud -> PC)
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    syncFromServer();
                    Thread.sleep(10000); 
                } catch (InterruptedException e) { break; }
            }
        });
        heartbeat.start();

        // 3. Start the OS File Watcher (PC -> Cloud)
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

        MenuItem settingsItem = new MenuItem("⚙️ Settings");
        settingsItem.addActionListener(e -> {
            System.out.println("⚙️ Settings opened from Right-Click menu.");
            SwingUtilities.invokeLater(SettingsUI::openWindow);
        });
        
        MenuItem forceSyncItem = new MenuItem("🔄 Force Sync Now");
        forceSyncItem.addActionListener(e -> {
            System.out.println("⚡ Manual sync triggered from tray.");
            new Thread(SyncDaemon::syncFromServer).start();
        });

        MenuItem exitItem = new MenuItem("❌ Exit SyncVault");
        exitItem.addActionListener(e -> {
            System.out.println("🛑 Shutting down SyncVault...");
            System.exit(0);
        });

        popup.add(statusItem);
        popup.addSeparator();
        popup.add(settingsItem);
        popup.add(forceSyncItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "SyncVault", popup);
        trayIcon.setImageAutoSize(true);

        // 🖱️ THE FIX: Left-Click instantly opens the Settings UI
        trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    System.out.println("🖱️ Left-Click detected! Launching UI...");
                    SwingUtilities.invokeLater(SettingsUI::openWindow);
                }
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("❌ TrayIcon could not be added.");
        }
    }

    private static Image createDefaultIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(0, 120, 215)); 
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
        // Fetch variables dynamically from ConfigManager so they update if UI changes
        String serverListUrl = ConfigManager.SERVER_URL + "/api/sync/list";
        Path syncDir = Paths.get(ConfigManager.SYNC_DIR_PATH);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(serverListUrl).toURL().openConnection();
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

                Path localPath = syncDir.resolve(cloudFile);
                if (!Files.exists(localPath)) {
                    System.out.println("🔎 Heartbeat found new cloud file: " + cloudFile);
                    FileReceiver.downloadFileFromServer(cloudFile);
                }
            }

            if (Files.exists(syncDir)) {
                Files.walk(syncDir)
                     .filter(Files::isRegularFile)
                     .forEach(localFile -> {
                         String relative = syncDir.relativize(localFile).toString().replace("\\", "/");
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