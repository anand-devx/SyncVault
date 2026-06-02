import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributes;

public class FolderWatcher {
    public static void main(String[] args) {
        Path syncDirectory = Paths.get("SyncFolder");

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            
            syncDirectory.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE, 
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            System.out.println("👀 SyncVault Daemon is active (Aggressive Sweeper Mode).");
            System.out.println("📂 Watching folder: " + syncDirectory.toAbsolutePath());

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take(); 
                } catch (InterruptedException ex) {
                    return;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    Path fullPath = syncDirectory.resolve(fileName);
                    String fileNameStr = fileName.toString();
                    
                    // 1. Check OS Attributes
                    boolean isHiddenOSFile = false;
                    try {
                        DosFileAttributes attrs = Files.readAttributes(fullPath, DosFileAttributes.class);
                        isHiddenOSFile = attrs.isHidden() || attrs.isSystem();
                    } catch (Exception e) {}

                    // 2. The Text Filter (Because Windows Antivirus cheats)
                    boolean isKnownJunk = fileNameStr.contains("Zone.Identifier") || 
                                          fileNameStr.toLowerCase().contains("smartscreen") ||
                                          fileNameStr.endsWith(".tmp") || 
                                          fileNameStr.startsWith("~") ||
                                          fileNameStr.equalsIgnoreCase("desktop.ini") || 
                                          fileNameStr.equalsIgnoreCase("Thumbs.db");

                    // ==========================================
                    // 🔥 THE AGGRESSIVE SWEEPER
                    // ==========================================
                    if (isHiddenOSFile || isKnownJunk) {
                        System.out.println("🧹 Caught Junk/OS file: " + fileNameStr);
                        
                        // We only auto-delete the junk, we don't try to delete critical hidden OS files
                        if (isKnownJunk && !fileNameStr.equalsIgnoreCase("desktop.ini")) {
                            try {
                                Files.deleteIfExists(fullPath);
                                System.out.println("🔥 Auto-deleted junk file locally!");
                            } catch (Exception e) {
                                // Sometimes Windows Antivirus locks the file while scanning it, so Java can't delete it instantly.
                                System.out.println("🔒 Ignored (Windows is currently locking this file).");
                            }
                        }
                        continue; // Stop processing so it never uploads!
                    }
                    // ==========================================

                    // Handle File Deletions (Sync to Cloud)
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        System.out.println("🗑️ Detected DELETE on: " + fileNameStr);
                        FileSender.deleteFile(fileNameStr);
                        continue;
                    }

                    // Handle File Uploads/Modifications
                    File changedFile = fullPath.toFile();
                    if (changedFile.isFile()) {
                        System.out.println("\n⚡ Detected " + kind.name() + " event on: " + fileNameStr);
                        FileSender.uploadFile(changedFile);
                    }
                }
                boolean valid = key.reset();
                if (!valid) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}