package com.example.campuscycle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class databaseConnector {
    private static final String URL = "jdbc:mysql://localhost:3306/campus_cycle_db";
    private static final String USER= "root";
    private static final String PASSWORD = "sayed";
    public static Connection getConnection() throws SQLException{
        return DriverManager.getConnection(URL,USER,PASSWORD);
    }

//    static void main(String[] args) {
//        try{
//            Connection connection= getConnection();
//            System.out.println("Connected");
//            connection.close();
//        }
//        catch(SQLException e)
//        {
//            System.out.println("Database connection failed");
//            e.printStackTrace();
//        }
//
//    }

}
