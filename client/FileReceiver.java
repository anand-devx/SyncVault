import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.FileSystemException;
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
        public static boolean deleteFileLocally(String relativePath) {
                Path path = Paths.get(ConfigManager.SYNC_FOLDER, relativePath);
                int maxRetries = 5;
                
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        boolean deleted = Files.deleteIfExists(path);
                        if (deleted) {
                            System.out.println("✅ Successfully deleted local file: " + relativePath);
                        }
                        return true; // Success or file didn't exist anyway
                    } catch (FileSystemException e) {
                        // Windows has a lock on the file. Wait 200ms and try again.
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    } catch (IOException e) {
                        System.err.println("❌ Fatal IO Error deleting " + relativePath + ": " + e.getMessage());
                        return false;
                    }
                }
                
                System.err.println("⏳ Gave up deleting (File locked by Windows): " + relativePath);
                return false; // Failed to delete, but DID NOT crash the app!
        }
}