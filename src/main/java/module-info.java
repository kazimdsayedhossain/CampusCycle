module com.example.campuscycle {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.campuscycle to javafx.fxml;
    exports com.example.campuscycle;
}