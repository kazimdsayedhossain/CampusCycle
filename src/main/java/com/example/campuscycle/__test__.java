package com.example.campuscycle;

import com.example.campuscycle.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class __test__ {

    // Note: MUST be 'public static void main'
    public static void main(String[] args) {
        String sql = "SELECT cycle_id, owner_name, cycle_type, is_verified, is_available FROM cycles";

        System.out.println("Connecting to Supabase PostgreSQL...");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            System.out.println("Connected successfully! Cycles found in cloud:");
            System.out.println("--------------------------------------------------");

            while (result.next()) {
                String cycleId = result.getString("cycle_id");
                String ownerName = result.getString("owner_name");
                String type = result.getString("cycle_type");
                boolean verified = result.getBoolean("is_verified");
                boolean available = result.getBoolean("is_available");

                System.out.println("ID: " + cycleId + " | Owner: " + ownerName + " | Type: " + type + " | Verified: " + verified + " | Available: " + available);
            }

            System.out.println("--------------------------------------------------");

        } catch (SQLException e) {
            System.err.println("Connection failed!");
            e.printStackTrace();
        }
    }
}