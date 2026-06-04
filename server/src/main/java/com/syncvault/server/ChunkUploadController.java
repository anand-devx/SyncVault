package com.syncvault.server; // Adjust if your package is com.syncvault.server.controller

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.syncvault.server.service.S3Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/sync")
public class ChunkUploadController {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private AmazonS3 s3Client; // We inject this for List and Download

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    // We no longer store files permanently! This is just a temporary staging area.
    private static final String STAGING_DIR = "/mnt/c/Users/dy873/Documents/CloudVault/staging";
    private static final String TEMP_DIR = "/mnt/c/Users/dy873/Documents/CloudVault/temp_chunks";

    // ==========================================
    // 📦 1. RECEIVE MULTIPART CHUNK (No changes needed)
    // ==========================================
    @PostMapping("/chunk")
    public ResponseEntity<String> receiveChunk(
            @RequestParam("filename") String filename,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            Path chunkFolder = Paths.get(TEMP_DIR, decodedPath.replace("/", "_") + "_chunks");
            Files.createDirectories(chunkFolder);

            Path chunkFile = chunkFolder.resolve(String.valueOf(chunkIndex));
            file.transferTo(chunkFile.toAbsolutePath().toFile());

            return ResponseEntity.ok("Chunk " + chunkIndex + " saved.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chunk upload failed: " + e.getMessage());
        }
    }

    // ==========================================
    // 🧩 2. MERGE CHUNKS & UPLOAD TO AWS S3
    // ==========================================
    @PostMapping("/merge")
    public ResponseEntity<String> mergeChunks(
            @RequestParam("filename") String filename,
            @RequestParam("totalChunks") int totalChunks) {
        try {
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            Path targetFilePath = Paths.get(STAGING_DIR, decodedPath);
            
            if (targetFilePath.getParent() != null) {
                Files.createDirectories(targetFilePath.getParent());
            }

            File outputFile = targetFilePath.toFile();
            if (outputFile.exists()) outputFile.delete();

            Path chunkFolder = Paths.get(TEMP_DIR, decodedPath.replace("/", "_") + "_chunks");

            // 1. Assemble the file locally
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = chunkFolder.resolve(String.valueOf(i));
                    if (!Files.exists(chunkFile)) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing chunk: " + i);
                    }
                    byte[] chunkBytes = Files.readAllBytes(chunkFile);
                    raf.write(chunkBytes);
                    Files.delete(chunkFile); // Delete chunk immediately
                }
            }

            // 2. 🚀 THE BRAIN TRANSPLANT: Push assembled file to AWS S3!
            s3Service.uploadFile(decodedPath, outputFile);

            // 3. 🗑️ Clean up: Delete the local staging file and chunk folders
            outputFile.delete(); 
            
            if (Files.exists(chunkFolder)) {
                Files.walk(chunkFolder)
                     .sorted(java.util.Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
            }

            System.out.println("🎉 File successfully assembled and pushed to S3: " + decodedPath);
            return ResponseEntity.ok("File merged and uploaded to S3 successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Merge failed: " + e.getMessage());
        }
    }

    // ==========================================
    // 🗑️ 3. CLOUD DELETION ENDPOINT
    // ==========================================
    @DeleteMapping("/delete-file/{filename}")
    public ResponseEntity<String> deleteFile(@PathVariable("filename") String filename) {
        try {
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            
            // Just tell AWS to delete it! No messy folder logic required.
            s3Service.deleteFile(decodedPath);
            
            return ResponseEntity.ok("File deleted from AWS S3 successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Deletion failed: " + e.getMessage());
        }
    }

    // ==========================================
    // ⬇️ 4. CLOUD DOWNLOAD ENDPOINT
    // ==========================================
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam("filename") String filename) {
        try {
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            
            // Fetch directly from AWS S3
            S3Object s3Object = s3Client.getObject(bucketName, decodedPath);
            InputStreamResource resource = new InputStreamResource(s3Object.getObjectContent());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + decodedPath + "\"")
                    .body(resource);
        } catch (Exception e) {
            System.out.println("❌ Download failed for: " + filename);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==========================================
    // 📋 5. CLOUD STATE ENDPOINT (For the Heartbeat)
    // ==========================================
    @GetMapping("/list")
    public ResponseEntity<String> listFiles() {
        try {
            // Ask AWS for a list of everything in the bucket
            ListObjectsV2Result result = s3Client.listObjectsV2(bucketName);
            StringBuilder fileList = new StringBuilder();
            
            for (S3ObjectSummary summary : result.getObjectSummaries()) {
                fileList.append(summary.getKey()).append("\n");
            }
            
            return ResponseEntity.ok(fileList.toString());
        } catch (Exception e) {
            System.out.println("❌ Failed to list AWS S3 files: " + e.getMessage());
            return ResponseEntity.status(500).body("");
        }
    }
}