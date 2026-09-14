package com.example.campuscycle.database;

import com.example.campuscycle.model.Cycle;
import com.sun.source.tree.WhileLoopTree;

import javax.naming.ContextNotEmptyException;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;

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

    public static ArrayList<Cycle> getAllCycles()
    {
        ArrayList<Cycle> list = new ArrayList<>();

        String sql= "SELECT * FROM cycles";

        try(Connection connection = getConnection(); PreparedStatement statement= connection.prepareStatement(sql); ResultSet result= statement.executeQuery();)
        {
            while (result.next())
            {
                Cycle cycle= new Cycle();
                cycle.cycle_id= result.getString("cycle_id");
                cycle.owner_name=result.getString(("owner_name"));
                cycle.ownwer_phone=result.getString("owner_phone");

                String cycle_type_string = result.getString("cycle_type");
                if(cycle_type_string!=null)
                {
                    cycle.type=Cycle.cycleType.valueOf(cycle_type_string);
                }

                Timestamp purTs=result.getTimestamp("purchase_date");
                if(purTs!=null)
                {
                    cycle.purchase_data=purTs.toInstant().atZone(ZoneId.systemDefault());
                }
                Timestamp regTs= result.getTimestamp("registered_at");
                if(regTs!=null)
                {
                    cycle.registered_at=regTs.toInstant().atZone(ZoneId.systemDefault());
                }

                cycle.is_gear=result.getBoolean("is_gear");
                cycle.needs_fuel=result.getBoolean("needs_fuel");
                cycle.is_verified=result.getBoolean(("is_verified"));
                cycle.needs_liscence=result.getBoolean("needs_liscence");

                list.add(cycle);
            }
        }catch(SQLException e)
        {
            e.printStackTrace();
        }

        return list;
    }
}
