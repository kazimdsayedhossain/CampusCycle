package com.example.campuscycle;

import com.example.campuscycle.auth.AuthService;
import com.example.campuscycle.auth.UserSession;
import com.example.campuscycle.ui.ContentArea;
import com.example.campuscycle.ui.LoginView;
import com.example.campuscycle.ui.NavigationBar;
import  com.example.campuscycle.ui.adminDashboard;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class HelloApplication extends Application {

    @Override
    public void start(Stage loginStage)
    {
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
                String result = AuthService.login(email,password);

                Platform.runLater(()->{
                    if("Success".equalsIgnoreCase(result))
                    {
                        loginStage.close();
                        if(UserSession.getInstance().isAdmin())
                        {
                            showAdminDashboard();
                        }
                        else showDashboard();
                    }
                    else{
                        loginView.setStatus(result, true);
                    }
                });
            }).start();
        });

        Scene loginScene = new Scene(loginView, 460,520);
        loginStage.setTitle("CampusCycle login");
        loginStage.setResizable(false);
        loginStage.setScene(loginScene);
        loginStage.show();
    }

    private void  showAdminDashboard()
    {
        Stage adminStage = new Stage();
        adminStage.setTitle("CampusCycle Admin");
        adminDashboard AdminDashBoard = new adminDashboard(adminStage, ()->{
            start(new Stage());
        });

        Scene scene = new Scene(AdminDashBoard, 900, 600);
        adminStage.setScene(scene);
        adminStage.show();
    }

    private void showDashboard()
    {
        Stage dashBoardStage = new Stage();
        dashBoardStage.setFullScreen(false);
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(255,255,255,0.8);-fx-background-radius: 5");

        Scene scene = new Scene(root, 600,400);
        scene.setFill(null);
        dashBoardStage.setTitle("CampusCycle Dashboard");

        VBox mainLayout = new VBox();
        root.getChildren().add(mainLayout);

        ContentArea contentArea = new ContentArea();
        NavigationBar navigationBar = new NavigationBar(dashBoardStage, contentArea);

        mainLayout.getChildren().addAll(navigationBar, contentArea);

        dashBoardStage.setScene(scene);
        dashBoardStage.initStyle(StageStyle.TRANSPARENT);
        dashBoardStage.show();

    }


    static void main(String[] args) {
        launch();
    }

}
