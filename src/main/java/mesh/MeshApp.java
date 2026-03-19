package mesh;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import mesh.controllers.MainController;

public class MeshApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        AnchorPane root = loader.load();

        primaryStage.setTitle("Meshenger");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> {
            MainController controller = loader.getController();
            controller.shutdown();
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}