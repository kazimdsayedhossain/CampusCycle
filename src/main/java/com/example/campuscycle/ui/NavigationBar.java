package com.example.campuscycle.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class NavigationBar extends HBox {

    public NavigationBar(Stage firstStage, ContentArea contentArea) {
        setStyle("-fx-background-color: rgba(30, 30, 30, 0.8);");
        setPrefHeight(60);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(0, 20, 0, 20));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("CampusCycle");
        title.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;"
        );

        Button homeButton = new Button("Home");
        homeButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;"
        );

        Button cyclesButton = new Button("Cycles");
        cyclesButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;"
        );

        Button registrationButton = new Button("Registration");
        registrationButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;"
        );

        Button profileButton = new Button("Profile");
        profileButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;"
        );

        Button minimizeButton = new Button("−");
        minimizeButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;"
        );

        Button maximizeButton = new Button("□");
        maximizeButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;"
        );

        Button closeButton = new Button("×");
        closeButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;"
        );

        closeButton.setOnAction(event -> firstStage.close());
        minimizeButton.setOnAction(event -> firstStage.setIconified(true));
        maximizeButton.setOnAction(event -> firstStage.setMaximized(!firstStage.isMaximized()));

        homeButton.setOnAction(event -> contentArea.showPage(new HomePage()));
        cyclesButton.setOnAction(event -> contentArea.showPage(new CyclePage()));
        profileButton.setOnAction(event -> contentArea.showPage(new ProfilePage()));
        registrationButton.setOnAction(event -> contentArea.showPage(new CycleRegistrationForm()));

        getChildren().addAll(title, homeButton, cyclesButton, registrationButton, profileButton, spacer, minimizeButton, maximizeButton, closeButton);
    }
}
