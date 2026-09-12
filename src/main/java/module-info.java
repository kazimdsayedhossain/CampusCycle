module com.example.campuscycle {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;


    opens com.example.campuscycle to javafx.fxml;
    exports com.example.campuscycle;
    exports com.example.campuscycle.model;
    exports com.example.campuscycle.ui;
    exports com.example.campuscycle.database;
}