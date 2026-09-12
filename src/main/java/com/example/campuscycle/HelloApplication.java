package com.example.campuscycle;

import com.example.campuscycle.ui.ContentArea;
import com.example.campuscycle.ui.NavigationBar;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class HelloApplication extends Application {

    @Override
    public void start(Stage firstStage) {
        firstStage.setFullScreen(true);
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(255, 255, 255, 0.7);" + "-fx-background-radius: 10");
        Scene scene = new Scene(root, 600, 400);
        scene.setFill(null);
        firstStage.setTitle("CampusCycle");

        VBox mainLayout = new VBox();
        root.getChildren().add(mainLayout);

        ContentArea contentArea = new ContentArea();
        NavigationBar navigationBar = new NavigationBar(firstStage, contentArea);

        mainLayout.getChildren().addAll(navigationBar, contentArea);

        firstStage.setScene(scene);
        firstStage.initStyle(StageStyle.TRANSPARENT);
        firstStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
