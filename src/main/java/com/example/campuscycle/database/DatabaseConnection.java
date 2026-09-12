package com.example.campuscycle.database;

import com.example.campuscycle.model.Cycle;

import java.lang.reflect.Type;
import java.sql.*;

public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/campus_cycle_db";
    private static final String USER = "root";
    private static final String PASSWORD = "sayed";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void registerNew(Cycle cycle)
    {
        String sql = """
        INSERT INTO cycles (
            cycle_id, owner_name, owner_phone, cycle_type,
            physical_condition, purchase_date, registered_at,
            is_gear, needs_fuel, needs_liscence, is_verified
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

        try(Connection connection = getConnection(); PreparedStatement statement=connection.prepareStatement(sql)) {
            statement.setString(1, cycle.cycle_id);
            statement.setString(2, cycle.owner_name);
            statement.setString(3, cycle.ownwer_phone);
            statement.setString(4, cycle.type != null ? cycle.type.name() : null);
            statement.setString(5, cycle.condition != null ? cycle.condition.name() : null);

            if(cycle.purchase_data!=null)
            {
                statement.setTimestamp(6, Timestamp.from(cycle.purchase_data.toInstant()));
            }
            else{
                statement.setNull(6, Types.TIMESTAMP);
            }
            statement.setTimestamp(7, Timestamp.from((cycle.registered_at.toInstant())));
            statement.setBoolean(8, cycle.is_gear);
            statement.setBoolean(9, cycle.needs_fuel);
            statement.setBoolean(10, cycle.needs_liscence);
            statement.setBoolean(11, cycle.is_verified);

            statement.executeUpdate();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }
    }
}
