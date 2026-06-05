import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SettingsUI {

    public static void openWindow() {
        JFrame frame = new JFrame("SyncVault — Settings");
        frame.setSize(450, 320);
        frame.setLocationRelativeTo(null);
        
        // Clean, minimal container with padding
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // UI Components - Load the strictly saved global path
        JTextField pathField = new JTextField(ConfigManager.SYNC_DIR_PATH, 20);
        JButton browseBtn = new JButton("Browse");
        JCheckBox autoStart = new JCheckBox("Launch on system startup", true);
        JButton saveBtn = new JButton("Save & Apply");

        // Layout
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Global PC Sync Path:"), gbc);
        
        gbc.gridy = 1;
        panel.add(pathField, gbc);
        
        gbc.gridx = 1;
        panel.add(browseBtn, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(autoStart, gbc);
        
        gbc.gridy = 3;
        panel.add(saveBtn, gbc);

        // Actions
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        saveBtn.addActionListener(e -> {
            // Update the global path (this triggers the GUI fallback check if invalid!)
            ConfigManager.updateSyncFolder(pathField.getText());
            
            FolderWatcher.stopWatching();
            
            // Only start watching if the user didn't click "Don't Sync" during validation
            if (!ConfigManager.SYNC_DISABLED) {
                FolderWatcher.startWatching();
            }
            
            frame.dispose();
        });

        frame.add(panel);
        frame.setVisible(true);
    }
}