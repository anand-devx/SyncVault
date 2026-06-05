package com.syncvault.server; 

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.syncvault.server.service.S3Service;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwtException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/sync")
public class ChunkUploadController {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private AmazonS3 s3Client; 

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${syncvault.jwt.secret}")
    private String jwtSecret;

    // Universal platform-safe scratch folders
    private static final String STAGING_DIR = System.getProperty("java.io.tmpdir") + "/syncvault_staging";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/syncvault_chunks";

    // Helper method to extract identity from token securely
    private String getUsernameFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header.");
        }
        String token = authHeader.substring(7); // Strip "Bearer "
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // ==========================================
    // 📦 1. RECEIVE MULTIPART CHUNK
    // ==========================================
    @PostMapping("/chunk")
    public ResponseEntity<String> receiveChunk(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("filename") String filename,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            String username = getUsernameFromToken(authHeader);
            String decodedPath = URLDecoder.decode(filename, StandardCharsets.UTF_8.toString());
            
            // Isolate chunk folder per user to avoid cross-user collision attacks
            String userSafeFolder = username + "_" + decodedPath.replace("/", "_") + "_chunks";
            Path chunkFolder = Paths.get(TEMP_DIR, userSafeFolder);
            Files.createDirectories(chunkFolder);

            Path chunkFile = chunkFolder.resolve(String.valueOf(chunkIndex));
            file.transferTo(chunkFile.toFile()); // Persist chunk to disk

            return ResponseEntity.ok("Chunk " + chunkIndex + " saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chunk upload failed: " + e.getMessage());
        }
    }

    // ==========================================
    // 🚀 2. ASSEMBLE CHUNKS AND PUSH TO S3
    // ==========================================
    @PostMapping("/merge")
    public ResponseEntity<String> mergeChunks(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("filename") String filename,
            @RequestParam("totalChunks") int totalChunks) {
    
        String username = getUsernameFromToken(authHeader);
        String decodedPath = "";
        Path chunkFolder = null;
        File outputFile = null;
    
        try {
            decodedPath = URLDecoder.decode(filename, StandardCharsets.UTF_8.toString());
            String userScopedS3Key = username + "/" + decodedPath;
    
            // Prepare target staging path
            Path targetFilePath = Paths.get(STAGING_DIR, username, decodedPath);
            if (targetFilePath.getParent() != null) {
                Files.createDirectories(targetFilePath.getParent());
            }
    
            outputFile = targetFilePath.toFile();
            // Remove existing file if it somehow exists from a failed run
            if (outputFile.exists()) outputFile.delete();
    
            String userSafeFolder = username + "_" + decodedPath.replace("/", "_") + "_chunks";
            chunkFolder = Paths.get(TEMP_DIR, userSafeFolder);
    
            // 1. REASSEMBLE: Write chunks sequentially to the output file
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = chunkFolder.resolve(String.valueOf(i));
                    if (!Files.exists(chunkFile)) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing chunk: " + i);
                    }
                    // Read bytes and append to final file
                    byte[] chunkBytes = Files.readAllBytes(chunkFile);
                    raf.write(chunkBytes);
                }
            }
    
            // 2. UPLOAD: Push the fully compiled file to S3
            // S3Service handles the input stream creation here
            s3Service.uploadFile(userScopedS3Key, outputFile);
            System.out.println("🎉 File successfully assembled and pushed to S3: " + userScopedS3Key);
    
            // 3. CLEANUP: Only triggered after S3 confirms success
            performCleanup(outputFile, chunkFolder);
    
            return ResponseEntity.ok("File merged and uploaded to S3 successfully.");
    
        } catch (Exception e) {
            e.printStackTrace();
            // Perform cleanup even on failure to avoid disk bloat
            performCleanup(outputFile, chunkFolder);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Merge failed: " + e.getMessage());
        }
    }
    
    /**
     * Robust cleanup helper. 
     * Separated to ensure it is called ONLY after S3 upload is finished 
     * or when an error requires a roll-back.
     */
    private void performCleanup(File outputFile, Path chunkFolder) {
        try {
            // Delete the reassembled staging file
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            // Delete the temporary chunk directory and all files inside
            if (chunkFolder != null && Files.exists(chunkFolder)) {
                Files.walk(chunkFolder)
                     .sorted(java.util.Comparator.reverseOrder()) // Delete files first, then folder
                     .map(Path::toFile)
                     .forEach(File::delete);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Cleanup encountered an issue: " + e.getMessage());
        }
    }

    // ==========================================
    // 🔍 3. LIST USER ISOLATED FILES
    // ==========================================
    @GetMapping("/list")
    public ResponseEntity<String> listFiles(@RequestHeader("Authorization") String authHeader) {
        try {
            String username = getUsernameFromToken(authHeader);
            String prefix = username + "/";

            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withPrefix(prefix);

            ListObjectsV2Result result = s3Client.listObjectsV2(request);
            StringBuilder fileList = new StringBuilder();

            for (S3ObjectSummary summary : result.getObjectSummaries()) {
                // Strip out the root user prefix before giving to the client daemon
                String clientPath = summary.getKey().substring(prefix.length());
                if (!clientPath.isEmpty()) {
                    fileList.append(clientPath).append("\n");
                }
            }
            return ResponseEntity.ok(fileList.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to retrieve file matrix.");
        }
    }

    // ==========================================
    // 🗑️ 4. USER SPECIFIC S3 DELETE
    // ==========================================
    @DeleteMapping("/delete-file")
    public ResponseEntity<String> deleteFile(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("filename") String filename) {
        try {
            String username = getUsernameFromToken(authHeader);
            String userScopedPath = username + "/" + URLDecoder.decode(filename, StandardCharsets.UTF_8.toString());
            
            s3Service.deleteFile(userScopedPath);
            return ResponseEntity.ok("File deleted from cloud storage.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Deletion trace failed.");
        }
    }

    // ==========================================
    // ⬇️ 5. USER ISOLATED STREAM DOWNLOAD
    // ==========================================
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("filename") String filename) {
        try {
            String username = getUsernameFromToken(authHeader);
            String decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8.toString());
            String userScopedPath = username + "/" + decodedFilename;

            S3Object s3Object = s3Client.getObject(bucketName, userScopedPath);
            InputStreamResource resource = new InputStreamResource(s3Object.getObjectContent());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + decodedFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}