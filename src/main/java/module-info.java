module com.example.meshenger {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.meshenger to javafx.fxml;
    exports com.example.meshenger;
}