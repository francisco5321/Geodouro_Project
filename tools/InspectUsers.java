import java.sql.*;
public class InspectUsers {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/Geodouro";
    String user = "postgres";
    String password = "123";
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection(url, user, password);
         PreparedStatement ps = c.prepareStatement("select user_id, is_authenticated, guest_label, first_name, last_name, email, username, password_hash from app_user order by user_id limit 20");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        System.out.println(
          rs.getInt("user_id") + " | auth=" + rs.getBoolean("is_authenticated") +
          " | guest=" + rs.getString("guest_label") +
          " | username=" + rs.getString("username") +
          " | email=" + rs.getString("email") +
          " | pass=" + rs.getString("password_hash")
        );
      }
    }
  }
}
