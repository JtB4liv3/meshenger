module mesh.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires MaterialFX;

    opens mesh to javafx.fxml;
    opens mesh.controllers to javafx.fxml;

    exports mesh;
    exports mesh.controllers;
    exports mesh.core;
    exports mesh.models;
    exports mesh.services;
    exports mesh.utils;
}