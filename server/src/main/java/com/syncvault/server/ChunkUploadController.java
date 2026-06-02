package com.syncvault.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/sync")
@CrossOrigin(origins = "*") // Allows your client to talk to this server without security blocks
public class ChunkUploadController {

    // This is the folder where all your files will be saved
    private final String STORAGE_DIR = "cloud_storage";

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("filename") String filename,
            @RequestParam("chunkIndex") int chunkIndex) {

        try {
            // 1. Create the cloud_storage directory if it doesn't exist yet
            Path storageDirectory = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }

            // 2. Name the chunk (e.g., massive.bin.part0, massive.bin.part1)
            String chunkName = filename + ".part" + chunkIndex;
            Path targetLocation = storageDirectory.resolve(chunkName);

            // 3. Save the file chunk to the disk
            file.transferTo(targetLocation.toAbsolutePath().toFile());

            System.out.println("✅ Received and saved: " + chunkName);
            return ResponseEntity.ok("Chunk " + chunkIndex + " saved successfully!");

        } catch (IOException e) {
            System.err.println("❌ Error saving chunk: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to save chunk");
        }
    }
}