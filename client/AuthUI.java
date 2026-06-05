import javax.swing.*;
import java.awt.*;

public class AuthUI {

    public static void showWindow() {
        // Run UI creation on the Event Dispatch Thread (Safe Swing Practice)
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SyncVault — Sign In");
            frame.setSize(380, 280);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout());
            
            // Force native Windows look and feel
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

            JPanel panel = new JPanel(new GridLayout(6, 1, 8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
            
            // Make the font a bit bolder for visibility
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12)); 

            panel.add(new JLabel("Username"));
            panel.add(usernameField);
            panel.add(new JLabel("Password"));
            panel.add(passwordField);
            panel.add(statusLabel);

            JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 0));
            JButton loginBtn = new JButton("Sign In");
            JButton registerBtn = new JButton("Create Account");
            buttonPanel.add(loginBtn);
            buttonPanel.add(registerBtn);
            panel.add(buttonPanel);

            frame.add(panel, BorderLayout.CENTER);

            // ==========================================
            // 🔐 LOGIN ACTION 
            // ==========================================
            loginBtn.addActionListener(e -> {
                String user = usernameField.getText().trim();
                String pass = new String(passwordField.getPassword());
                
                if (user.isEmpty() || pass.isEmpty()) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Please enter username and password.");
                    return;
                }

                loginBtn.setEnabled(false);
                registerBtn.setEnabled(false);
                statusLabel.setForeground(new Color(0, 120, 215)); 
                statusLabel.setText("⏳ Connecting to server...");
                
                new Thread(() -> {
                    // Keep saving the basic credentials
                    ConfigManager.saveSettings(ConfigManager.SERVER_URL, ConfigManager.SYNC_FOLDER, user, pass);
                    String result = FileSender.login(user, pass); 
                    
                    SwingUtilities.invokeLater(() -> {
                        if (result.equals("SUCCESS") && ConfigManager.AUTH_TOKEN != null) {
                            
                            // 🚨 MULTI-ACCOUNT WIRING: Setup dynamic folder & DB for this user!
                            ConfigManager.setSession(user, ConfigManager.AUTH_TOKEN);
                            
                            statusLabel.setForeground(new Color(0, 150, 0));
                            statusLabel.setText("✅ Success! Booting engine...");
                            
                            Timer timer = new Timer(1000, evt -> {
                                frame.dispose();
                                new Thread(() -> SyncDaemon.launch()).start(); 
                            });
                            timer.setRepeats(false);
                            timer.start();
                        } else {
                            loginBtn.setEnabled(true);
                            registerBtn.setEnabled(true);
                            statusLabel.setForeground(Color.RED);
                            statusLabel.setText("❌ " + result); 
                        }
                    });
                }).start();
            });

            // ==========================================
            // 📝 REGISTER ACTION
            // ==========================================
            registerBtn.addActionListener(e -> {
                String user = usernameField.getText().trim();
                String pass = new String(passwordField.getPassword());
                
                if (user.length() < 3 || pass.length() < 6) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("User (min 3) | Pass (min 6 chars)");
                    return;
                }
                
                loginBtn.setEnabled(false);
                registerBtn.setEnabled(false);
                statusLabel.setForeground(new Color(0, 120, 215));
                statusLabel.setText("⏳ Creating secure account...");

                new Thread(() -> {
                    String result = FileSender.register(user, pass); 
                    
                    SwingUtilities.invokeLater(() -> {
                        if (result.equals("SUCCESS")) {
                            statusLabel.setForeground(new Color(0, 150, 0));
                            statusLabel.setText("✅ Account created! Logging in...");
                            
                            new Thread(() -> {
                                ConfigManager.saveSettings(ConfigManager.SERVER_URL, ConfigManager.SYNC_FOLDER, user, pass);
                                String loginResult = FileSender.login(user, pass);
                                
                                SwingUtilities.invokeLater(() -> {
                                    if (loginResult.equals("SUCCESS") && ConfigManager.AUTH_TOKEN != null) {
                                        
                                        // 🚨 MULTI-ACCOUNT WIRING: Setup dynamic folder & DB for this user!
                                        ConfigManager.setSession(user, ConfigManager.AUTH_TOKEN);
                                        
                                        frame.dispose();
                                        new Thread(() -> SyncDaemon.launch()).start();
                                    } else {
                                        statusLabel.setForeground(Color.RED);
                                        statusLabel.setText("❌ Auto-login failed: " + loginResult);
                                        loginBtn.setEnabled(true);
                                        registerBtn.setEnabled(true);
                                    }
                                });
                            }).start();
                            
                        } else {
                            loginBtn.setEnabled(true);
                            registerBtn.setEnabled(true);
                            statusLabel.setForeground(Color.RED);
                            statusLabel.setText("❌ " + result); 
                        }
                    });
                }).start();
            });

            frame.setVisible(true);
        });
    }
}