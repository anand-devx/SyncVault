import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;

public class FileSender {
    private static final String BASE_URL = ConfigManager.SERVER_URL + "/api/sync";
    private static final String CHUNK_URL = BASE_URL + "/chunk";
    private static final String MERGE_URL = BASE_URL + "/merge";
    private static final String DELETE_URL = BASE_URL + "/delete-file/";

    // ==========================================
    // 🗑️ THE DELETION SYNC
    // ==========================================
    public static void deleteFile(String relativePath) {
        try {
            String encodedPath = URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20");
            HttpURLConnection connection = (HttpURLConnection) URI.create(DELETE_URL + encodedPath).toURL().openConnection();
            connection.setRequestMethod("DELETE");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                System.out.println("☁️ Successfully deleted " + relativePath + " from cloud!");
            } else {
                System.out.println("⚠️ Cloud file not found or already deleted.");
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to reach server for deletion: " + e.getMessage());
        }
    }

    // ==========================================
    // 🚀 THE UPLOAD ENGINE (Folder Supported)
    // ==========================================
    public static void uploadFile(File file, String relativePath) {
        if (!file.exists()) return;

        int chunkSize = 4 * 1024 * 1024; // 4MB chunks
        byte[] buffer = new byte[chunkSize];
        boolean uploadSuccessful = true;
        int chunkIndex = 0; 

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            System.out.println("🚀 Starting upload of: " + relativePath);

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                if (!sendChunkToServer(chunkData, relativePath, chunkIndex)) {
                    System.out.println("❌ Failed to send chunk " + chunkIndex);
                    uploadSuccessful = false;
                    break;
                }
                chunkIndex++;
            }

            if (uploadSuccessful) {
                System.out.println("🔄 Upload finished. Telling server to merge " + chunkIndex + " chunks...");
                if (triggerMerge(relativePath, chunkIndex)) {
                    System.out.println("🎉 Boom! File upload and merge completely successful: " + relativePath);
                } else {
                    System.out.println("❌ Server failed to merge the pieces.");
                }
            } else {
                System.out.println("🛑 Upload stopped due to an error.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ File might be locked by another program. Will retry later.");
        }
    }

    private static boolean sendChunkToServer(byte[] chunkData, String relativePath, int chunkIndex) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(CHUNK_URL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n" + relativePath + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chunkIndex\"\r\n\r\n" + chunkIndex + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            request.write(chunkData);
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) { 
                    System.out.println("❌ Network Error on Chunk: " + e.getMessage());
                    return false; 
                }
        return connection.getResponseCode() == 200;
    }

    private static boolean triggerMerge(String relativePath, int totalChunks) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(MERGE_URL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n" + relativePath + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"totalChunks\"\r\n\r\n" + totalChunks + "\r\n");
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) { 
                    System.out.println("❌ Network Error on Chunk: " + e.getMessage());
                    return false; 
                }
        return connection.getResponseCode() == 200;
    }
}