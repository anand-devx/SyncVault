import java.io.*;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigManager {

    private static final String CONFIG_FILE = "syncvault.properties";

    public static String SERVER_URL;
    public static String SYNC_DIR_PATH;

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

            SERVER_URL = props.getProperty(
                "SERVER_URL",
                "http://localhost:8080"
            );

            String defaultFolder = Paths.get(
                System.getProperty("user.home"),
                "Desktop",
                "SyncFolder"
            ).toString();
            SYNC_DIR_PATH = props.getProperty("SYNC_DIR", defaultFolder);

            System.out.println("⚙️ Config Loaded: Server -> " + SERVER_URL);
            System.out.println("⚙️ Config Loaded: Folder -> " + SYNC_DIR_PATH);
        } catch (Exception e) {
            System.out.println(
                "❌ Failed to read settings. Using hardcoded defaults."
            );
            SERVER_URL = "http://localhost:8080";
            SYNC_DIR_PATH = Paths.get(
                System.getProperty("user.home"),
                "Desktop",
                "SyncFolder"
            ).toString();
        }
    }

    private static void createDefaultConfig(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("# ==========================================");
            writer.println("# SyncVault Client Configuration");
            writer.println("# ==========================================");
            writer.println();
            writer.println("# The URL of your Spring Boot Server");
            writer.println("SERVER_URL=http://localhost:8080");
            writer.println();
            writer.println("# The local folder you want to sync to the cloud");
            String defaultFolder = Paths.get(
                System.getProperty("user.home"),
                "Desktop",
                "SyncFolder"
            )
                .toString()
                .replace("\\", "/");
            writer.println("SYNC_DIR=" + defaultFolder);

            System.out.println(
                "📄 Generated new settings file: " + CONFIG_FILE
            );
        } catch (IOException e) {
            System.out.println("❌ Could not create default config file.");
        }
    }

    public static void saveSettings(String newUrl, String newDir) {
            SERVER_URL = newUrl;
            SYNC_DIR_PATH = newDir;
    
            Properties props = new Properties();
            File file = new File(CONFIG_FILE);
    
            // 1. Read the existing file first so we don't delete other settings
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    props.load(fis);
                } catch (IOException e) {
                    System.out.println("⚠️ Could not load existing settings, creating new.");
                }
            }
    
            // 2. Update only what we need
            props.setProperty("SERVER_URL", SERVER_URL);
            props.setProperty("SYNC_DIR", SYNC_DIR_PATH);
    
            // 3. Save it securely
            try (FileOutputStream fos = new FileOutputStream(file)) {
                props.store(fos, "SyncVault Client Configuration Updated via UI");
                System.out.println("💾 Settings saved successfully to disk!");
            } catch (IOException e) {
                System.out.println("❌ Failed to save new settings: " + e.getMessage());
            }
        }
}
