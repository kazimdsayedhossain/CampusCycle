package bd.ac.kuet.campuscycle.data;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseConstraintCheckTest {

    @Test
    void checkConstraints() {
        if (!DatabaseConnection.isAvailable()) return;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("""
                SELECT tc.table_name, tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc ON tc.constraint_name = cc.constraint_name
                WHERE tc.table_schema = 'public' AND tc.table_name IN ('rentals', 'payment_records')
            """)) {
                while (rs.next()) {
                    System.out.printf("CONSTRAINT: %s.%s => %s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
