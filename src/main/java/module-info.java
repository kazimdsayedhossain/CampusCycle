module bd.ac.kuet.campuscycle {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires jdk.jsobject;
    requires java.net.http;
    requires com.google.gson;
    requires java.sql;

    exports bd.ac.kuet.campuscycle;
    exports bd.ac.kuet.campuscycle.domain;
    exports bd.ac.kuet.campuscycle.data;
    exports bd.ac.kuet.campuscycle.ui;
    opens bd.ac.kuet.campuscycle.ui to javafx.fxml;
}
