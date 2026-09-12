package com.example.campuscycle.ui;

import com.example.campuscycle.database.DatabaseConnection;
import com.example.campuscycle.model.Cycle;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class CycleRegistrationForm extends VBox {

    private TextField ownerNameField;
    private TextField ownerPhoneField;
    private ComboBox<Cycle.cycleType> typeComboBox;
    private ComboBox<Cycle.physical_condition> conditionComboBox;
    private DatePicker purchaseDatePicker;
    private CheckBox isVerifiedCheckBox;
    private Button submitButton;

    public CycleRegistrationForm() {
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setMaxWidth(300);

        ownerNameField = new TextField();
        ownerNameField.setPromptText("Enter owner's name");

        ownerPhoneField = new TextField();
        ownerPhoneField.setPromptText("Enter phone number");

        typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll(Cycle.cycleType.values());
        typeComboBox.setPromptText("Select Cycle Type");
        typeComboBox.setMaxWidth(Double.MAX_VALUE);

        conditionComboBox = new ComboBox<>();
        conditionComboBox.getItems().addAll(Cycle.physical_condition.values());
        conditionComboBox.setPromptText("Select Condition");
        conditionComboBox.setMaxWidth(Double.MAX_VALUE);

        purchaseDatePicker = new DatePicker();
        purchaseDatePicker.setPromptText("Select purchase date");
        purchaseDatePicker.setMaxWidth(Double.MAX_VALUE);

        isVerifiedCheckBox = new CheckBox("Is Verified");

        submitButton = new Button("Register Cycle");
        submitButton.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(
                ownerNameField,
                ownerPhoneField,
                typeComboBox,
                conditionComboBox,
                purchaseDatePicker,
                isVerifiedCheckBox,
                submitButton
        );
        submitButton.setOnAction(event->{
            String name=ownerNameField.getText();
            String phone=ownerPhoneField.getText();
            Cycle.cycleType type =typeComboBox.getValue();
            Cycle.physical_condition condition= conditionComboBox.getValue();
            LocalDate date= purchaseDatePicker.getValue();
            ZonedDateTime purchaseDate = (date!=null)? date.atStartOfDay(ZoneId.systemDefault()): null;
            boolean isVerified= isVerifiedCheckBox.isSelected();

            Cycle cycle= new Cycle();
            cycle.register_new_cycle(name,phone,type,condition,purchaseDate,isVerified);
            DatabaseConnection.registerNew(cycle);
        });
    }

    public TextField getOwnerNameField() {
        return ownerNameField;
    }

    public TextField getOwnerPhoneField() {
        return ownerPhoneField;
    }

    public ComboBox<Cycle.cycleType> getTypeComboBox() {
        return typeComboBox;
    }

    public ComboBox<Cycle.physical_condition> getConditionComboBox() {
        return conditionComboBox;
    }

    public DatePicker getPurchaseDatePicker() {
        return purchaseDatePicker;
    }

    public CheckBox getIsVerifiedCheckBox() {
        return isVerifiedCheckBox;
    }

    public Button getSubmitButton() {
        return submitButton;
    }
}
