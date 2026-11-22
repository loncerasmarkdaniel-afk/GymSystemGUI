import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection helper for the Group 6 Gym Membership System.
 * Put this file in the SAME folder as GymManagerGUI.java.
 *
 * Laragon defaults: user = root, password = (empty), port = 3306
 */
public class DB {

    // ---- Change these only if your Laragon setup is different ----
    private static final String HOST     = "localhost";
    private static final String PORT     = "3306";
    private static final String DATABASE = "gym_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "";

    // Set to true ONLY if you are using the MariaDB Connector/J jar
    // instead of the MySQL Connector/J jar.
    private static final boolean USE_MARIADB_DRIVER = false;

    private static final String URL;

    static {
        if (USE_MARIADB_DRIVER) {
            URL = "jdbc:mariadb://" + HOST + ":" + PORT + "/" + DATABASE;
        } else {
            URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila";
        }
    }

    // Returns a new connection. Use it inside try-with-resources so it closes itself:
    //   try (Connection con = DB.getConnection()) { ... }
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Quick connection test. Run this file by itself:
    //   java -cp ".;mysql-connector-j-8.4.0.jar" DB
    public static void main(String[] args) {
        try (Connection con = getConnection()) {
            System.out.println("Connected to database: " + con.getCatalog());
        } catch (SQLException e) {
            System.out.println("Connection FAILED: " + e.getMessage());
            System.out.println("Checklist:");
            System.out.println(" 1. Is Laragon running (Start All)?");
            System.out.println(" 2. Did you run the gym_db SQL script in HeidiSQL?");
            System.out.println(" 3. Is the connector .jar in the classpath (-cp)?");
            System.out.println(" 4. Are USER / PASSWORD / PORT above correct?");
        }
    }
}
