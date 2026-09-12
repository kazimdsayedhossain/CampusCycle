module com.example.campuscycle {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;


    opens com.example.campuscycle to javafx.fxml;
    exports com.example.campuscycle;
}