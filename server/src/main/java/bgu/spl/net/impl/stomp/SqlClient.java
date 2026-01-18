package bgu.spl.net.impl.stomp;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Helper class to communicate with the Python SQL server.
 * Sends SQL strings and receives responses.
 */
public class SqlClient {
    private static final String SQL_HOST = "127.0.0.1";
    private static final int SQL_PORT = 7778;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Execute an SQL command/query by sending it to the Python SQL server.
     * 
     * @param sql The SQL string to execute
     * @return The response from the server, or an error message
     */
    public static String executeSql(String sql) {
        try (Socket socket = new Socket(SQL_HOST, SQL_PORT);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream()) {

            // Send SQL with null terminator (as expected by Python server)
            out.write((sql + "\0").getBytes("UTF-8"));
            out.flush();

            // Read response until null terminator
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1 && b != 0) {
                response.write(b);
            }
            return response.toString("UTF-8");
        } catch (IOException e) {
            System.err.println("[SqlClient] Error: " + e.getMessage());
            return "ERROR:" + e.getMessage();
        }
    }

    /**
     * Get current timestamp formatted for SQL.
     */
    private static String getCurrentTimestamp() {
        return LocalDateTime.now().format(formatter);
    }

    /**
     * Escape single quotes in SQL strings to prevent SQL injection.
     */
    private static String escape(String value) {
        if (value == null)
            return "";
        return value.replace("'", "''");
    }

    // ==================== Convenience Methods ====================

    /**
     * Record a new user registration.
     */
    public static void registerUser(String username, String password) {
        String sql = String.format(
                "INSERT INTO users (username, password, registration_date) VALUES ('%s', '%s', '%s')",
                escape(username), escape(password), getCurrentTimestamp());
        executeSql(sql);
    }

    /**
     * Record a user login.
     */
    public static void recordLogin(String username) {
        String sql = String.format(
                "INSERT INTO login_history (username, login_time) VALUES ('%s', '%s')",
                escape(username), getCurrentTimestamp());
        executeSql(sql);
    }

    /**
     * Record a user logout (update the latest login record without a logout time).
     */
    public static void recordLogout(String username) {
        String sql = String.format(
                "UPDATE login_history SET logout_time = '%s' WHERE username = '%s' AND logout_time IS NULL",
                getCurrentTimestamp(), escape(username));
        executeSql(sql);
    }

    /**
     * Record a file upload via the report command.
     */
    public static void recordFileUpload(String username, String filename, String gameChannel) {
        String sql = String.format(
                "INSERT INTO file_tracking (username, filename, upload_time, game_channel) VALUES ('%s', '%s', '%s', '%s')",
                escape(username), escape(filename), getCurrentTimestamp(), escape(gameChannel));
        executeSql(sql);
    }

    /**
     * Record a channel subscription.
     */
    public static void recordSubscription(String username, String channel) {
        String sql = String.format(
                "INSERT INTO subscriptions (username, channel, subscribe_time) VALUES ('%s', '%s', '%s')",
                escape(username), escape(channel), getCurrentTimestamp());
        executeSql(sql);
    }

    /**
     * Remove a channel subscription (when user unsubscribes).
     */
    public static void removeSubscription(String username, String channel) {
        String sql = String.format(
                "DELETE FROM subscriptions WHERE username = '%s' AND channel = '%s'",
                escape(username), escape(channel));
        executeSql(sql);
    }

    /**
     * Remove all subscriptions for a user (when user disconnects).
     */
    public static void removeAllSubscriptions(String username) {
        String sql = String.format(
                "DELETE FROM subscriptions WHERE username = '%s'",
                escape(username));
        executeSql(sql);
    }
}
