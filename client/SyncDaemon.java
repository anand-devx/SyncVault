import java.nio.file.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class SyncDaemon {
    // A map to remember the last time we processed a specific file
    private static final Map<String, Long> lastProcessedTime = new HashMap<>();
    // A 500-millisecond cooldown window
    private static final long DEBOUNCE_DELAY_MS = 500;

    public static void main(String[] args) {
        System.out.println("🚀 SyncVault Client Daemon Initializing...");

        try {
            Path syncDirectory = Paths.get("./SyncFolder");
            if (!Files.exists(syncDirectory)) Files.createDirectories(syncDirectory);

            WatchService watchService = FileSystems.getDefault().newWatchService();
            syncDirectory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );

            System.out.println("👀 Actively monitoring: " + syncDirectory.toAbsolutePath());

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException ex) {
                    return;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path filename = (Path) event.context();
                    String fileKey = filename.toString() + "_" + kind.name();

                    // --- DEBOUNCE LOGIC ---
                    long currentTime = System.currentTimeMillis();
                    long lastTime = lastProcessedTime.getOrDefault(fileKey, 0L);

                    if (currentTime - lastTime > DEBOUNCE_DELAY_MS) {
                        System.out.println("✅ [PROCESSED " + kind.name() + "] -> " + filename);
                        lastProcessedTime.put(fileKey, currentTime);
                        
                        // 👉 NEW: If it's a Create or Modify event, chunk and hash the file!
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            String fullPath = syncDirectory.resolve(filename).toString();
                            System.out.println("   ⚙️ Processing file blocks...");
                            
                            // Call our new Engine
                            List<String> hashes = FileChunker.processFile(fullPath);
                            System.out.println("   🚀 File mapped to " + hashes.size() + " block(s).\n");
                        }
                    } else {
                        // We caught a duplicate OS event! Ignore it.
                        System.out.println("   [IGNORED DUPLICATE] -> " + filename);
                    }
                }

                if (!key.reset()) break;
            }

        } catch (IOException e) {
            System.err.println("❌ Critical IO Error: " + e.getMessage());
        }
    }
}