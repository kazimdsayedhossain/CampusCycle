package com.example.campuscycle.ui;

import com.example.campuscycle.database.DatabaseConnection;
import com.example.campuscycle.model.Cycle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BookingDialog extends Stage{

    private final double BASE_FARE = 20.0;
    public BookingDialog(Cycle cycle, Runnable onBookingConfirmed) {
        // 1. Make it a modal popup (blocks interaction with the main window until closed)
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Reserve Cycle - " + cycle.cycle_id);
        VBox layout = new VBox(12);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPrefWidth(350);

        Label titleLabel= new Label("Reserve Cycle: "+cycle.cycle_id);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");

        Label typeLabel = new Label("Type: "+ cycle.type);
        Label ownerLabel = new Label("Owner: "+cycle.owner_name+"("+cycle.ownwer_phone+")");
        Label condLabel = new Label("Condition: "+cycle.condition);

        Label sliderTitle = new Label("Select Reservation Duration");
        sliderTitle.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        Slider slider = new Slider(15,180,15);
        slider.setMajorTickUnit(15);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);

        Label durationLabel = new Label("Duration: 15 min");
        Label priceLabel = new Label(String.format("Total Price: $%.2f", BASE_FARE));
        priceLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");

        slider.valueProperty().addListener((obs, oldval, newVal)->{
            int duration = newVal.intValue();
            durationLabel.setText("Duration: " + duration + " mins");
            int extraSteps = (duration - 15) / 15;
            double totalPrice = BASE_FARE * Math.pow(1.05, extraSteps);
            priceLabel.setText(String.format("Total Price: $%.2f", totalPrice));
        });


        Button confirmButton = new Button("Confirm Booking");
        confirmButton.setMaxWidth(Double.MAX_VALUE);
        confirmButton.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        confirmButton.setOnAction(e->{
            boolean success = DatabaseConnection.bookCycle(cycle.cycle_id);
            if(success){
                close();
                if(onBookingConfirmed!=null)
                {
                    onBookingConfirmed.run();
                }
            }
        });

    layout.getChildren().addAll(   titleLabel, typeLabel, ownerLabel, condLabel,
            sliderTitle, slider, durationLabel, priceLabel, confirmButton);

    setScene(new Scene(layout));

    }
}
