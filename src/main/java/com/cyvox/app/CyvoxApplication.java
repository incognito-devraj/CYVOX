package com.cyvox.app;

import com.cyvox.config.AppConfig;
import com.cyvox.controller.DashboardController;
import com.cyvox.util.ResourceResolver;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class CyvoxApplication extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(CyvoxApplication.class);

    @Override
    public void init() {
        AppConfig.ensureWorkspaceDirectories();
        LOGGER.info("Starting {}", AppConfig.applicationName());
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(ResourceResolver.requireResource("fxml/Main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(
                ResourceResolver.requireResource("css/theme.css").toExternalForm(),
                ResourceResolver.requireResource("css/window.css").toExternalForm(),
                ResourceResolver.requireResource("css/buttons.css").toExternalForm(),
                ResourceResolver.requireResource("css/textfield.css").toExternalForm(),
                ResourceResolver.requireResource("css/dropzone.css").toExternalForm(),
                ResourceResolver.requireResource("css/animations.css").toExternalForm()
        );
        DashboardController dashboardController = loader.getController();

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(AppConfig.applicationName());
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        dashboardController.configureWindow(stage);
        stage.setOnCloseRequest(event -> dashboardController.shutdown());
        stage.show();

        LOGGER.info("Primary stage displayed");
    }
}
