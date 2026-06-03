import java.io.*;
import java.util.Properties;
import java.nio.file.Paths;

public class ConfigManager {
    private static final String CONFIG_FILE = "syncvault.properties";
    
    public static String SERVER_URL;
    public static String SYNC_DIR_PATH;

    // 🚀 THE FIX: This static block runs automatically the millisecond Java touches this class,
    // ensuring the variables are populated BEFORE SyncDaemon tries to use them.
    static {
        init();
    }

    private static void init() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            createDefaultConfig(file);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            SERVER_URL = props.getProperty("SERVER_URL", "http://172.22.54.83:8080");
            
            String defaultFolder = Paths.get(System.getProperty("user.home"), "Desktop", "SyncFolder").toString();
            SYNC_DIR_PATH = props.getProperty("SYNC_DIR", defaultFolder);
            
            System.out.println("⚙️ Config Loaded: Server -> " + SERVER_URL);
            System.out.println("⚙️ Config Loaded: Folder -> " + SYNC_DIR_PATH);
            
        } catch (Exception e) {
            System.out.println("❌ Failed to read settings. Using hardcoded defaults.");
        }
    }

    private static void createDefaultConfig(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("# ==========================================");
            writer.println("# SyncVault Client Configuration");
            writer.println("# ==========================================");
            writer.println();
            writer.println("# The URL of your Spring Boot Server (Change this when moving to AWS)");
            writer.println("SERVER_URL=http://172.22.54.83:8080");
            writer.println();
            writer.println("# The local folder you want to sync to the cloud");
            String defaultFolder = Paths.get(System.getProperty("user.home"), "Desktop", "SyncFolder").toString().replace("\\", "/");
            writer.println("SYNC_DIR=" + defaultFolder);
            
            System.out.println("📄 Generated new settings file: " + CONFIG_FILE);
        } catch (IOException e) {
            System.out.println("❌ Could not create default config file.");
        }
    }
    // 🚀 NEW METHOD: The UI will call this when the user clicks "Save"
    public static void saveSettings(String newUrl, String newDir) {
        SERVER_URL = newUrl;
        SYNC_DIR_PATH = newDir;

        Properties props = new Properties();
        props.setProperty("SERVER_URL", SERVER_URL);
        props.setProperty("SYNC_DIR", SYNC_DIR_PATH);

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            // This overwrites the text file with the new values!
            props.store(fos, "SyncVault Client Configuration Updated via UI");
            System.out.println("💾 Settings saved successfully to disk!");
        } catch (IOException e) {
            System.out.println("❌ Failed to save new settings.");
        }
    }
}