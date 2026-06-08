import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
public class FileSender {
    private static final String BASE_URL = ConfigManager.SERVER_URL + "/api/sync";
    private static final String CHUNK_URL = BASE_URL + "/chunk";
    private static final String MERGE_URL = BASE_URL + "/merge";
    private static final String DELETE_URL = BASE_URL + "/delete-file?filename=";
    private static final List<String> deletionBuffer = new ArrayList<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // ==========================================
    // 🗑️ THE DELETION SYNC
    // ==========================================
    // ==========================================
        // 🗑️ THE DELETION SYNC
        // 
        // ==========================================
        // ==========================================
            // 🔐 STANDARD APP AUTHENTICATION (DYNAMIC)
            // ==========================================
            
            // ==========================================
                // 🔐 PRO AUTHENTICATION (DYNAMIC & ERROR MAPPED)
                // ==========================================
                
                // Change your deleteFile method to a buffer-and-send approach
        public static void deleteFilesBatch(List<String> filesToDelete) {
            try {
                // 1. Serialize the list to JSON
                String jsonPayload = jsonMapper.writeValueAsString(filesToDelete);
        
                // 2. Send the whole batch in ONE request
                HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL + "/delete-batch").toURL().openConnection();
                connection.setRequestMethod("POST"); // Use POST for the batch
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);
                connection.setDoOutput(true);
        
                connection.getOutputStream().write(jsonPayload.getBytes());
        
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("☁️ Successfully deleted batch of " + filesToDelete.size() + " files.");
                } else {
                    System.out.println("⚠️ Batch delete failed. Code: " + responseCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public static String register(String username, String password) {
            try {
                HttpURLConnection conn = (HttpURLConnection) 
                    URI.create(ConfigManager.SERVER_URL + "/auth/register").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000); // Fail fast if server is offline
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        
                String body = "username=" + URLEncoder.encode(username, "UTF-8") + 
                                "&password=" + URLEncoder.encode(password, "UTF-8");
                conn.getOutputStream().write(body.getBytes());
                
                // 🚨 FIX: Read the code once here!
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    return "SUCCESS";
                } else {
                    return readErrorStream(conn, responseCode); // Pass it down
                }
            } catch (Exception e) {
                    e.printStackTrace();
                    // 🚨 FIX: This will show us the EXACT reason it failed
                    return "Network Error: " + e.getMessage(); 
                }
            
        }
    
        public static String login(String username, String password) {
            try {
                HttpURLConnection conn = (HttpURLConnection) 
                    URI.create(ConfigManager.SERVER_URL + "/auth/login").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                
                String body = "username=" + URLEncoder.encode(username, "UTF-8") + 
                                "&password=" + URLEncoder.encode(password, "UTF-8");
                conn.getOutputStream().write(body.getBytes());
                
                // 🚨 FIX: Read the code once here!
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    ConfigManager.AUTH_TOKEN = new String(conn.getInputStream().readAllBytes()).trim();
                    return "SUCCESS";
                } else {
                    return readErrorStream(conn, responseCode); // Pass it down
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Server Unreachable: Verify URL and AWS Firewall.";
            }
        }
    
        // 🚨 FIX: Now accepts responseCode as a safe integer
        private static String readErrorStream(HttpURLConnection conn, int responseCode) {
                // 🚨 FIX: If the server sends no text at all, catch it here before it crashes!
                if (conn.getErrorStream() == null) {
                    return "Server blocked request (Code: " + responseCode + ") - No text returned.";
                }
                
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorMessage = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorMessage.append(line);
                    }
                    return errorMessage.toString();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return "Stream Read Error (Code: " + responseCode + ")";
                }
            }

        public static void deleteFile(String relativePath) {
                try {
                    // Because we are using ?filename= now, standard encoding is perfectly safe!
                    String encodedPath = URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20"); 
        
                    HttpURLConnection connection = (HttpURLConnection) URI.create(DELETE_URL + encodedPath).toURL().openConnection();
                    // ... rest of the method stays the same!
                connection.setRequestMethod("DELETE");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200 || responseCode == 204) {
                    System.out.println("☁️ Successfully deleted " + relativePath + " from cloud!");
                } else {
                    System.out.println("⚠️ Server rejected deletion. HTTP Code: " + responseCode);
                    
                    // 🚨 UN-MUTE THE SERVER: Print the exact error message
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = br.readLine()) != null) {
                            System.out.println("   Server says: " + errorLine);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("❌ Failed to reach server for deletion: " + e.getMessage());
            }
        }

    // ==========================================
    // 🚀 THE UPLOAD ENGINE (Folder Supported)
    // ==========================================
    // Replace the existing uploadFile method in FileSender.java
        public static boolean uploadFile(File file, String relativePath) {
            if (!file.exists()) return false;
    
            int chunkSize = 4 * 1024 * 1024; 
            byte[] buffer = new byte[chunkSize];
            boolean uploadSuccessful = true;
            int chunkIndex = 0; 
    
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
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
                        return true; // ✅ Success
                    } else {
                        System.out.println("❌ Server failed to merge the pieces.");
                        return false; // ❌ Failed
                    }
                } else {
                    System.out.println("🛑 Upload stopped due to an error.");
                    return false; // ❌ Failed
                }
            } catch (Exception e) {
                System.out.println("⚠️ File might be locked by another program. Will retry later: " + relativePath);
                return false; // ❌ Failed
            }
        }
        // 🛡️ Safe stream consumer to prevent Socket Leaks
            private static void drainAndClose(HttpURLConnection conn) {
                try (java.io.InputStream is = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    while (is.read(buffer) != -1) {} // Drain stream to allow keep-alive socket reuse
                } catch (Exception ignore) {}
            }
    private static boolean sendChunkToServer(byte[] chunkData, String relativePath, int chunkIndex) throws Exception {
            String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
            HttpURLConnection connection = (HttpURLConnection) URI.create(CHUNK_URL).toURL().openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                drainAndClose(connection); // ⚡ FREE THE SOCKET
                return true;
            } else {
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
                e.printStackTrace();
                System.out.println("❌ Network Error on Chunk: " + e.getMessage());
                return false; 
            }
    
            // 🚨 THE FIX: Actually read the server's response!
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                return true;
            } else {
                System.out.println("❌ Server rejected chunk " + chunkIndex + " with HTTP Code: " + responseCode);
                // Try to read the exact error message from Spring Boot
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = br.readLine()) != null) {
                        System.out.println("   Server says: " + errorLine);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        }

    private static boolean triggerMerge(String relativePath, int totalChunks) throws Exception {
        String boundary = "SyncVaultBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(MERGE_URL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer " + ConfigManager.AUTH_TOKEN);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        // ... inside triggerMerge ...
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            drainAndClose(connection); // ⚡ FREE THE SOCKET
            return true;
        }
        return false;
        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n" + relativePath + "\r\n");
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"totalChunks\"\r\n\r\n" + totalChunks + "\r\n");
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        } catch (Exception e) { 
                    e.printStackTrace();    
                    System.out.println("❌ Network Error on Chunk: " + e.getMessage());
                    return false; 
                }
        return connection.getResponseCode() == 200;
    }
}