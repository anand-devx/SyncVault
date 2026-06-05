import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ModernTrayMenu {

    private JFrame menuFrame;
    public ModernTrayMenu() {
            buildMenuFrame(); 
        }
        public void createAndShowTray() {
            if (!SystemTray.isSupported()) return;
        
            try {
                SystemTray tray = SystemTray.getSystemTray();
                // Use a standard system icon or your own png
                Image iconImage = Toolkit.getDefaultToolkit().getImage("icon.png");
                TrayIcon trayIcon = new TrayIcon(iconImage, "SyncVault");
                trayIcon.setImageAutoSize(true);
        
                // 🚨 CRITICAL: Remove the 'popup' entirely. 
                // We only use the tray icon as a trigger for your high-res custom frame.
                trayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            toggleMenu(); // This opens your sleek custom window
                        }
                    }
                });
        
                tray.add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }

        private void buildMenuFrame() {
            menuFrame = new JFrame("SyncVault"); // Standard title bar
            menuFrame.setSize(300, 350);
            // Remove setUndecorated(true) so Windows handles the window frame
            // Remove setBackground(Color.WHITE) so it uses the system theme
            menuFrame.setLayout(new BoxLayout(menuFrame.getContentPane(), BoxLayout.Y_AXIS));
        
            // Simple layout: just buttons
            menuFrame.add(createMenuButton("Open Sync Folder", this::openFolder));
            menuFrame.add(createMenuButton("Force Sync Now", this::forceSync));
            menuFrame.add(createMenuButton("Upload File...", this::uploadCustomFile));
            menuFrame.add(createMenuButton("Settings", this::openSettings));
            menuFrame.add(new JSeparator());
            menuFrame.add(createMenuButton("Log Out", this::logout));
            menuFrame.add(createMenuButton("Quit SyncVault", () -> {
                    System.out.println("👋 Quitting SyncVault...");
                    System.exit(0); 
                }));
            // Default close behavior is now standard
            menuFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        }

    private JButton createMenuButton(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(300, 40));
        btn.setBackground(Color.WHITE);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(240, 240, 240));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(Color.WHITE);
            }
        });

        btn.addActionListener(e -> {
            menuFrame.setVisible(false); 
            action.run();
        });
        return btn;
    }

    // 🚨 Changed to PUBLIC so SyncDaemon can call it when the tray icon is clicked!
    public void toggleMenu() {
        if (menuFrame.isVisible()) {
            menuFrame.setVisible(false);
        } else {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(menuFrame.getGraphicsConfiguration());
            int taskBarSize = scnMax.bottom;

            menuFrame.setLocation(
                screenSize.width - menuFrame.getWidth() - 10,
                screenSize.height - taskBarSize - menuFrame.getHeight() - 10
            );

            menuFrame.setVisible(true);
            menuFrame.requestFocus(); 
        }
    }

    // --- Action Methods ---

    private void openFolder() {
        try {
            Desktop.getDesktop().open(new File(ConfigManager.SYNC_FOLDER));
        } catch (Exception e) {
            System.out.println("Could not open folder.");
        }
    }

    private void forceSync() {
        System.out.println("⚡ Triggering immediate background sync...");
        // 🚨 Wires directly into your existing daemon's heartbeat logic
        new Thread(SyncDaemon::syncFromServer).start();
    }

    private void uploadCustomFile() {
        // 1. Initialize the native Windows File Chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a file to upload");
    
        // 2. Open it in a way that blocks until the user picks a file
        int result = fileChooser.showOpenDialog(null);
    
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            // ... (your existing copy logic) ...
            try {
                Path destination = Paths.get(ConfigManager.SYNC_FOLDER, selectedFile.getName());
                Files.copy(selectedFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                SyncDaemon.showNotification("Upload Started", "Added " + selectedFile.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void openSettings() {
        SettingsUI.openWindow();
    }

    private void logout() {
        System.out.println("Logging out...");
        
        // 🚨 KILL THE PROCESSES
        SyncDaemon.isRunning = false; 
        FolderWatcher.stopWatching(); 
        
        ConfigManager.clearSession();
        SyncDaemon.removeTrayIcon();
        AuthUI.showWindow();
    }
}