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

    public static void main(String[] args) {
        System.out.println("🚀 Starting SyncVault Daemon...");
        
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    syncFromServer();
                    Thread.sleep(10000); 
                } catch (InterruptedException e) { break; }
            }
        });
        heartbeat.start();

        FolderWatcher.startWatching();
    }

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

            // Download Missing Files
            for (String cloudFile : cloudFiles) {
                Path localPath = SYNC_DIR.resolve(cloudFile);
                if (!Files.exists(localPath)) {
                    System.out.println("🔎 Heartbeat found new cloud file: " + cloudFile);
                    FileReceiver.downloadFileFromServer(cloudFile);
                }
            }

            // Delete Local Files
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
            // 🚨 We removed the silent fail. Now we will see if the network drops!
            System.out.println("❌ Heartbeat Network Error: " + e.getMessage());
        }
    }
}