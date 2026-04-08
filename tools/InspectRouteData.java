import java.sql.*;
public class InspectRouteData {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Geodouro", "postgres", "123")) {
      try (PreparedStatement ps = c.prepareStatement(
        "select rp.route_plan_id, rp.user_id, rp.name, rp.description, rp.start_label, rp.start_latitude, rp.start_longitude, rpp.route_plan_point_id, rpp.visit_order, rpp.saved_visit_target_id, svt.observation_id, svt.plant_species_id, svt.publication_id, o.device_observation_id, o.latitude, o.longitude, o.predicted_scientific_name, o.enriched_scientific_name, ps.scientific_name as plant_name, p.title as publication_title, p.description as publication_description from route_plan rp join route_plan_point rpp on rpp.route_plan_id = rp.route_plan_id join saved_visit_target svt on svt.saved_visit_target_id = rpp.saved_visit_target_id left join observation o on o.observation_id = svt.observation_id left join plant_species ps on ps.plant_species_id = svt.plant_species_id left join publication p on p.publication_id = svt.publication_id order by rp.route_plan_id, rpp.visit_order limit 30")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            System.out.println(
              "route=" + rs.getInt("route_plan_id") +
              " name=" + rs.getString("name") +
              " order=" + rs.getInt("visit_order") +
              " svt=" + rs.getInt("saved_visit_target_id") +
              " obs=" + rs.getString("observation_id") +
              " plant=" + rs.getString("plant_species_id") +
              " pub=" + rs.getString("publication_id") +
              " lat=" + rs.getString("latitude") +
              " lon=" + rs.getString("longitude") +
              " pred=" + rs.getString("predicted_scientific_name") +
              " enriched=" + rs.getString("enriched_scientific_name") +
              " plantName=" + rs.getString("plant_name") +
              " pubTitle=" + rs.getString("publication_title")
            );
          }
        }
      }
    }
  }
}
