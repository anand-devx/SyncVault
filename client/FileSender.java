import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;

public class FileSender {
    private static final String CHUNK_URL = "http://localhost:8080/api/sync/chunk";
    private static final String MERGE_URL = "http://localhost:8080/api/sync/merge";
    private static final String DELETE_URL = "http://localhost:8080/api/sync/delete-file/"; // 🚀 NEW

    // 🚀 NEW: Tells the server to delete a file
    public static void deleteFile(String filename) {
        try {
            // Because URLs can't have spaces, we safely encode the filename
            String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
            HttpURLConnection connection = (HttpURLConnection) URI.create(DELETE_URL + encodedFilename).toURL().openConnection();
            connection.setRequestMethod("DELETE");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                System.out.println("☁️ Successfully deleted " + filename + " from cloud!");
            } else {
                System.out.println("⚠️ Cloud file not found or already deleted.");
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to reach server for deletion.");
        }
    }

    // 🚀 NEW: We changed this to a reusable method that takes ANY file!
    public static void uploadFile(File file) {
        if (!file.exists()) {
            System.out.println("❌ File not found!");
            return;
        }

        int chunkSize = 4 * 1024 * 1024; // 4MB
        byte[] buffer = new byte[chunkSize];
        boolean uploadSuccessful = true;
        int totalChunks = 0;

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            System.out.println("🚀 Starting upload of: " + file.getName());

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                if (sendChunkToServer(chunkData, file.getName(), totalChunks)) {
                    System.out.println("✅ Chunk " + totalChunks + " sent successfully!");
                } else {
                    System.out.println("❌ Failed to send chunk " + totalChunks);
                    uploadSuccessful = false;
                    break;
                }
                totalChunks++;
            }

            if (uploadSuccessful) {
                System.out.println("🔄 Upload finished. Telling server to merge " + totalChunks + " chunks...");
                if (triggerMerge(file.getName(), totalChunks)) {
                    System.out.println("🎉 Boom! File upload and merge completely successful!");
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

    private static boolean sendChunkToServer(byte[] chunkData, String filename, int chunkIndex) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(CHUNK_URL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n" + filename + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chunkIndex\"\r\n\r\n" + chunkIndex + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            request.write(chunkData);
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) { return false; }
        return connection.getResponseCode() == 200;
    }

    private static boolean triggerMerge(String filename, int totalChunks) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(MERGE_URL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n" + filename + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"totalChunks\"\r\n\r\n" + totalChunks + "\r\n");
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) { return false; }
        return connection.getResponseCode() == 200;
    }
}