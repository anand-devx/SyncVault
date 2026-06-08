import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderWatcher {
    private static Path syncDirectory;
    private static volatile boolean running = false;
    
    // 🧠 THE MEMORY BANKS
    private static final Set<String> maskedEvents = ConcurrentHashMap.newKeySet(); 
    private static Map<String, Long> previousState = new ConcurrentHashMap<>(); 

    // 🚀 Thread pool so mass uploads don't freeze the Watcher!
    private static final ExecutorService fileTaskQueue = Executors.newFixedThreadPool(4);

    public static void maskEvent(String relativePath) {
        maskedEvents.add(relativePath.replace("\\", "/"));
    }

    public static void stopWatching() {
        System.out.println("🛑 Stopping FolderWatcher...");
        running = false; 
    }
    
    public static void startWatching() {
        if (running) stopWatching(); 
        
        running = true; 
        syncDirectory = Paths.get(ConfigManager.SYNC_FOLDER);

        new Thread(() -> {
            try {
                Files.createDirectories(syncDirectory);
                
                Map<String, Long> initial = takeSnapshot();
                if (initial != null) previousState.putAll(initial);
                
                System.out.println("📂 Watching folder: " + syncDirectory.toAbsolutePath());

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
    private static void scanForChanges() {
        Map<String, Long> currentState = takeSnapshot();
        
        // 🚨 If snapshot failed due to Windows locking the folder during mass-copy, skip this cycle!
        if (currentState == null) return; 

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
                System.out.println("\n⚡ Detected local change on: " + relativePath);
                
                // Track it immediately so we don't spam queue it
                previousState.put(relativePath, currentTimestamp);

                // 🚨 Push to background threads! 
                fileTaskQueue.submit(() -> {
                    boolean success = FileSender.uploadFile(changedFile, relativePath);
                    if (!success) {
                        // 🔄 Retry Logic: If upload failed (file locked, network error), 
                        // wipe it from memory so the Watcher detects it again next cycle!
                        previousState.remove(relativePath);
                    }
                });
            }
        }

        // 2. Check for DELETED files
        List<String> batchDeleteList = new ArrayList<>();

        for (String oldFilePath : previousState.keySet()) {
            if (!currentState.containsKey(oldFilePath)) {
                
                // 🛡️ The Shield
                if (maskedEvents.contains(oldFilePath)) {
                    System.out.println("🛡️ Event masked (Server delete): " + oldFilePath);
                    maskedEvents.remove(oldFilePath);
                    previousState.remove(oldFilePath);
                    continue;
                }

                System.out.println("🗑️ Detected local DELETE on: " + oldFilePath);
                batchDeleteList.add(oldFilePath);
                previousState.remove(oldFilePath);
            }
        }

        // 🚨 Process all deletes AT ONCE to outrun the Heartbeat!
        if (!batchDeleteList.isEmpty()) {
            DatabaseManager.markAsDeletedBatch(batchDeleteList); // ⚡ 5ms SQLite Transaction
            fileTaskQueue.submit(() -> FileSender.deleteFilesBatch(batchDeleteList));
        }
    }

    private static Map<String, Long> takeSnapshot() {
        Map<String, Long> state = new HashMap<>();
        if (!Files.exists(syncDirectory)) return state;

        try (java.util.stream.Stream<Path> stream = Files.walk(syncDirectory)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = syncDirectory.relativize(path).toString().replace("\\", "/");
                if (relativePath.endsWith(".tmp") || relativePath.startsWith("~") || relativePath.contains("Zone.Identifier")) return;
                state.put(relativePath, path.toFile().lastModified());
            });
            return state; 
        } catch (IOException e) {
            System.out.println("⏳ Folder is currently busy (mass operation in progress). Waiting for I/O unlock...");
            return null; 
        }
    }
}