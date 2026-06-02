package com.syncvault.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/sync")
public class ChunkUploadController {

    private static final String STORAGE_DIR = "/mnt/c/Users/dy873/Documents/CloudVault";
    private static final String TEMP_DIR = STORAGE_DIR + "/temp_chunks";

    // ==========================================
    // 📦 1. RECEIVE MULTIPART CHUNK
    // ==========================================
    // ==========================================
    // 📦 1. RECEIVE MULTIPART CHUNK
    // ==========================================
    @PostMapping("/chunk")
    public ResponseEntity<String> receiveChunk(
            @RequestParam("filename") String filename,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            // Clean up the relative path if it was URL-encoded by the client
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            
            // Create a unique temporary directory for this specific file's chunks
            Path chunkFolder = Paths.get(TEMP_DIR, decodedPath.replace("/", "_") + "_chunks");
            Files.createDirectories(chunkFolder);

            // Save the individual binary chunk
            Path chunkFile = chunkFolder.resolve(String.valueOf(chunkIndex));
            
            // 🚀 THE FIX: Force Spring Boot to use the Absolute Path, not the Tomcat /tmp path!
            file.transferTo(chunkFile.toAbsolutePath().toFile());

            return ResponseEntity.ok("Chunk " + chunkIndex + " saved.");
        } catch (Exception e) {
            e.printStackTrace(); // Print full error to your WSL terminal just in case
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chunk upload failed: " + e.getMessage());
        }
    }
    // ==========================================
    // 🧩 2. MERGE CHUNKS & REBUILD DIRECTORY TREE
    // ==========================================
    @PostMapping("/merge")
    public ResponseEntity<String> mergeChunks(
            @RequestParam("filename") String filename,
            @RequestParam("totalChunks") int totalChunks) {
        try {
            String decodedPath = URLDecoder.decode(filename, "UTF-8");
            
            // Define the ultimate destination inside cloud_storage
            Path targetFilePath = Paths.get(STORAGE_DIR, decodedPath);
            
            // 🌳 THE FIX: Automatically create any sub-folders (like cloud_storage/Taxes/2026/)
            if (targetFilePath.getParent() != null) {
                Files.createDirectories(targetFilePath.getParent());
            }

            File outputFile = targetFilePath.toFile();
            // Delete old version if it exists before rewriting
            if (outputFile.exists()) outputFile.delete();

            Path chunkFolder = Paths.get(TEMP_DIR, decodedPath.replace("/", "_") + "_chunks");

            // Stitch the pieces back together sequentially
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = chunkFolder.resolve(String.valueOf(i));
                    if (!Files.exists(chunkFile)) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing chunk: " + i);
                    }
                    
                    byte[] chunkBytes = Files.readAllBytes(chunkFile);
                    raf.write(chunkBytes);
                    
                    // Clean up the temp chunk file immediately to save disk space
                    Files.delete(chunkFile);
                }
            }

            // Clean up the empty temporary chunk directory
            Files.deleteIfExists(chunkFolder);
            
            System.out.println("🎉 File successfully assembled in cloud: " + decodedPath);
            return ResponseEntity.ok("File merged successfully.");
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
            Path targetFilePath = Paths.get(STORAGE_DIR, decodedPath);

            if (Files.exists(targetFilePath)) {
                Files.delete(targetFilePath);
                System.out.println("🗑️ File deleted from cloud storage: " + decodedPath);
                
                // Optional: Clean up empty parent directories left behind
                Path parent = targetFilePath.getParent();
                while (parent != null && !parent.getFileName().toString().equals(STORAGE_DIR)) {
                    if (Files.isDirectory(parent) && Files.list(parent).findFirst().isEmpty()) {
                        Files.delete(parent);
                        parent = parent.getParent();
                    } else {
                        break;
                    }
                }
                return ResponseEntity.ok("File deleted successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Deletion failed: " + e.getMessage());
        }
    }

    // ==========================================
    // ⬇️ 4. CLOUD DOWNLOAD ENDPOINT
    // ==========================================
    @GetMapping("/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(@RequestParam("filename") String filename) {
        try {
            String decodedPath = java.net.URLDecoder.decode(filename, "UTF-8");
            Path file = Paths.get(STORAGE_DIR, decodedPath);
            if (!Files.exists(file)) return ResponseEntity.notFound().build();
            
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(file.toUri());
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName().toString() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==========================================
    // 📋 5. CLOUD STATE ENDPOINT (For the Heartbeat)
    // ==========================================
    @GetMapping("/list")
    public ResponseEntity<String> listFiles() {
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDir)) return ResponseEntity.ok("");
            
            StringBuilder fileList = new StringBuilder();
            Files.walk(storageDir)
                 .filter(Files::isRegularFile)
                 .forEach(path -> {
                     String relative = storageDir.relativize(path).toString().replace("\\", "/");
                     fileList.append(relative).append("\n");
                 });
            return ResponseEntity.ok(fileList.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("");
        }
    }
}