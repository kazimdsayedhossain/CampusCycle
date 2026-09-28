package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.SupportConversation;
import bd.ac.kuet.campuscycle.domain.SupportMessage;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modern Help and Student Support Portal.
 * Uses the clean Bento Card system, structured chat bubbles, live ticket states,
 * and responsive messaging layout.
 */
public class SupportView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final ObservableList<SupportConversation> conversations = FXCollections.observableArrayList();
    private final ListView<SupportConversation> convoList = new ListView<>(conversations);

    private final VBox messageContainer = new VBox(12);
    private final ScrollPane threadScroll = new ScrollPane(messageContainer);
    private final Label threadTitle = new Label("Select a Conversation");
    private final Label threadSub = new Label("Messages will appear here");
    private final Label threadBadge = new Label("Active");

    private final TextField subjectField = new TextField();
    private final TextArea newTicketArea = new TextArea();
    private final TextArea composer = new TextArea();
    private final Button createBtn = new Button("+ Create Ticket");
    private final Button clearSelectionBtn = new Button("New Inquiry");
    private SupportConversation selected;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a");

    public SupportView(CampusUser user, CampusRepository repo) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;

        setSpacing(20);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

        HBox header = createHeader();
        HBox main = createMainLayout();

        getChildren().addAll(header, main);
        ThemeManager.applyFadeIn(this);
        loadConversations();
    }

    private HBox createHeader() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label("Connect with the KUET Cycle Office team for ride assistance, account queries, and lost items");
        sub.getStyleClass().add("view-subtitle");
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -fx-ink-700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hotline = new HBox(8);
        hotline.setAlignment(Pos.CENTER);
        hotline.getStyleClass().add("filter-chip");
        hotline.setPadding(new Insets(6, 12, 6, 12));
        Label hotLabel = new Label("Office Hours: 8:00 AM – 8:00 PM");
        hotLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600;");
        hotline.getChildren().addAll(
                ThemeManager.createIcon(ThemeManager.ICON_CLOCK, 14, Color.web("#10B981")),
                hotLabel
        );

        row.getChildren().addAll(sub, spacer, hotline);
        return row;
    }

    private HBox createMainLayout() {
        HBox split = new HBox(18);
        split.setAlignment(Pos.TOP_LEFT);

        // Left Panel (Ticket List & Creation)
        VBox left = new VBox(14);
        left.getStyleClass().add("bento-card");
        left.setPrefWidth(360);
        left.setMinWidth(280);
        left.setMaxWidth(380);
        left.setPadding(new Insets(20));

        Label leftTitle = new Label("Active Inquiries");
        leftTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        convoList.setPrefHeight(340);
        convoList.getStyleClass().add("modern-list-view");
        convoList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SupportConversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    VBox card = new VBox(4);
                    card.setPadding(new Insets(8, 10, 8, 10));

                    HBox top = new HBox(8);
                    top.setAlignment(Pos.CENTER_LEFT);

                    Label subj = new Label(item.subject());
                    subj.setStyle("-fx-font-weight: 700; -fx-font-size: 12.5px;");
                    subj.setWrapText(true);
                    subj.setMaxWidth(220);
                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    boolean isOpen = "OPEN".equalsIgnoreCase(item.state());
                    Label st = new Label(isOpen ? "OPEN" : "RESOLVED");
                    st.setStyle(isOpen
                            ? "-fx-background-color: -fx-teal-soft; -fx-text-fill: -fx-teal-dark; -fx-font-size: 9.5px; -fx-font-weight: 800; -fx-background-radius: 999px; -fx-padding: 2px 7px;"
                            : "-fx-background-color: -fx-surface-alt; -fx-text-fill: -fx-ink-500; -fx-font-size: 9.5px; -fx-font-weight: 800; -fx-background-radius: 999px; -fx-padding: 2px 7px;");

                    top.getChildren().addAll(subj, sp, st);

                    Label time = new Label(item.updatedAt() != null ? item.updatedAt().format(TIME_FORMAT) : "");
                    time.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");

                    card.getChildren().addAll(top, time);
                    setGraphic(card);
                    setText(null);
                }
            }
        });

        convoList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            selected = b;
            updateCreateFormState();
            loadThread();
        });

        VBox newBox = new VBox(8);
        newBox.setStyle("-fx-border-color: #E2E8F0 transparent transparent transparent; -fx-border-width: 1px; -fx-padding: 12px 0 0 0;");
        Label newLbl = new Label("Start New Inquiry");
        newLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-opacity: 0.8;");
        newLbl.setLabelFor(subjectField);

        subjectField.setPromptText("Brief subject (3-160 chars)...");
        subjectField.getStyleClass().add("modern-input");
        subjectField.setAccessibleText("New inquiry subject, 3 to 160 characters");

        newTicketArea.setPromptText("Describe your issue (1-2000 chars)...");
        newTicketArea.getStyleClass().add("modern-input");
        newTicketArea.setPrefRowCount(3);
        newTicketArea.setWrapText(true);
        newTicketArea.setAccessibleText("New inquiry message, 1 to 2000 characters");

        createBtn.getStyleClass().add("secondary-button");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> createConversation());

        clearSelectionBtn.getStyleClass().add("secondary-button");
        clearSelectionBtn.setMaxWidth(Double.MAX_VALUE);
        clearSelectionBtn.setTooltip(new Tooltip("Deselect the current thread to start a new inquiry"));
        clearSelectionBtn.setOnAction(e -> convoList.getSelectionModel().clearSelection());

        newBox.getChildren().addAll(newLbl, subjectField, newTicketArea, createBtn, clearSelectionBtn);
        left.getChildren().addAll(leftTitle, convoList, newBox);
        updateCreateFormState();

        // Right Panel (Conversation Thread & Reply Composer)
        VBox right = new VBox(14);
        right.getStyleClass().add("bento-card");
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setMinWidth(0);
        right.setPadding(new Insets(20));

        // Thread Header
        HBox threadHeader = new HBox(12);
        threadHeader.setAlignment(Pos.CENTER_LEFT);
        VBox headText = new VBox(2);
        threadTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        threadSub.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");
        threadSub.setWrapText(true);
        threadSub.setMaxWidth(460);
        headText.getChildren().addAll(threadTitle, threadSub);

        Region rightSp = new Region();
        HBox.setHgrow(rightSp, Priority.ALWAYS);

        threadBadge.setStyle("-fx-background-color: -fx-teal-soft; -fx-text-fill: -fx-teal-dark; -fx-font-size: 10px; -fx-font-weight: 800; -fx-background-radius: 999px; -fx-padding: 4px 10px;");
        threadHeader.getChildren().addAll(headText, rightSp, threadBadge);

        // Messages Scroll Area
        threadScroll.setFitToWidth(true);
        threadScroll.setPrefHeight(380);
        threadScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        messageContainer.setPadding(new Insets(8));

        // Reply Composer
        VBox composerBox = new VBox(8);
        composerBox.setStyle("-fx-border-color: #E2E8F0 transparent transparent transparent; -fx-border-width: 1px; -fx-padding: 12px 0 0 0;");

        Label replyLbl = new Label("Reply");
        replyLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-opacity: 0.8;");
        replyLbl.setLabelFor(composer);

        composer.setPromptText("Write your reply (1-2000 chars)...");
        composer.getStyleClass().add("modern-input");
        composer.setPrefRowCount(3);
        composer.setWrapText(true);
        composer.setAccessibleText("Reply to the selected inquiry, 1 to 2000 characters");

        HBox sendRow = new HBox(10);
        sendRow.setAlignment(Pos.CENTER_RIGHT);

        Button sendBtn = new Button("Send Reply →");
        sendBtn.getStyleClass().add("primary-button");
        sendBtn.setStyle("-fx-background-color: #0A3D36; -fx-text-fill: #FFFFFF; -fx-font-weight: 700; -fx-padding: 8px 18px; -fx-background-radius: 10px;");
        sendBtn.setOnAction(e -> sendReply());

        sendRow.getChildren().add(sendBtn);
        composerBox.getChildren().addAll(replyLbl, composer, sendRow);

        right.getChildren().addAll(threadHeader, threadScroll, composerBox);

        split.getChildren().addAll(left, right);
        return split;
    }

    private void loadConversations() {
        AppExecutor.asyncThenFx(
                () -> repo.supportConversations(user),
                rows -> {
                    conversations.setAll(rows);
                    if (!rows.isEmpty() && selected == null) {
                        convoList.getSelectionModel().select(0);
                    } else if (rows.isEmpty()) {
                        messageContainer.getChildren().clear();
                        Label empty = new Label("No inquiries found. Create one using the form on the left.");
                        empty.setWrapText(true);
                        empty.setMaxWidth(460);
                        empty.setStyle("-fx-opacity: 0.6; -fx-font-size: 13px; -fx-padding: 20px;");
                        messageContainer.getChildren().add(empty);
                    }
                },
                err -> showLoadError("Couldn't load inquiries."));
    }

    /** Distinct error state with Retry — never a silent empty list (P-112). */
    private void showLoadError(String message) {
        messageContainer.getChildren().clear();
        Label error = new Label(message + " Check your connection and try again.");
        error.setWrapText(true);
        error.setMaxWidth(460);
        error.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 13px; -fx-font-weight: 700; -fx-padding: 20px 20px 8px 20px;");
        Button retry = new Button("Retry");
        retry.getStyleClass().add("secondary-button");
        retry.setOnAction(e -> {
            messageContainer.getChildren().clear();
            loadConversations();
            if (selected != null) loadThread();
        });
        messageContainer.getChildren().addAll(error, retry);
    }

    /** The create form is only for new tickets: disabled while a thread is selected (P-111). */
    private void updateCreateFormState() {
        boolean threadSelected = selected != null;
        subjectField.setDisable(threadSelected);
        newTicketArea.setDisable(threadSelected);
        createBtn.setDisable(threadSelected);
        clearSelectionBtn.setVisible(threadSelected);
        clearSelectionBtn.setManaged(threadSelected);
    }

    private void loadThread() {
        if (selected == null) return;
        threadTitle.setText(selected.subject());
        threadSub.setText("Ticket ID: #" + selected.id().substring(0, Math.min(8, selected.id().length())) + " · Created for " + user.displayName());
        boolean isOpen = "OPEN".equalsIgnoreCase(selected.state());
        threadBadge.setText(isOpen ? "OPEN" : "RESOLVED");
        threadBadge.setStyle(isOpen
                ? "-fx-background-color: -fx-teal-soft; -fx-text-fill: -fx-teal-dark; -fx-font-size: 10px; -fx-font-weight: 800; -fx-background-radius: 999px; -fx-padding: 4px 10px;"
                : "-fx-background-color: -fx-surface-alt; -fx-text-fill: -fx-ink-500; -fx-font-size: 10px; -fx-font-weight: 800; -fx-background-radius: 999px; -fx-padding: 4px 10px;");

        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        return repo.supportMessages(user, selected.id());
                    } catch (Exception e) {
                        return List.<SupportMessage>of();
                    }
                },
                rows -> {
                    messageContainer.getChildren().clear();
                    if (rows.isEmpty()) {
                        Label empty = new Label("No messages yet in this inquiry. Write a reply below.");
                        empty.setStyle("-fx-opacity: 0.6; -fx-font-size: 12px; -fx-padding: 10px;");
                        messageContainer.getChildren().add(empty);
                    } else {
                        for (SupportMessage m : rows) {
                            messageContainer.getChildren().add(createMessageBubble(m));
                        }
                    }
                    Platform.runLater(() -> threadScroll.setVvalue(1.0));
                },
                err -> showLoadError("Couldn't load messages for this inquiry."));
    }

    private Node createMessageBubble(SupportMessage m) {
        // Ownership is sender-id equality ONLY — display names can collide and
        // any "USER"-role sender (including other students) must not render as "You".
        boolean isMe = m.senderId() != null && m.senderId().equals(user.id());

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(520);
        bubble.setPadding(new Insets(10, 14, 10, 14));

        if (isMe) {
            bubble.setStyle("-fx-background-color: #EDF7F3; -fx-background-radius: 14px 14px 2px 14px; -fx-border-color: #D2E8DE; -fx-border-radius: 14px 14px 2px 14px; -fx-border-width: 1px;");
        } else {
            bubble.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 14px 14px 14px 2px; -fx-border-color: #E2E8F0; -fx-border-radius: 14px 14px 14px 2px; -fx-border-width: 1px;");
        }

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label sender = new Label(isMe ? "You" : m.senderName() + " (Cycle Office)");
        sender.setStyle(isMe
                ? "-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0A3D36;"
                : "-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #1E293B;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label time = new Label(m.createdAt() != null ? m.createdAt().format(TIME_FORMAT) : "");
        time.setStyle("-fx-font-size: 9.5px; -fx-opacity: 0.6;");

        top.getChildren().addAll(sender, sp, time);

        Label body = new Label(m.body());
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #0F172A; -fx-line-spacing: 2px;");

        bubble.getChildren().addAll(top, body);
        row.getChildren().add(bubble);
        return row;
    }

    private void createConversation() {
        // New-ticket text comes from its OWN composer — never the reply box.
        String subject = subjectField.getText() == null ? "" : subjectField.getText().trim();
        String first = newTicketArea.getText() == null ? "" : newTicketArea.getText().trim();
        if (subject.length() < 3 || subject.length() > 160) {
            alert("Subject must be 3-160 characters.");
            return;
        }
        if (first.length() < 1 || first.length() > 2000) {
            alert("Initial message must be 1-2000 characters. Type it in the new-inquiry box on the left.");
            return;
        }
        AppExecutor.asyncThenFx(
                () -> repo.createSupportConversation(user, subject, first),
                id -> {
                    subjectField.clear();
                    newTicketArea.clear();
                    loadConversations();
                },
                err -> alert("Could not create request. Please retry."));
    }

    private void sendReply() {
        if (selected == null) {
            alert("Select an inquiry from the left first.");
            return;
        }
        String body = composer.getText() == null ? "" : composer.getText().trim();
        if (body.length() < 1 || body.length() > 2000) {
            alert("Reply message must be 1-2000 characters.");
            return;
        }
        AppExecutor.asyncThenFx(
                () -> {
                    repo.postSupportMessage(user, selected.id(), body);
                    return true;
                },
                ok -> {
                    composer.clear();
                    loadThread();
                },
                err -> alert("Could not send reply. Please retry."));
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.showAndWait();
    }
}
