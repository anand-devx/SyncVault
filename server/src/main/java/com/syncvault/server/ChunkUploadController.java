package com.syncvault.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/sync")
@CrossOrigin(origins = "*")
public class ChunkUploadController {

    private final String STORAGE_DIR = "cloud_storage";

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("filename") String filename,
            @RequestParam("chunkIndex") int chunkIndex) {
        try {
            Path storageDirectory = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }
            String chunkName = filename + ".part" + chunkIndex;
            Path targetLocation = storageDirectory.resolve(chunkName);
            file.transferTo(targetLocation.toAbsolutePath().toFile());
            
            System.out.println("✅ Received: " + chunkName);
            return ResponseEntity.ok("Chunk " + chunkIndex + " saved successfully!");
        } catch (IOException e) {
            System.err.println("❌ Error saving chunk: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to save chunk");
        }
    }

    // 🚀 NEW: The Reassembly Endpoint
    @PostMapping("/merge")
    public ResponseEntity<String> mergeChunks(
            @RequestParam("filename") String filename,
            @RequestParam("totalChunks") int totalChunks) {
        try {
            Path storageDirectory = Paths.get(STORAGE_DIR);
            File mergedFile = new File(storageDirectory.toFile(), filename);
            
            // Open the final file and glue the chunks in order
            try (FileOutputStream fos = new FileOutputStream(mergedFile, true)) {
                for (int i = 0; i < totalChunks; i++) {
                    File chunkFile = new File(storageDirectory.toFile(), filename + ".part" + i);
                    Files.copy(chunkFile.toPath(), fos); // Glue piece
                    chunkFile.delete(); // Delete piece to clean up space
                }
            }
            System.out.println("🎉 Successfully merged chunks into: " + filename);
            return ResponseEntity.ok("File merged successfully!");
            
        } catch (IOException e) {
            System.err.println("❌ Error merging file: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to merge file");
        }
    }
}