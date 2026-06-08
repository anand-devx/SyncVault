// import java.net.HttpURLConnection;
// import java.net.URI;
// import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.*;
import javax.swing.SwingUtilities;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.UIManager;
import java.io.File;
// import com.formdev.flatlaf.FlatDarkLaf;

public class SyncDaemon {

    private static TrayIcon trayIcon;
    private static ModernTrayMenu modernMenu; // 🚨 Added our new Modern Menu engine
    public static volatile boolean isRunning = true;
    public static void main(String[] args) {
        System.out.println("🚀 Starting SyncVault Daemon...");
        // FlatDarkLaf.setup();
        try {
                // This makes your app use the Windows native UI components
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // If no credentials saved, show login window first
        if (ConfigManager.USERNAME == null || ConfigManager.USERNAME.equals("")) {
            AuthUI.showWindow(); // blocks until user logs in or registers
            return; 
        }
    
        // Try to login with saved credentials
        String response = FileSender.login(ConfigManager.USERNAME, ConfigManager.PASSWORD);
        if (ConfigManager.AUTH_TOKEN == null) {
            AuthUI.showWindow(); // credentials failed, show login again
            return;
        }
    
        // 🚨 IMPORTANT: Setup the multi-account dynamic folder and database before launching!
        ConfigManager.setSession(ConfigManager.USERNAME, ConfigManager.AUTH_TOKEN);
        
        launch(); // Boot the background processes!
    }

    public static void launch() {
        isRunning = true;
        if (SystemTray.isSupported()) {
            modernMenu = new ModernTrayMenu(); // Initialize the sleek UI
            setupSystemTray();
        }
        
        new Thread(() -> {
                while (isRunning) { // 🚨 Check the flag
                    try { 
                        syncFromServer(); 
                        Thread.sleep(10000); 
                    } catch (InterruptedException e) { break; }
                    catch (Exception e) { /* log errors */ }
                }
                System.out.println("🛑 Heartbeat thread stopped.");
            }).start();
        
        // 🚨 Make sure FolderWatcher is using ConfigManager.SYNC_FOLDER now!
        FolderWatcher.startWatching();
    }

    // ==========================================
    // 🖥️ THE UPGRADED SYSTEM TRAY
    // ==========================================
    private static void setupSystemTray() {
        SystemTray tray = SystemTray.getSystemTray();
        Image image = createDefaultIcon();

        // 🚨 Notice: We DO NOT pass a 'PopupMenu' anymore. Just the image and tooltip!
        trayIcon = new TrayIcon(image, "SyncVault - Connecting...");
        trayIcon.setImageAutoSize(true);

        // 🖱️ Left/Right Click instantly toggles your new ModernTrayMenu window!
        trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                System.out.println("🖱️ Tray Icon Clicked! Launching Modern UI...");
                modernMenu.toggleMenu(); // Spawns the sleek Dropbox-style window
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
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

    public static void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public static void updateSyncTime() {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        if (trayIcon != null) {
            trayIcon.setToolTip("SyncVault — Last synced: " + time);
        }
    }

    // ==========================================
    // 🫀 THE HEARTBEAT (Cloud -> PC)
    // ==========================================
    
        public static void syncFromServer() {
            if (ConfigManager.AUTH_TOKEN == null || ConfigManager.SYNC_FOLDER == null) return;
    
            String serverListUrl = ConfigManager.SERVER_URL + "/api/sync/list";
            Path syncDir = Paths.get(ConfigManager.SYNC_FOLDER);
    
            try {
                // 1. Fetch from Cloud
                HttpURLConnection conn = (HttpURLConnection) URI.create(serverListUrl).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);
                
                if (conn.getResponseCode() != 200) return;
    
                Set<String> cloudFiles = new HashSet<>();
                try (Scanner scanner = new Scanner(conn.getInputStream())) {
                    while (scanner.hasNextLine()) {
                        String file = scanner.nextLine().trim();
                        if (!file.isEmpty()) cloudFiles.add(file);
                    }
                }
    
                Set<String> currentCloudFolders = new HashSet<>();
                for (String file : cloudFiles) {
                    String[] parts = file.split("/");
                    String folderPath = "";
                    for (int i = 0; i < parts.length - 1; i++) {
                        folderPath += (i == 0 ? "" : "/") + parts[i];
                        currentCloudFolders.add(folderPath);
                    }
                }
    
                // 2. Fetch DB Memory
                Set<String> lastKnownFolders = DatabaseManager.getLastKnownFolders();
                Set<String> lastKnownFiles = DatabaseManager.getLastKnownFiles(); 
    
                // 3. Diff Folders (Wrapped in try-catch)
                for (String oldFolder : lastKnownFolders) {
                    if (!currentCloudFolders.contains(oldFolder)) {
                        Path localDirPath = syncDir.resolve(oldFolder);
                        if (Files.exists(localDirPath)) {
                            try {
                                if (Files.list(localDirPath).findFirst().isEmpty()) {
                                    Files.delete(localDirPath);
                                }
                            } catch (Exception e) { /* Ignore folder lock */ }
                        }
                    }
                }
                System.out.println("🫀 Heartbeat check... Cloud has " + cloudFiles.size() + " files.");
                // 4. Download Loop
                for (String cloudFile : cloudFiles) {
                    try {
                        if (cloudFile.contains("Zone.Identifier")) continue;
                        
                        if (DatabaseManager.isMarkedDeleted(cloudFile)) continue;
                    
                        Path localPath = syncDir.resolve(cloudFile);
                        if (!Files.exists(localPath)) {
                            FolderWatcher.maskEvent(cloudFile); 
                            FileReceiver.downloadFileFromServer(cloudFile);
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Error downloading " + cloudFile + ": " + e.getMessage());
                    }
                }
    
                // 5. 🚨 THE FIX: Bulletproof Local Deletion Loop
                if (Files.exists(syncDir)) {
                    List<Path> localFiles = new ArrayList<>();
                    try (Stream<Path> stream = Files.walk(syncDir)) {
                        localFiles = stream.filter(Files::isRegularFile).collect(Collectors.toList());
                    } catch (Exception e) { e.printStackTrace(); }
    
                    for (Path localFile : localFiles) {
                        try {
                            String relative = syncDir.relativize(localFile).toString().replace("\\", "/");
                            if (relative.endsWith(".tmp") || relative.startsWith("~") || relative.contains("Zone.Identifier")) continue;
    
                            // If it's NOT in the cloud, but our DB remembers it used to be there:
                            if (!cloudFiles.contains(relative) && lastKnownFiles.contains(relative)) {
                                System.out.println("🔎 Heartbeat noticed cloud deleted: " + relative);
                                FileReceiver.deleteFileLocally(relative); // Uses the safe retry method
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Loop error protecting Daemon crash: " + e.getMessage());
                        }
                    }
                }
    
                // 6. Save State (This will NOW ALWAYS RUN because the loop cannot crash)
                DatabaseManager.updateCloudState(cloudFiles, currentCloudFolders);
                updateSyncTime();
                
            } catch (Exception e) {
                System.err.println("❌ Heartbeat Network Error: " + e.getMessage());
            }
        }
    // Allow the UI to cleanly unhook the tray icon on logout
    public static void removeTrayIcon() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }
}