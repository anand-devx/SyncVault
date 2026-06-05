import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReceiver {
    private static final Path syncDirectory = Paths.get(ConfigManager.SYNC_FOLDER);
    private static final String BASE_URL = ConfigManager.SERVER_URL + "/api/sync/download?filename=";

    // ==========================================
    // ☁️🛬 SERVER COMMAND: Download New/Modified File
    // ==========================================
    public static void downloadFileFromServer(String relativePath) {
        try {
            // 1. Tell the Watcher to look away for this specific file path
            FolderWatcher.maskEvent(relativePath);

            // 2. Resolve the local destination path and build directory tree if missing
            Path targetPath = syncDirectory.resolve(relativePath);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            // 3. Connect to Spring Boot stream
            String encodedPath = java.net.URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20");
            HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL + encodedPath).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);

            if (connection.getResponseCode() == 200) {
                try (InputStream is = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("📥 Real-time Mirror: Downloaded from cloud -> " + relativePath);
            } else {
                System.out.println("❌ Server rejected download request for: " + relativePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Error downloading file via sync: " + e.getMessage());
        }
    }

    // ==========================================
    // ☁️🗑️ SERVER COMMAND: Delete Local File 
    // ==========================================
    // ==========================================
        // ☁️🗑️ SERVER COMMAND: Delete Local File 
        // ==========================================
        public static void deleteFileLocally(String relativePath) {
            try {
                Path targetPath = syncDirectory.resolve(relativePath);
                if (Files.exists(targetPath)) {
                    // 1. Tell the Watcher to look away
                    FolderWatcher.maskEvent(relativePath);
    
                    // 2. Delete the file off the hard drive
                    Files.delete(targetPath);
                    System.out.println("🗑️ Real-time Mirror: Deleted local file -> " + relativePath);
    
                    // 3. 🎯 TARGETED CLEANUP: Only check the exact folder we just emptied!
                    Path parentDir = targetPath.getParent();
                    
                    // Keep moving up the folder tree until we hit the root SyncFolder
                    while (parentDir != null && !parentDir.toAbsolutePath().equals(syncDirectory.toAbsolutePath())) {
                        
                        // Safely check if the folder is empty without leaking memory
                        try (java.util.stream.Stream<Path> stream = Files.list(parentDir)) {
                            if (stream.findAny().isPresent()) {
                                break; // Folder has other stuff in it. Stop checking!
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            break; // Folder is locked by Windows, stop checking!
                        }
    
                        // If we made it here, the folder is completely empty. Delete it!
                        Files.delete(parentDir);
                        System.out.println("🧹 Cleaned up empty synced folder -> " + parentDir.getFileName());
                        
                        // Move up to check the grandparent folder!
                        parentDir = parentDir.getParent();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("❌ Error executing local sync delete: " + e.getMessage());
            }
        }
}