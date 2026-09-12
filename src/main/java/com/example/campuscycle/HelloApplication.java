package com.example.campuscycle;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;


public class HelloApplication extends Application {
    @Override
    public void start(Stage firstStage) {
        // 1. Initialize the StackPane root node
        StackPane root = new StackPane();

        // 2. Initialize the Scene with the root node and window dimensions (width, height)
        Scene firstStageScene = new Scene(root, 600, 400);

        // 3. Set up and show the stage (window)
        firstStage.setTitle("CampuCycle");
        Image title_img = new Image(getClass().getResource("/com/example/campuscycle/title_image.png").toExternalForm());
        firstStage.getIcons().add(title_img);



        firstStage.setScene(firstStageScene);
        firstStage.show();
    }
}
