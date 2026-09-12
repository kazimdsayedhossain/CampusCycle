package com.example.campuscycle;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.BlockingDeque;

public class HelloApplication extends Application {

    @Override
    public void start(Stage firstStage) {

    StackPane root = new StackPane();
    root.setStyle("-fx-background-color: rgba(255, 255, 255, 0.7);"+"-fx-background-radius: 10");
    Scene scene= new Scene(root,600,400);
    scene.setFill(null);
    firstStage.setTitle("CampusCycle");

    VBox mainLayout = new VBox();
    HBox navigationBar = new HBox();
    root.getChildren().add(mainLayout);
//    mainLayout.setStyle("-fx-background-color: rgba(100,150,200,.4);");
    navigationBar.setStyle("-fx-background-color: rgba(30, 30, 30, 0.8);");
    navigationBar.setPrefHeight(60);
    navigationBar.setAlignment(Pos.CENTER_LEFT);
    navigationBar.setSpacing(10);
    navigationBar.setPadding(new Insets(0,20,0,20));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Label title = new Label("CampusCycle");
//    navigationBar.getChildren().add(title);
    title.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;"
        );

    Button homeButton = new Button("Home");
//    navigationBar.getChildren().add(homeButton);
    homeButton.setStyle(
            "-fx-background-color: transparent;"+
                    "-fx-text-fill: white;"+
                    "-fx-font-size: 16px;"
    );

    Button cyclesButton = new Button("Cycles");
    cyclesButton.setStyle(
            "-fx-background-color: transparent;"+
                    "-fx-text-fill: white;"+
                    "-fx-font-size: 16px;"
    );

    Button profileButton = new Button("Profile");
    profileButton.setStyle(
                "-fx-background-color: transparent;"+
                        "-fx-text-fill: white;"+
                        "-fx-font-size: 16px;"
        );

    Button minimizeButton = new Button("−");
    minimizeButton.setStyle(
            "-fx-background-color: transparent;"+
                    "-fx-text-fill: white;"+
                    "-fx-font-size: 20px;"
    );
    Button maximizeButton = new Button("□");
    maximizeButton.setStyle(
            "-fx-background-color: transparent;"+
                    "-fx-text-fill: white;"+
                    "-fx-font-size: 20px;"
    );
    Button closeButton = new Button("×");
    closeButton.setStyle(
            "-fx-background-color: transparent;"+
                    "-fx-text-fill: white;"+
                    "-fx-font-size: 20px;"
    );

    closeButton.setOnAction(even-> firstStage.close());
    minimizeButton.setOnAction(event->firstStage.setIconified(true));
    maximizeButton.setOnAction(event->firstStage.setMaximized(!firstStage.isMaximized()));

    StackPane contentArea = new StackPane();
    contentArea.setAlignment(Pos.CENTER);
    Label homeLabel = new Label("This is Home Tab");
    homeButton.setOnAction(event->{
        contentArea.getChildren().clear();
        contentArea.getChildren().add(homeLabel);
    });

    Label cyclesLabel = new Label("Cycle Page");
    cyclesButton.setOnAction(event->{
        contentArea.getChildren().clear();
        contentArea.getChildren().add(cyclesLabel);
    });

    contentArea.getChildren().add(homeLabel);;
    mainLayout.getChildren().addAll(navigationBar,contentArea);
    navigationBar.getChildren().addAll(title,homeButton,cyclesButton,profileButton,spacer,minimizeButton,maximizeButton,closeButton);
    firstStage.setScene(scene);
    firstStage.initStyle(StageStyle.TRANSPARENT);
    firstStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
