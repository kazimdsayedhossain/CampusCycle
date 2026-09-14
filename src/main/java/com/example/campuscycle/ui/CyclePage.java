package com.example.campuscycle.ui;

import com.example.campuscycle.database.DatabaseConnection;
import com.example.campuscycle.model.Cycle;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;

public class CyclePage extends VBox {

    private FlowPane cardContainer;

    public CyclePage() {
        setAlignment(Pos.CENTER);
        setSpacing(10);

        cardContainer = new FlowPane();
        cardContainer.setHgap(15);
        cardContainer.setVgap(15);
        cardContainer.setAlignment(Pos.CENTER);

        loadCycles();

        ScrollPane scrollPane = new ScrollPane(cardContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        getChildren().add(scrollPane);
    }

    public void loadCycles() {
        cardContainer.getChildren().clear();
        ArrayList<Cycle> cycleList = DatabaseConnection.getAllCycles();
        for (Cycle c : cycleList) {
            cardContainer.getChildren().add(new CycleCard(c, this::loadCycles));
        }
    }
}
