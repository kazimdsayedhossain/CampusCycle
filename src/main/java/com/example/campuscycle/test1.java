package com.example.campuscycle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class test1 {
    static void main(String[] args) {
        String sql = """
                INSERT INTO cycles(cycle_id, owner_name, owner_phone, cycle_type, physical_condition)
                VALUES (?,?,?,?,?)
                """;


        try (Connection connection = databaseConnector.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "CC-TEST001");
            statement.setString(2, "Sayed");
            statement.setString(3, "01712345678");
            statement.setString(4, "CITY_COMMUTER");
            statement.setString(5, "Perfect");
            statement.executeUpdate();
            System.out.println("Cycle inserted");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
