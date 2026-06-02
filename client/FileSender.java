import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class FileSender {
    private static final String SERVER_URL = "http://localhost:8080/api/sync/chunk";

    public static void main(String[] args) {
        File file = new File("SyncFolder/massive.bin");
        if (!file.exists()) {
            System.out.println("❌ File not found! Make sure massive.bin is in SyncFolder.");
            return;
        }

        int chunkSize = 4 * 1024 * 1024; // 4MB
        byte[] buffer = new byte[chunkSize];
        boolean uploadSuccessful = true;

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            int chunkIndex = 0;

            System.out.println("🚀 Starting upload of: " + file.getName());

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                boolean success = sendChunkToServer(chunkData, file.getName(), chunkIndex);
                
                if (success) {
                    System.out.println("✅ Chunk " + chunkIndex + " sent successfully!");
                } else {
                    System.out.println("❌ Failed to send chunk " + chunkIndex);
                    uploadSuccessful = false;
                    break;
                }
                chunkIndex++;
            }

            if (uploadSuccessful) {
                System.out.println("🎉 Upload complete!");
            } else {
                System.out.println("🛑 Upload stopped due to an error.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean sendChunkToServer(byte[] chunkData, String filename, int chunkIndex) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        
        // This line fixes the deprecation warning
        URL url = URI.create(SERVER_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            // 1. Filename
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n");
            request.writeBytes(filename + "\r\n");

            // 2. Chunk Index
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chunkIndex\"\r\n\r\n");
            request.writeBytes(chunkIndex + "\r\n");

            // 3. File Chunk
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            request.write(chunkData);
            request.writeBytes("\r\n");

            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            // This is the magic line that will tell us EXACTLY why it is failing
            System.out.println("⚠️ Server responded with status code: " + responseCode + " (" + connection.getResponseMessage() + ")");
        }
        return responseCode == 200;
    }
}