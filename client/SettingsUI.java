import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SettingsUI {
    
    public static void openWindow() {
        System.out.println("🖼️ Attempting to draw Settings window..."); // 👈 Added this!

        // Create the window
        JFrame frame = new JFrame("SyncVault Settings");
        frame.setSize(450, 200);
        frame.setLayout(new GridLayout(3, 2, 10, 10));
        frame.setLocationRelativeTo(null); 
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
        frame.setAlwaysOnTop(true); // 🚀 THE FIX: Force it above all other Windows!
        // Create the labels and text inputs (pre-filled with current config)
        JLabel urlLabel = new JLabel("  Server URL (AWS or Local):");
        JTextField urlField = new JTextField(ConfigManager.SERVER_URL);

        JLabel dirLabel = new JLabel("  Local Sync Folder:");
        JTextField dirField = new JTextField(ConfigManager.SYNC_DIR_PATH);

        // Create the Save Button
        JButton saveButton = new JButton("Save Settings");
        saveButton.setBackground(new Color(0, 120, 215));
        saveButton.setForeground(Color.WHITE);
        
        saveButton.addActionListener((ActionEvent e) -> {
            String newUrl = urlField.getText().trim();
            String newDir = dirField.getText().trim();

            if (newUrl.isEmpty() || newDir.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Fields cannot be empty!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Hand the new data to the ConfigManager!
            ConfigManager.saveSettings(newUrl, newDir);

            JOptionPane.showMessageDialog(frame, "Settings saved! Please exit the app from the System Tray and restart it to apply the new folder.", "Success", JOptionPane.INFORMATION_MESSAGE);
            frame.dispose(); 
        });

        // Add everything to the window
        frame.add(urlLabel);
        frame.add(urlField);
        frame.add(dirLabel);
        frame.add(dirField);
        frame.add(new JLabel("")); // Empty spacer to push button to the right
        frame.add(saveButton);

        // Make it visible!
        frame.setVisible(true);
    }
}