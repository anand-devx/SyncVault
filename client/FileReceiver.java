import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReceiver {
    private static final Path syncDirectory = Paths.get(ConfigManager.SYNC_DIR_PATH);
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
            System.out.println("❌ Error downloading file via sync: " + e.getMessage());
        }
    }

    // ==========================================
    // ☁️🗑️ SERVER COMMAND: Delete Local File 
    // ==========================================
    public static void deleteFileLocally(String relativePath) {
        try {
            Path targetPath = syncDirectory.resolve(relativePath);
            if (Files.exists(targetPath)) {
                // 1. Tell the Watcher to look away so it doesn't fire an organic DELETE back to the server
                FolderWatcher.maskEvent(relativePath);

                // 2. Delete it off the hard drive
                Files.delete(targetPath);
                System.out.println("🗑️ Real-time Mirror: Deleted local file -> " + relativePath);
            }
        } catch (Exception e) {
            System.out.println("❌ Error executing local sync delete: " + e.getMessage());
        }
    }
}