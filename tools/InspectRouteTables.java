import java.sql.*;
public class InspectRouteTables {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Geodouro", "postgres", "123")) {
      try (PreparedStatement ps = c.prepareStatement(
        "select table_name from information_schema.tables where table_schema = 'public' and (table_name like '%route%' or table_name like '%visit%' or table_name like '%target%') order by table_name")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String table = rs.getString(1);
            System.out.println("TABLE=" + table);
            try (PreparedStatement ps2 = c.prepareStatement(
              "select column_name, data_type from information_schema.columns where table_schema='public' and table_name=? order by ordinal_position")) {
              ps2.setString(1, table);
              try (ResultSet rs2 = ps2.executeQuery()) {
                while (rs2.next()) {
                  System.out.println("  " + rs2.getString(1) + " | " + rs2.getString(2));
                }
              }
            }
          }
        }
      }
    }
  }
}
