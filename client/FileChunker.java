import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class FileChunker {
    // Standard enterprise chunk size: 4 Megabytes
    private static final int CHUNK_SIZE = 4 * 1024 * 1024; 

    /**
     * Reads a file, splits it into 4MB chunks, and returns a list of SHA-256 hashes.
     */
    public static List<String> processFile(String filePath) {
        List<String> chunkHashes = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            return chunkHashes;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Use BufferedInputStream for high-performance disk reading
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                int chunkIndex = 0;

                // Read exactly up to 4MB at a time
                while ((bytesRead = bis.read(buffer)) > 0) {
                    // Hash only the exact bytes read (prevents padding bugs on the final chunk)
                    digest.update(buffer, 0, bytesRead);
                    byte[] hashBytes = digest.digest();
                    
                    String hexHash = bytesToHex(hashBytes);
                    chunkHashes.add(hexHash);
                    
                    System.out.println("   📦 [Chunk " + chunkIndex + "] Size: " + (bytesRead/1024) + "KB | Hash: " + hexHash.substring(0, 16) + "...");
                    chunkIndex++;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Hashing Error on file " + filePath + ": " + e.getMessage());
        }

        return chunkHashes;
    }

    /**
     * Converts raw cryptographic bytes into a readable Hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}