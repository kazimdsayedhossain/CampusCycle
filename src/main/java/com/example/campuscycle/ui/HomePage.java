package com.example.campuscycle.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class HomePage extends StackPane {

    public HomePage() {
        setAlignment(Pos.CENTER);
        Label homeLabel = new Label("This is Home Tab");
        getChildren().add(homeLabel);
    }
}
