package mesh.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import mesh.core.MeshNode;
import mesh.core.NodeInfo;
import mesh.utils.NetworkUtils;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class MainController implements Initializable, MeshNode.UIUpdater {

    @FXML private TextArea newMessagesArea;
    @FXML private TextArea connectionInfoArea;
    @FXML private TextArea nodesArea;
    @FXML private TextField messageField;
    @FXML private TextField targetField;
    @FXML private CheckBox broadcastCheckBox;
    @FXML private Button sendButton;
    @FXML private Button stopButton;

    // Элементы кастомного заголовка
    @FXML private HBox titleBar;
    @FXML private Button minimizeBtn;
    @FXML private Button closeBtn;

    private MeshNode meshNode;
    private Thread meshThread;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Для перетаскивания окна
    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupEventHandlers();
        setupWindowControls();
        setupWindowDragging();
        updateConnectionInfo();
        startMeshNode();
    }

    private void setupEventHandlers() {
        messageField.setOnAction(event -> handleSendMessage());

        broadcastCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                targetField.setDisable(true);
                targetField.setPromptText("Широковещательная рассылка");
            } else {
                targetField.setDisable(false);
                targetField.setPromptText("Кому (ID узла)");
            }
        });

        sendButton.setOnAction(event -> handleSendMessage());
        stopButton.setOnAction(event -> handleStopAndExit());
    }

    // Настройка кнопок управления окном (только свернуть и закрыть)
    private void setupWindowControls() {
        // Кнопка свернуть
        minimizeBtn.setOnAction(e -> {
            Stage stage = (Stage) minimizeBtn.getScene().getWindow();
            stage.setIconified(true);
        });

        // Кнопка закрыть
        closeBtn.setOnAction(e -> {
            shutdown();
            Stage stage = (Stage) closeBtn.getScene().getWindow();
            stage.close();
            Platform.exit();
            System.exit(0);
        });
    }

    // Настройка перетаскивания окна
    private void setupWindowDragging() {
        titleBar.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
    }

    private void updateConnectionInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Статус: запуск...\n");
        info.append("Локальный IP: ").append(NetworkUtils.getLocalIpAddress()).append("\n");
        connectionInfoArea.setText(info.toString());
    }

    private void startMeshNode() {
        meshNode = new MeshNode(this);
        meshThread = new Thread(() -> {
            try {
                meshNode.start();
            } catch (Exception e) {
                logError("Ошибка запуска: " + e.getMessage());
                e.printStackTrace();
            }
        });
        meshThread.setDaemon(true);
        meshThread.start();
    }

    @FXML
    private void handleSendMessage() {
        if (meshNode == null) return;

        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            showAlert("Ошибка", "Введите текст сообщения");
            return;
        }

        if (broadcastCheckBox.isSelected()) {
            meshNode.broadcastMessage(text);
            logMessage("Я -> ВСЕМ", text);
        } else {
            String targetId = targetField.getText().trim();
            if (targetId.isEmpty()) {
                showAlert("Ошибка", "Введите ID получателя или выберите 'Всем'");
                return;
            }

            meshNode.sendMessage(targetId, text);
            logMessage("Я -> " + targetId, text);
        }

        messageField.clear();
        if (!broadcastCheckBox.isSelected()) {
            targetField.clear();
        }
    }

    @FXML
    private void handleStopAndExit() {
        shutdown();
        Platform.exit();
        System.exit(0);
    }

    public void shutdown() {
        if (meshNode != null) {
            meshNode.stop();
        }
    }

    @Override
    public void onNodeStarted(String nodeId) {
        Platform.runLater(() -> {
            String info = String.format(
                    "Статус: АКТИВЕН\n" +
                            "ID узла: %s\n" +
                            "Локальный IP: %s\n",
                    nodeId, NetworkUtils.getLocalIpAddress()
            );
            connectionInfoArea.setText(info);
            logMessage("СИСТЕМА", "Узел запущен. ID: " + nodeId);
        });
    }

    @Override
    public void onNewNeighbor(NodeInfo neighbor) {
        Platform.runLater(() -> {
            logMessage("СИСТЕМА", String.format("Обнаружен новый узел: %s (%s)",
                    neighbor.getNodeId(), neighbor.getAddress().getHostAddress()));
            updateNodesList();
        });
    }

    @Override
    public void onNeighborLost(String nodeId) {
        Platform.runLater(() -> {
            logMessage("СИСТЕМА", "Узел отключился: " + nodeId);
            updateNodesList();
        });
    }

    @Override
    public void onMessageReceived(String senderId, String senderName, String text) {
        Platform.runLater(() -> {
            logMessage(senderId, text);
        });
    }

    @Override
    public void onMessageDelivered(String targetId, boolean success) {
        Platform.runLater(() -> {
            if (success) {
                logMessage("СИСТЕМА", "✓ Сообщение доставлено узлу " + targetId);
            } else {
                logMessage("СИСТЕМА", "✗ Не удалось доставить сообщение узлу " + targetId);
            }
        });
    }

    private void updateNodesList() {
        if (meshNode == null) return;

        StringBuilder nodes = new StringBuilder();
        nodes.append("Текущий узел: ").append(meshNode.getNodeId()).append("\n");

        if (meshNode.getNeighborCount() == 0) {
            nodes.append("Нет активных соседей\n");
            nodes.append("Ожидание подключений...\n");
        } else {
            int index = 1;
            for (NodeInfo node : meshNode.getNeighbors()) {
                nodes.append(String.format("%d. %s\n   (%s)\n",
                        index++,
                        node.getNodeId(),
                        node.getAddress().getHostAddress()));
            }
        }

        nodesArea.setText(nodes.toString());
    }

    private void logMessage(String sender, String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        newMessagesArea.appendText(String.format("[%s] %s: %s\n", timestamp, sender, message));
        newMessagesArea.setScrollTop(Double.MAX_VALUE);
    }

    private void logError(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        newMessagesArea.appendText(String.format("[%s] ОШИБКА: %s\n", timestamp, message));
        newMessagesArea.setScrollTop(Double.MAX_VALUE);
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}