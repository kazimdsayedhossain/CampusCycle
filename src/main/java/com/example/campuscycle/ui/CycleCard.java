package com.example.campuscycle.ui;

import com.example.campuscycle.model.Cycle;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;


public class CycleCard extends VBox {

    public CycleCard(Cycle cycle)
    {
        setSpacing(8);
        setPadding(new Insets(15));
        setPrefWidth(220);

        setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-border-color: #d1d5db;" +
                        "-fx-border-radius: 10px;" +
                        "-fx-border-width: 1px;"
        );

        Label idLabel = new Label(cycle.cycle_id);
        idLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");

        Label typeLabel= new Label("Type: "+cycle.type);
        typeLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        Label ownerLabel = new Label("Owner: "+ cycle.owner_name);
        ownerLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
        Label conditionLabel = new Label("Condition: "+ cycle.condition);
        conditionLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
        Label phoneLabel = new Label("Contact: " + cycle.ownwer_phone);
        phoneLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");

        getChildren().addAll(idLabel, typeLabel, ownerLabel, conditionLabel, phoneLabel);

    }

}
