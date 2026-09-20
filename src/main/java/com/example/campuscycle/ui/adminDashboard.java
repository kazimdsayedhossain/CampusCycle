package com.example.campuscycle.ui;

import com.example.campuscycle.auth.UserSession;
import com.example.campuscycle.database.DatabaseConnection;
import com.example.campuscycle.model.Cycle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;

public class adminDashboard extends VBox {

    private final FlowPane cardContainer;
    private final Stage adminStage;
    private final Runnable onLogout;

    public adminDashboard(Stage adminstage, Runnable onLogout)
    {
        this.adminStage=adminstage;
        this.onLogout=onLogout;

        setSpacing(20);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: #f1f5f9");

        HBox headerBar = createHeaderBar();

        Label SectionTitle = new Label("Pending Cycle Inspections");
        SectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label sectionSubTitle = new Label("Review user-submitted cycles. Approve cycles will apprear in the public rental catagory");
        sectionSubTitle.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        VBox titleBar = new VBox(4, SectionTitle, sectionSubTitle);

        cardContainer = new FlowPane(16,16);
        cardContainer.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(cardContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(headerBar, titleBar,scrollPane);
        loadPendingCycles();
    }

    private HBox createHeaderBar()
    {
        Label logo = new Label("CampusCycle");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");

        Label adminBadge = new Label("Admin Console");
        adminBadge.setStyle(
                "-fx-background-color: #fef08a; -fx-text-fill: #854d0e; " +
                        "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;"
        );

        String adminEmail = UserSession.getInstance().getEmail();
        Label emailLabel = new Label("Logged in as "+adminEmail);
        emailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer,Priority.ALWAYS);

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 14;"
        );

        logoutBtn.setOnAction(e->{UserSession.getInstance().clear(); adminStage.close();
            if(onLogout!=null) onLogout.run();
        });

        HBox header = new HBox(12, logo, adminBadge, spacer, emailLabel, logoutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12,20,12,20));
        header.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 8, 0, 0, 2);"
        );

        return header;
    }

    public void loadPendingCycles()
    {
        cardContainer.getChildren().clear();

        ArrayList<Cycle> pendingList = DatabaseConnection.getPendingCycles();

        if(pendingList.isEmpty())
        {
            Label emptyLable = new Label("There is not cycle pending for approval");
            emptyLable.setStyle("-fx-font-size: 15px; -fx-text-fill:  #64748b; -fx-padding: 30;");
            cardContainer.getChildren().add(emptyLable);
        }

        for(Cycle c: pendingList)
        {
            cardContainer.getChildren().add(createAdminCard(c));
        }
    }

    private VBox createAdminCard(Cycle c)
    {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setPrefWidth(280);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #f59e0b; -fx-border-width: 2; -fx-border-radius: 10;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 6, 0, 0, 2);");
        Label idLable = new Label(c.cycle_id);
        idLable.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");
        Label ownerLabel = new Label("Owner: "+c.owner_name+" ("+c.ownwer_phone+") ");
        ownerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        Label typeLabel = new Label("Type: "+c.type);
        typeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
        Label condLabel = new Label("Condition: "+c.condition);
        condLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #d97706;");
        Button approveBtn = new Button("Approve & Verify");
        approveBtn.setMaxWidth(Double.MAX_VALUE);
        approveBtn.setStyle(
                "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8;"
        );
        approveBtn.setOnAction(e->{
            boolean success = DatabaseConnection.verifyCycle(c.cycle_id);
            if(success)
                loadPendingCycles();
        });

        card.getChildren().addAll(idLable, typeLabel, ownerLabel, condLabel, approveBtn);
        return card;
    }
}
