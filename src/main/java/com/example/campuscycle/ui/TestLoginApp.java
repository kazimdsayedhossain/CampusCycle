package com.example.campuscycle.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.example.campuscycle.auth.AuthService;
import javafx.application.Platform;

public class TestLoginApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        LoginView loginView = new LoginView();

        // Temporary test click action to see what you selected
        loginView.getLoginButton().setOnAction(e -> {
            // Step 1: Read the typed text
            String email = loginView.getEmail();
            String password = loginView.getPassword();

            // Step 2: Validate - make sure fields aren't empty
            if (email.isEmpty() || password.isEmpty()) {
                loginView.setStatus("Please enter both email and password.", true);
                return;
            }

            // Step 3: Show temporary "Signing in..." message
            loginView.setStatus("Signing in...", false);

            // Step 4: Run the network call in a background thread
            new Thread(() -> {
                String result = AuthService.login(email, password);

                // Step 5: Send the result back to the JavaFX UI thread
                Platform.runLater(() -> {
                    if ("Success".equalsIgnoreCase(result)) {
                        loginView.setStatus("Login Successful! Welcome.", false);
                    } else {
                        loginView.setStatus(result, true); // Red error message
                    }
                });
            }).start();
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