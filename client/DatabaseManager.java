import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = ConfigManager.DB_URL;
    private static Connection globalConn;

    // 🚨 THE FIX: Added 'synchronized' so Watcher and Heartbeat threads don't crash the DB on startup
    private static synchronized void ensureConnected() {
        // System.out.println("DEBUG: Ensuring connection to DB at path: " + DB_URL);
        try {
            if (globalConn == null || globalConn.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                System.out.println("DEBUG: Connecting to: " + DB_URL); // Log the actual path
                globalConn = DriverManager.getConnection(DB_URL);
    
                try (Statement stmt = globalConn.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 5000;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    
                    // 1. Create table if missing
                    stmt.execute("CREATE TABLE IF NOT EXISTS CloudState (path TEXT PRIMARY KEY, is_dir BOOLEAN, is_deleted INTEGER DEFAULT 0);");
                    
                    // 2. FORCE ADD THE COLUMN (If it fails, it just means the column exists, which is fine)
                    try {
                        stmt.execute("ALTER TABLE CloudState ADD COLUMN is_deleted INTEGER DEFAULT 0;");
                        System.out.println("✅ Schema migration: Added is_deleted column.");
                    } catch (SQLException e) {
                        // Column likely already exists, ignore
                        System.out.println("ℹ️ Schema check complete.");
                    }
                }
                System.out.println("🗄️ SQLite Database connected and verified!");
            }
        } catch (Exception e) {
            System.err.println("❌ CRITICAL DB ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static synchronized void markAsDeleted(String path) {
        ensureConnected();
        if (globalConn == null) return;
    
        String sql = "UPDATE CloudState SET is_deleted = 1 WHERE path = ?";
        try (PreparedStatement pstmt = globalConn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static synchronized Set<String> getLastKnownFolders() {
        ensureConnected();
        Set<String> folders = new HashSet<>();
        if (globalConn == null) return folders;

        try (Statement stmt = globalConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT path FROM CloudState WHERE is_dir = 1")) {
            while (rs.next()) folders.add(rs.getString("path"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ DB Read Error: " + e.getMessage());
        }
        return folders;
    }

    public static synchronized Set<String> getLastKnownFiles() {
        ensureConnected();
        Set<String> files = new HashSet<>();
        if (globalConn == null) return files;
    
        // 🚨 Filter out deleted files here
        String sql = "SELECT path FROM CloudState WHERE is_dir = 0 AND is_deleted = 0";
        try (Statement stmt = globalConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) files.add(rs.getString("path"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }
    public static synchronized void purgeDeletedFiles() {
        ensureConnected();
        if (globalConn == null) return;
        
        try (Statement stmt = globalConn.createStatement()) {
            stmt.execute("DELETE FROM CloudState WHERE is_deleted = 1");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // DatabaseManager.java
    public static synchronized boolean isMarkedDeleted(String path) {
        ensureConnected();
        if (globalConn == null) return false;
    
        // We check the 'is_deleted' flag for the specific file
        String sql = "SELECT is_deleted FROM CloudState WHERE path = ?";
        try (PreparedStatement pstmt = globalConn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_deleted") == 1;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // If not found or error, assume NOT deleted (so we can sync)
    }
    
    // Add this to DatabaseManager.java
        public static synchronized void markAsDeletedBatch(List<String> paths) {
            ensureConnected();
            if (globalConn == null || paths.isEmpty()) return;
    
            try {
                globalConn.setAutoCommit(false); // Start transaction
                String sql = "UPDATE CloudState SET is_deleted = 1 WHERE path = ?";
                
                try (PreparedStatement pstmt = globalConn.prepareStatement(sql)) {
                    for (String path : paths) {
                        pstmt.setString(1, path);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch(); // Execute all at once
                }
                globalConn.commit(); // Commit transaction
            } catch (SQLException e) {
                e.printStackTrace();
                try { globalConn.rollback(); } catch (Exception ignore) {}
            } finally {
                try { globalConn.setAutoCommit(true); } catch (Exception ignore) {}
            }
        }
    public static synchronized void updateCloudState(Set<String> files, Set<String> folders) {
            ensureConnected();
            try {
                globalConn.setAutoCommit(false);
                
                // Do NOT use "DELETE FROM CloudState" here. 
                // We only UPSERT so tombstoned flags (is_deleted) are preserved.
                String upsertSql = "INSERT INTO CloudState (path, is_dir, is_deleted) VALUES (?, ?, 0) " +
                                   "ON CONFLICT(path) DO UPDATE SET is_deleted = 0";
                                   
                try (PreparedStatement pstmt = globalConn.prepareStatement(upsertSql)) {
                    for (String file : files) {
                        pstmt.setString(1, file);
                        pstmt.setBoolean(2, false);
                        pstmt.addBatch();
                    }
                    // (Add folders similarly if needed)
                    pstmt.executeBatch();
                }
                globalConn.commit();
            } catch (Exception e) { 
                e.printStackTrace(); 
                try { globalConn.rollback(); } catch (Exception ignore) {}
            } finally {
                try { globalConn.setAutoCommit(true); } catch (Exception ignore) {}
            }
        }
}