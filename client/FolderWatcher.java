import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderWatcher {
    private static WatchService watchService;
    private static final Map<WatchKey, Path> keys = new HashMap<>();
    private static Path syncDirectory;

    // 🧠 THE MEMORY BANK: Tracks files modified by the server
    private static final Set<String> maskedEvents = ConcurrentHashMap.newKeySet();

    public static void maskEvent(String relativePath) {
        maskedEvents.add(relativePath.replace("\\", "/"));
    }

    public static void startWatching() {
        Path userHome = Paths.get(System.getProperty("user.home"));
        syncDirectory = userHome.resolve("Desktop").resolve("SyncFolder");

        try {
            Files.createDirectories(syncDirectory);
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(syncDirectory);

            System.out.println("👀 SyncVault Daemon is active (Two-Way Masking Mode).");
            System.out.println("📂 Watching folder & sub-folders: " + syncDirectory.toAbsolutePath());

            while (true) {
                WatchKey key;
                try { key = watchService.take(); } catch (InterruptedException ex) { return; }

                Path dir = keys.get(key);
                if (dir == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path name = (Path) event.context();
                    Path child = dir.resolve(name);
                    
                    String relativePath = syncDirectory.relativize(child).toString().replace("\\", "/");

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                        System.out.println("📁 New folder detected, attaching watcher: " + relativePath);
                        registerAll(child);
                        continue; 
                    }

                    boolean isKnownJunk = relativePath.endsWith(".tmp") || relativePath.startsWith("~") || relativePath.contains("Zone.Identifier");
                    if (isKnownJunk) continue;

                    // 🛡️ THE SHIELD: Prevent Echo Loop
                    if (maskedEvents.contains(relativePath)) {
                        System.out.println("🛡️ Event masked (Server-originated action): " + relativePath + " [" + kind + "] -> Ignoring loop.");
                        maskedEvents.remove(relativePath); 
                        continue;
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        System.out.println("🗑️ Detected local DELETE on: " + relativePath);
                        FileSender.deleteFile(relativePath);
                        continue;
                    }

                    File changedFile = child.toFile();
                    if (changedFile.isFile() && (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY)) {
                        System.out.println("\n⚡ Detected event on: " + relativePath + " - Waiting for lock...");
                        if (waitForUnlock(changedFile)) {
                            System.out.println("✅ Lock released! Starting upload...");
                            FileSender.uploadFile(changedFile, relativePath); 
                        }
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) keys.remove(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_CREATE, 
                    StandardWatchEventKinds.ENTRY_MODIFY, 
                    StandardWatchEventKinds.ENTRY_DELETE);
                keys.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean waitForUnlock(File file) {
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                raf.close();
                return true; 
            } catch (Exception e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }
}