import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderWatcher {
    private static Path syncDirectory;
    private static volatile boolean running = false;
    
    // 🧠 THE MEMORY BANKS
    private static final Set<String> maskedEvents = ConcurrentHashMap.newKeySet(); // Stops Two-Way Sync loops
    private static Map<String, Long> previousState = new HashMap<>();              // Tracks file timestamps

    public static void maskEvent(String relativePath) {
        maskedEvents.add(relativePath.replace("\\", "/"));
    }

    public static void stopWatching() {
            System.out.println("🛑 Stopping FolderWatcher...");
            running = false; 
        }
    
        public static void startWatching() {
            // If it's already running, stop it first before starting a new one
            if (running) stopWatching(); 
            
            running = true; // Set the flag to true
            syncDirectory = Paths.get(ConfigManager.SYNC_FOLDER);
    
            new Thread(() -> {
                try {
                    Files.createDirectories(syncDirectory);
                    previousState = takeSnapshot();
                    System.out.println("📂 Watching folder: " + syncDirectory.toAbsolutePath());
    
                    // 🚨 Use the 'running' flag in the loop
                    while (running) { 
                        Thread.sleep(2000);
                        scanForChanges();
                    }
                    System.out.println("✅ FolderWatcher stopped successfully.");
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }).start();
        }

    // ==========================================
    // 📸 THE SNAPSHOT ENGINE
    // ==========================================
    // ==========================================
        // 📸 THE SNAPSHOT ENGINE
        // ==========================================
        private static void scanForChanges() {
            Map<String, Long> currentState = takeSnapshot();
            Set<String> failedLockedFiles = new HashSet<>(); // 👈 Track files we couldn't access
    
            // 1. Check for NEW or MODIFIED files
            for (Map.Entry<String, Long> entry : currentState.entrySet()) {
                String relativePath = entry.getKey();
                Long currentTimestamp = entry.getValue();
                Long previousTimestamp = previousState.get(relativePath);
    
                if (previousTimestamp == null || !previousTimestamp.equals(currentTimestamp)) {
                    // 🛡️ The Shield: Did the server do this?
                    if (maskedEvents.contains(relativePath)) {
                        System.out.println("🛡️ Event masked (Server download): " + relativePath);
                        maskedEvents.remove(relativePath);
                        continue; 
                    }
    
                    File changedFile = syncDirectory.resolve(relativePath).toFile();
                    if (waitForUnlock(changedFile)) {
                        System.out.println("\n⚡ Detected local change on: " + relativePath);
                        FileSender.uploadFile(changedFile, relativePath);
                    } else {
                        // 🚨 THE FIX: Windows still has it locked. Mark it so we forget it!
                        System.out.println("⏳ File locked by Windows, will retry: " + relativePath);
                        failedLockedFiles.add(relativePath);
                    }
                }
            }
    
            // 🚨 THE FIX: Remove locked files from currentState so we re-detect them next loop!
            for (String lockedFile : failedLockedFiles) {
                currentState.remove(lockedFile);
            }
    
            // 2. Check for DELETED files
            for (String oldFilePath : previousState.keySet()) {
                if (!currentState.containsKey(oldFilePath) && !failedLockedFiles.contains(oldFilePath)) {
                    // 🛡️ The Shield: Did the server tell us to delete this?
                    if (maskedEvents.contains(oldFilePath)) {
                        System.out.println("🛡️ Event masked (Server delete): " + oldFilePath);
                        maskedEvents.remove(oldFilePath);
                        continue;
                    }
    
                    System.out.println("🗑️ Detected local DELETE on: " + oldFilePath);
                    FileSender.deleteFile(oldFilePath);
                }
            }
    
            // Update the baseline for the next 2-second cycle
            previousState = currentState;
        }

    // Reads the entire folder tree and returns a map of [File Path -> Last Modified Time]
    private static Map<String, Long> takeSnapshot() {
        Map<String, Long> state = new HashMap<>();
        if (!Files.exists(syncDirectory)) return state;

        try (java.util.stream.Stream<Path> stream = Files.walk(syncDirectory)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = syncDirectory.relativize(path).toString().replace("\\", "/");
                
                // Ignore junk Windows system files
                if (relativePath.endsWith(".tmp") || relativePath.startsWith("~") || relativePath.contains("Zone.Identifier")) return;
                
                state.put(relativePath, path.toFile().lastModified());
            });
        } catch (IOException ignored) {}
        return state;
    }

    // ==========================================
    // 🛡️ THE FILE LOCK PATIENT POLLER
    // ==========================================
    private static boolean waitForUnlock(File file) {
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                raf.close();
                return true; 
            } catch (Exception e) {
                e.printStackTrace();
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return false; // Gave up waiting for Windows to release the file
    }
}