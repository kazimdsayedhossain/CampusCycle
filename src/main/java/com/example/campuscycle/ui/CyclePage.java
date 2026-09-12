package com.example.campuscycle.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class CyclePage extends StackPane {

    public CyclePage() {
        setAlignment(Pos.CENTER);
        Label cyclesLabel = new Label("Cycle Page");
        getChildren().add(cyclesLabel);
    }
}
