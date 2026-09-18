package com.example.campuscycle;

import com.example.campuscycle.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.example.campuscycle.auth.AuthService;
import javafx.application.Platform;

public class __test__ extends Application {

    public void start(Stage primaryStage){
        LoginView loginView = new LoginView();

        loginView.getLoginButton().setOnAction(e->{
            String email = loginView.getEmail();
            String password = loginView.getPassword();

            if(email.isEmpty() || password.isEmpty())
            {
                loginView.setStatus("Enter both email and password", true);
                return;
            }

            loginView.setStatus("Signing in...", false);

            new Thread(()->{
               String result = AuthService.login(email, password);
               Platform.runLater(()->{
                   if(result=="Success")
                   {
                       loginView.setStatus("Login successful! Welcome", false);
                   }
                   else {
                       loginView.setStatus(result, true);
                   }
               });
            }).start();
        });

        Scene scene = new Scene(loginView, 460,520);
        primaryStage.setTitle("CampusCycle - Login Test");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }


    static void main(String[] args) {
    launch(args);
    }
}
