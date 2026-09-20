package com.example.campuscycle.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class LoginView extends VBox {

    // Input controls so we can read what the user typed
    private final TextField emailField;
    private final PasswordField passwordField;
    private final RadioButton userRadio;
    private final RadioButton adminRadio;
    private final Label statusLabel;
    private final Button loginButton;

    public LoginView() {

        super(16);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(30, 40, 30, 40));
        setStyle("-fx-background-color: #f8fafc;");


        VBox card = new VBox(14);
        card.setMaxWidth(380);
        card.setPadding(new Insets(28));
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 12, 0, 0, 4);"
        );


        Label appTitle = new Label("CampusCycle");
        appTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");

        Label subtitle = new Label("Sign in to your account");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");


        Label roleTitle = new Label("Select Your Role:");
        roleTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");

        ToggleGroup roleGroup = new ToggleGroup();
        userRadio = new RadioButton("Student / User");
        userRadio.setToggleGroup(roleGroup);
        userRadio.setSelected(true); // Default to User
        userRadio.setStyle("-fx-text-fill: #334155; -fx-cursor: hand;");

        adminRadio = new RadioButton("Admin");
        adminRadio.setToggleGroup(roleGroup);
        adminRadio.setStyle("-fx-text-fill: #334155; -fx-cursor: hand;");

        HBox roleBox = new HBox(20, userRadio, adminRadio);
        roleBox.setAlignment(Pos.CENTER);


        Label emailLabel = new Label("Email Address");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");
        emailField = new TextField();
        emailField.setPromptText("e.g. user@campus.com");
        emailField.setStyle("-fx-pref-height: 36px; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setStyle("-fx-pref-height: 36px; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");


        VBox emailBox = new VBox(4, emailLabel, emailField);
        emailBox.setAlignment(Pos.CENTER_LEFT);

        VBox passBox = new VBox(4, passLabel, passwordField);
        passBox.setAlignment(Pos.CENTER_LEFT);


        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        statusLabel.setWrapText(true);


        loginButton = new Button("Sign In");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setStyle(
                "-fx-background-color: #0284c7; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-pref-height: 40px; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;"
        );


        card.getChildren().addAll(
                appTitle, subtitle,
                roleTitle, roleBox,
                emailBox, passBox,
                statusLabel,
                loginButton
        );


        getChildren().add(card);
    }

    // --- Helper Methods to access input values ---

    public String getEmail() {
        return emailField.getText().trim();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getSelectedRole() {
        return adminRadio.isSelected() ? "ADMIN" : "USER";
    }

    public void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        if (isError) {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc2626;"); // Red
        } else {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #16a34a;"); // Green
        }
    }

    public Button getLoginButton() {
        return loginButton;
    }


}