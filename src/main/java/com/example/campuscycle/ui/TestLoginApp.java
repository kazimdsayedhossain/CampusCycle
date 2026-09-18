package com.example.campuscycle.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TestLoginApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        LoginView loginView = new LoginView();

        // Temporary test click action to see what you selected
        loginView.getLoginButton().setOnAction(e -> {
            String role = loginView.getSelectedRole();
            String email = loginView.getEmail();
            loginView.setStatus("Selected Role: " + role + " | Email: " + email, false);
        });

        Scene scene = new Scene(loginView, 460, 520);
        primaryStage.setTitle("CampusCycle - Login Test");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}