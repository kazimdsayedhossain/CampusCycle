package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.SupportConversation;
import bd.ac.kuet.campuscycle.domain.SupportMessage;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Student/admin support inbox: request creation, conversation list, thread, reply. */
public class SupportView extends VBox {
    private final CampusUser user;
    private final CampusRepository repo;
    private final ObservableList<SupportConversation> conversations = FXCollections.observableArrayList();
    private final ObservableList<String> threadLines = FXCollections.observableArrayList();
    private final ListView<SupportConversation> convoList = new ListView<>(conversations);
    private final ListView<String> threadList = new ListView<>(threadLines);
    private final TextField subjectField = new TextField();
    private final TextArea composer = new TextArea();
    private SupportConversation selected;

    public SupportView(CampusUser user, CampusRepository repo) {
        this.user = user;
        this.repo = repo;
        setSpacing(16);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        VBox titleCol = new VBox(2);
        Label title = new Label("Help and Support");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");
        Label sub = new Label("Ask the Cycle Office; replies stay in this thread.");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        HBox main = new HBox(16);
        VBox left = new VBox(8);
        left.setPrefWidth(340);
        Label leftLbl = new Label("CONVERSATIONS");
        leftLbl.getStyleClass().add("metric-label");
        convoList.setPrefHeight(420);
        convoList.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SupportConversation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.subject() + " [" + item.state() + "]");
            }
        });
        convoList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            selected = b;
            loadThread();
        });
        subjectField.setPromptText("New request subject (3-160 chars)");
        subjectField.getStyleClass().add("modern-input");
        Button newBtn = new Button("New Request");
        newBtn.getStyleClass().add("secondary-button");
        newBtn.setOnAction(e -> createConversation());
        left.getChildren().addAll(leftLbl, convoList, subjectField, newBtn);

        VBox right = new VBox(8);
        HBox.setHgrow(right, Priority.ALWAYS);
        Label rightLbl = new Label("MESSAGE THREAD");
        rightLbl.getStyleClass().add("metric-label");
        threadList.setPrefHeight(360);
        composer.setPromptText("Write a reply (1-2000 chars)");
        composer.setPrefRowCount(3);
        Button sendBtn = new Button("Send Reply");
        sendBtn.getStyleClass().add("primary-button");
        sendBtn.setOnAction(e -> sendReply());
        right.getChildren().addAll(rightLbl, threadList, composer, sendBtn);

        main.getChildren().addAll(left, right);
        Region spacer = new Region();
        getChildren().addAll(titleCol, main, spacer);
        ThemeManager.applyFadeIn(this);
        loadConversations();
    }

    private void loadConversations() {
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        return repo.supportConversations(user);
                    } catch (Exception e) {
                        return List.<SupportConversation>of();
                    }
                },
                rows -> {
                    conversations.setAll(rows);
                    if (!rows.isEmpty() && selected == null) {
                        convoList.getSelectionModel().select(0);
                    }
                },
                err -> {});
    }

    private void loadThread() {
        if (selected == null) return;
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        return repo.supportMessages(user, selected.id());
                    } catch (Exception e) {
                        return List.<SupportMessage>of();
                    }
                },
                rows -> {
                    threadLines.clear();
                    for (SupportMessage m : rows) {
                        threadLines.add(m.senderName() + ": " + m.body());
                    }
                    if (rows.isEmpty()) threadLines.add("No messages yet.");
                },
                err -> {});
    }

    private void createConversation() {
        String subject = subjectField.getText() == null ? "" : subjectField.getText().trim();
        String first = composer.getText() == null ? "" : composer.getText().trim();
        if (subject.length() < 3) {
            alert("Subject must be 3-160 characters.");
            return;
        }
        if (first.isEmpty()) {
            alert("Write the first message in the composer below.");
            return;
        }
        AppExecutor.asyncThenFx(
                () -> repo.createSupportConversation(user, subject, first),
                id -> {
                    subjectField.clear();
                    composer.clear();
                    loadConversations();
                },
                err -> alert("Could not create request. Please retry."));
    }

    private void sendReply() {
        if (selected == null) {
            alert("Select a conversation first.");
            return;
        }
        String body = composer.getText() == null ? "" : composer.getText().trim();
        if (body.isEmpty()) {
            alert("Reply cannot be empty.");
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
