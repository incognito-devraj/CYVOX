package com.cyvox.app;

import com.cyvox.config.AppConfig;
import com.cyvox.controller.DashboardController;
import com.cyvox.util.ResourceResolver;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
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
        FXMLLoader loader = new FXMLLoader(ResourceResolver.requireResource("fxml/dashboard-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        scene.getStylesheets().add(ResourceResolver.requireResource("css/application.css").toExternalForm());
        DashboardController dashboardController = loader.getController();

        stage.setTitle(AppConfig.applicationName());
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> dashboardController.shutdown());
        stage.show();

        LOGGER.info("Primary stage displayed");
    }
}
