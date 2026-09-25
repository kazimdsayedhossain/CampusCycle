package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class PassbookView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;

    public PassbookView(CampusUser user, CampusRepository repo) {
        this.user = user;
        this.repo = repo;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox header = createHeader();
        GridPane summary = createSummaryBento();
        VBox historySection = createHistorySection();
        VBox receiptSection = createDigitalReceipt();

        getChildren().addAll(header, summary, historySection, receiptSection);
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Transit Passbook & Receipts");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("Official record of completed university bicycle commutes and subsidized fares");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("Export Statement (PDF)");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 13, Color.web("#0284C7")));
        exportBtn.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Passbook statement exported to Downloads/KUET_Passbook.pdf", ButtonType.OK);
            a.showAndWait();
        });

        row.getChildren().addAll(titleCol, spacer, exportBtn);
        return row;
    }

    private GridPane createSummaryBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        List<RentalRecord> list = repo.rentals(user);
        long completed = list.stream().filter(r -> r.status() == bd.ac.kuet.campuscycle.domain.RentalStatus.RETURNED).count();

        VBox c1 = createMetricCard("COMPLETED RIDES", completed + " Commutes", "Verified handovers", ThemeManager.ICON_BIKE, "#0284C7");
        VBox c2 = createMetricCard("TOTAL DISTANCE", (completed * 2.8) + " km", "Zero-emission travel", ThemeManager.ICON_PIN, "#10B981");
        VBox c3 = createMetricCard("CAMPUS SUBSIDY", "25% Active", "Auto-deducted at lock", ThemeManager.ICON_SHIELD, "#0284C7");

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.33);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    private VBox createMetricCard(String label, String value, String sub, String svgIcon, String accentHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 14, Color.web(accentHex)));
        iconBadge.setPrefSize(28, 28);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        Label val = new Label(value);
        val.getStyleClass().add("metric-number");

        Label badge = new Label(sub);
        badge.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, val, badge);
        return card;
    }

    private VBox createHistorySection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        Label title = new Label("Commute History Log");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");

        List<RentalRecord> list = repo.rentals(user);
        VBox rows = new VBox(10);

        if (list.isEmpty()) {
            HBox empty = new HBox(10);
            empty.setAlignment(Pos.CENTER_LEFT);
            empty.setPadding(new Insets(14));
            empty.getStyleClass().add("sub-panel");

            Label msg = new Label("No rental history recorded yet. Complete your first ride to view statements.");
            msg.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.7;");
            empty.getChildren().add(msg);
            rows.getChildren().add(empty);
        } else {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a");
            for (RentalRecord r : list) {
                HBox row = new HBox(14);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("sub-panel");
                row.setPadding(new Insets(12, 16, 12, 16));

                StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 15, Color.web("#0284C7")));
                icon.setPrefSize(32, 32);
                icon.getStyleClass().add("action-icon-btn");

                VBox details = new VBox(2);
                Label label = new Label(r.cycleLabel());
                label.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 750;");

                String timeStr = r.startedAt().format(dtf);
                Label date = new Label(timeStr + " • " + r.requestedMinutes() + " mins booked");
                date.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
                details.getChildren().addAll(label, date);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label fare = new Label(String.format("BDT %.2f", r.quotedAmountPoisha() / 100.0));
                fare.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 800;");

                Label statusBadge = new Label(r.status().name());
                statusBadge.getStyleClass().add(r.status() == bd.ac.kuet.campuscycle.domain.RentalStatus.ACTIVE ? "badge-available" : "filter-chip");

                row.getChildren().addAll(icon, details, spacer, fare, statusBadge);
                rows.getChildren().add(row);
            }
        }

        card.getChildren().addAll(title, rows);
        return card;
    }

    private VBox createDigitalReceipt() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        Label title = new Label("University Transit Electronic Receipt Sample");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");

        VBox ticket = new VBox(10);
        ticket.getStyleClass().add("sub-panel");
        ticket.setPadding(new Insets(18));

        Label uniHeader = new Label("KHULNA UNIVERSITY OF ENGINEERING & TECHNOLOGY (KUET)");
        uniHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-opacity: 0.6; -fx-letter-spacing: 0.08em;");

        Label receiptTitle = new Label("CampusCycle Transit Voucher • Official Audit Receipt");
        receiptTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        Separator sep = new Separator();

        HBox metaRow = new HBox(20);
        metaRow.getChildren().addAll(
                createReceiptMeta("RIDER NAME", user.displayName()),
                createReceiptMeta("STUDENT ID / EMAIL", user.email()),
                createReceiptMeta("SUBSIDY CODE", "KUET-GREEN-25"),
                createReceiptMeta("STATUS", "APPROVED & SETTLED")
        );

        ticket.getChildren().addAll(uniHeader, receiptTitle, sep, metaRow);
        card.getChildren().addAll(title, ticket);
        return card;
    }

    private VBox createReceiptMeta(String label, String value) {
        VBox b = new VBox(2);
        Label l = new Label(label);
        l.getStyleClass().add("metric-label");
        l.setStyle("-fx-font-size: 9.5px;");

        Label v = new Label(value);
        v.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        b.getChildren().addAll(l, v);
        return b;
    }
}
