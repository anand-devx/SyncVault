import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class DatabaseManager {
    
    // 🥷 The Stealth Path
    // 🥷 THE STEALTH PATH: Hides the DB in your Windows User folder so VS Code can't lock it!
        private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.home").replace("\\", "/") + "/.syncvault_stealth.db";
    private static Connection globalConn;

    // 🛡️ THE BOUNCER: Auto-heals the connection and table if they are missing
    private static void ensureConnected() {
        if (globalConn == null) {
            try {
                Class.forName("org.sqlite.JDBC"); 
                globalConn = DriverManager.getConnection(DB_URL);
                
                try (Statement stmt = globalConn.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 5000;"); 
                    stmt.execute("PRAGMA journal_mode = WAL;"); 
                    stmt.execute("CREATE TABLE IF NOT EXISTS CloudState (path TEXT PRIMARY KEY, is_dir BOOLEAN);");
                }
                System.out.println("🗄️ SQLite Database connected and verified!");
            } catch (Exception e) { 
                System.out.println("❌ DB Init Error: " + e.getMessage()); 
            }
        }
    }

    public static synchronized Set<String> getLastKnownFolders() {
        ensureConnected(); // Check safety before reading!
        
        Set<String> folders = new HashSet<>();
        if (globalConn == null) return folders;

        try (Statement stmt = globalConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT path FROM CloudState WHERE is_dir = 1")) {
             
            while (rs.next()) {
                folders.add(rs.getString("path"));
            }
        } catch (Exception e) { 
            System.out.println("❌ DB Read Error: " + e.getMessage()); 
        }
        return folders;
    }
    // ==========================================
        // 🧠 FETCH FILE MEMORY
        // ==========================================
        // ==========================================
            // 🧠 FETCH FILE MEMORY
            // ==========================================
            public static synchronized Set<String> getLastKnownFiles() {
                ensureConnected(); // Check safety before reading!
                
                Set<String> files = new HashSet<>();
                if (globalConn == null) return files;
        
                // Select the 'path' column where it is a file (is_dir = 0)
                try (Statement stmt = globalConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT path FROM CloudState WHERE is_dir = 0")) {
                     
                    while (rs.next()) {
                        files.add(rs.getString("path")); // Fetch the correct column!
                    }
                } catch (Exception e) { 
                    System.out.println("❌ DB Read Error: " + e.getMessage()); 
                }
                return files;
            }
            public static synchronized void updateCloudState(Set<String> files, Set<String> folders) {
                    ensureConnected(); // Check safety before writing!
                    
                    if (globalConn == null) return;
            
                    try {
                        globalConn.setAutoCommit(false); 
                        
                        try (Statement stmt = globalConn.createStatement()) {
                            stmt.execute("DELETE FROM CloudState"); 
                        }
                        
                        try (PreparedStatement pstmt = globalConn.prepareStatement("INSERT INTO CloudState (path, is_dir) VALUES (?, ?)")) {
                            for (String file : files) {
                                pstmt.setString(1, file);
                                pstmt.setBoolean(2, false);
                                pstmt.addBatch();
                            }
                            for (String folder : folders) {
                                pstmt.setString(1, folder);
                                pstmt.setBoolean(2, true);
                                pstmt.addBatch();
                            }
                            pstmt.executeBatch();
                        }
                        globalConn.commit(); 
                        
                    } catch (Exception e) { 
                        System.out.println("❌ DB Write Error: " + e.getMessage()); 
                        try { globalConn.rollback(); } catch (SQLException ex) {}
                    } finally {
                        // 🛡️ GUARANTEED to run, fixing the trapped transaction bug!
                        try { globalConn.setAutoCommit(true); } catch (SQLException ex) {}
                    }
                }
}