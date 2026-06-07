import java.io.*;
import java.util.Properties;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JOptionPane;

public class ConfigManager {
    // 🚨 1. Core Variables (Restored SYNC_DIR_PATH)
    public static String SERVER_URL = "http://ec2-15-206-159-122.ap-south-1.compute.amazonaws.com:8080";
    public static String SYNC_DIR_PATH = System.getProperty("user.home") + File.separator + "SyncVault";
    public static String USERNAME = "";
    public static String PASSWORD = "";
    
    // 🚨 2. Multi-Account Dynamic Variables
    public static String AUTH_TOKEN = null;
    public static String CURRENT_USER = null;
    public static String SYNC_FOLDER; // Ensure this is only declared ONCE!
    public static String DB_URL;
    
    // Flag to track if the user chose "Don't Sync" in the error prompt
    public static boolean SYNC_DISABLED = false; 

    private static final String CONFIG_FILE = "syncvault.properties";

    static {
        loadSettings();
    }

    // 🛡️ THE BULLETPROOF SHIELD
    // This strips out any "SyncVault_anand" garbage from the root path.
    private static void autoCleanRootPath() {
        if (SYNC_DIR_PATH != null) {
            File f = new File(SYNC_DIR_PATH);
            boolean cleaned = false;
            // The while loop handles deep nesting if it happened previously!
            while (f != null && f.getName() != null && f.getName().startsWith("SyncVault_")) {
                SYNC_DIR_PATH = f.getParent();
                if (SYNC_DIR_PATH == null) {
                    SYNC_DIR_PATH = System.getProperty("user.home") + File.separator + "SyncVault";
                    break;
                }
                f = new File(SYNC_DIR_PATH);
                cleaned = true;
            }
            if (cleaned) {
                System.out.println("🧹 Auto-cleaned corrupted root path back to: " + SYNC_DIR_PATH);
            }
        }
    }

    public static void updateSyncFolder(String newPath) {
        SYNC_DIR_PATH = newPath;
        autoCleanRootPath(); // Shield activated
        saveSettings(SERVER_URL, SYNC_DIR_PATH); 
        
        if (CURRENT_USER != null && AUTH_TOKEN != null) {
            setSession(CURRENT_USER, AUTH_TOKEN);
        }
    }

    public static void loadSettings() {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(in);
            
            SERVER_URL = props.getProperty("SERVER_URL", "http://localhost:8080");
            SYNC_DIR_PATH = props.getProperty("SYNC_DIR_PATH", System.getProperty("user.home") + File.separator + "SyncVault");
            
            autoCleanRootPath(); // Shield activated on startup
            
            USERNAME = props.getProperty("USERNAME", "");
            PASSWORD = props.getProperty("PASSWORD", "");
        } catch (IOException e) {
            // Ignore, will use defaults if file doesn't exist yet
        }
    }

    // Expected by AuthUI
    public static void saveSettings(String url, String dir, String user, String pass) {
        SERVER_URL = url;
        SYNC_DIR_PATH = dir;
        autoCleanRootPath(); // 🚨 Shield prevents AuthUI from corrupting the root!
        USERNAME = user;
        PASSWORD = pass;
        saveToFile();
    }

    // Expected by SettingsUI
    public static void saveSettings(String url, String dir) {
        SERVER_URL = url;
        SYNC_DIR_PATH = dir;
        autoCleanRootPath(); // Shield activated
        saveToFile();
    }

    private static void saveToFile() {
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            
            props.setProperty("SERVER_URL", SERVER_URL != null ? SERVER_URL : "http://localhost:8080");
            props.setProperty("SYNC_DIR_PATH", SYNC_DIR_PATH != null ? SYNC_DIR_PATH : System.getProperty("user.home") + File.separator + "SyncVault");
            props.setProperty("USERNAME", USERNAME != null ? USERNAME : "");
            props.setProperty("PASSWORD", PASSWORD != null ? PASSWORD : "");
            
            props.store(out, "SyncVault Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // 🔀 The Multi-Account Session Builder
    // ==========================================
    public static void setSession(String username, String token) {
        CURRENT_USER = username;
        AUTH_TOKEN = token;
        SYNC_DISABLED = false; 
        
        autoCleanRootPath(); // 🚨 Double-check shield right before building the path!
        
        String defaultFallback = System.getProperty("user.home") + File.separator + "SyncVault";
        boolean pathResolved = false;

        // 🚨 STRICT CHECK: GUI Prompt Validation Loop
        while (!pathResolved) {
            if (SYNC_DIR_PATH == null || SYNC_DIR_PATH.trim().isEmpty()) {
                SYNC_DIR_PATH = defaultFallback;
            }

            File rootDir = new File(SYNC_DIR_PATH);
            
            if (!rootDir.exists()) {
                boolean created = rootDir.mkdirs();
                if (!created) {
                    Object[] options = {"Retry", "Use Default PC Path", "Don't Sync"};
                    int choice = JOptionPane.showOptionDialog(null,
                            "The sync path '" + SYNC_DIR_PATH + "' cannot be found or created.\n\n"
                            + "If you do not create the path, the default PC path will be used:\n" + defaultFallback,
                            "SyncVault Folder Error",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[0]);

                    if (choice == 0) { // Retry
                        continue;
                    } else if (choice == 1) { // Use Default
                        SYNC_DIR_PATH = defaultFallback;
                        new File(SYNC_DIR_PATH).mkdirs();
                        JOptionPane.showMessageDialog(null, "Using default fallback path:\n" + SYNC_DIR_PATH);
                        pathResolved = true;
                    } else { // Don't Sync
                        SYNC_DISABLED = true;
                        SYNC_FOLDER = null; 
                        System.out.println("⚠️ User chose NOT to sync.");
                        return; 
                    }
                } else {
                    pathResolved = true; 
                }
            } else {
                pathResolved = true; 
            }
        }
        
        // Resolve the user-specific folder strictly from the Global Base
        Path basePath = Paths.get(SYNC_DIR_PATH);
        Path userFolder = basePath.resolve("SyncVault_" + username);
        SYNC_FOLDER = userFolder.toString();
        
        // 🚨 THE FIX IS HERE 🚨
        // Generate a safe, dynamic database path in the user's hidden Windows folder
        String userHome = System.getProperty("user.home").replace("\\", "/");
        File appDataDir = new File(userHome + "/.syncvault");
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        DB_URL = "jdbc:sqlite:" + appDataDir.getAbsolutePath().replace("\\", "/") + "/syncvault_" + username + ".db";
        // -------------------------

        File folder = new File(SYNC_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        
        System.out.println("✅ Sync folder strictly bound to: " + SYNC_FOLDER);
        saveToFile(); 
    }

    public static void clearSession() {
        AUTH_TOKEN = null;
        CURRENT_USER = null;
        DB_URL = null;
        USERNAME = "";
        PASSWORD = "";
        
        autoCleanRootPath(); // Clear any leftover garbage on logout
        saveToFile();
    }
    
}