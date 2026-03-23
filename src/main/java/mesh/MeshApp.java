package mesh;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import mesh.controllers.MainController;

public class MeshApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        VBox root = loader.load();

        // Убираем стандартный заголовок окна
        primaryStage.initStyle(StageStyle.UNDECORATED);

        Scene scene = new Scene(root);

        // Подключаем CSS (если еще не подключен в FXML)
        scene.getStylesheets().add(getClass().getResource("/css/messenger.css").toExternalForm());

        primaryStage.setScene(scene);
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