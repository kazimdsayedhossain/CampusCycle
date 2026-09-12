package com.example.campuscycle.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class ProfilePage extends StackPane {

    public ProfilePage() {
        setAlignment(Pos.CENTER);
        Label profileLabel = new Label("Profile");
        getChildren().add(profileLabel);
    }
}
