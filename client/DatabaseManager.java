import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.home").replace("\\", "/") + "/.syncvault_stealth.db";
    private static Connection globalConn;

    // 🚨 THE FIX: Added 'synchronized' so Watcher and Heartbeat threads don't crash the DB on startup
    private static synchronized void ensureConnected() {
        try {
            if (globalConn == null || globalConn.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                globalConn = DriverManager.getConnection(DB_URL);

                try (Statement stmt = globalConn.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 5000;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    stmt.execute("CREATE TABLE IF NOT EXISTS CloudState (path TEXT PRIMARY KEY, is_dir BOOLEAN);");
                }
                System.out.println("🗄️ SQLite Database connected and verified!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ DB Init Error: " + e.getMessage());
            globalConn = null;
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

        try (Statement stmt = globalConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT path FROM CloudState WHERE is_dir = 0")) {
            while (rs.next()) files.add(rs.getString("path"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ DB Read Error: " + e.getMessage());
        }
        return files;
    }

    public static synchronized void updateCloudState(Set<String> files, Set<String> folders) {
        ensureConnected();
        if (globalConn == null) return;

        try {
            globalConn.setAutoCommit(false);

            try (Statement stmt = globalConn.createStatement()) {
                stmt.execute("DELETE FROM CloudState");
            }

            try (PreparedStatement pstmt = globalConn.prepareStatement(
                    "INSERT INTO CloudState (path, is_dir) VALUES (?, ?)")) {
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
            e.printStackTrace();
            System.out.println("❌ DB Write Error: " + e.getMessage());
            try { globalConn.rollback(); } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try { globalConn.setAutoCommit(true); } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}