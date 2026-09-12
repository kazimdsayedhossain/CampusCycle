package com.example.campuscycle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class test2 {
    static void main(String[] args) {
        String sql= "select * from cycles";

        try(Connection connection= databaseConnector.getConnection(); PreparedStatement statement= connection.prepareStatement(sql); ResultSet result= statement.executeQuery()){

        while(result.next())
        {
            String cycleId= result.getString("cycle_id");
            String ownerName = result.getString("owner_name");

            System.out.println(cycleId);
            System.out.println(ownerName);
        }

        }catch (SQLException e)
        {

        }
    }
}
