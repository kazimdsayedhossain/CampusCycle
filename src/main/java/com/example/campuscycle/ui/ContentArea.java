package com.example.campuscycle.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

public class ContentArea extends StackPane {

    public ContentArea() {
        setAlignment(Pos.CENTER);
        showPage(new HomePage());
    }

    public void showPage(Node newPage) {
        getChildren().clear();
        getChildren().add(newPage);
    }
}
