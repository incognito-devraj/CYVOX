package com.cyvox.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class DashboardController {

    private static final List<String> PRESETS = List.of(
            "High Quality",
            "Balanced",
            "Maximum Compression",
            "Archive"
    );

    @FXML
    private Label inputFolderValue;

    @FXML
    private Label outputFolderValue;

    @FXML
    private Label statusValue;

    @FXML
    private ComboBox<String> presetComboBox;

    @FXML
    private void initialize() {
        presetComboBox.getItems().setAll(PRESETS);
        presetComboBox.getSelectionModel().select("Balanced");
        statusValue.setText("Ready for workspace setup");
    }

    @FXML
    private void chooseInputFolder() {
        Path selectedPath = chooseDirectory("Select Input Folder");
        if (selectedPath != null) {
            inputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Input folder selected");
        }
    }

    @FXML
    private void chooseOutputFolder() {
        Path selectedPath = chooseDirectory("Select Output Folder");
        if (selectedPath != null) {
            outputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Output folder selected");
        }
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Window owner = statusValue.getScene() == null ? null : statusValue.getScene().getWindow();
        File selection = chooser.showDialog(owner);
        return selection == null ? null : selection.toPath();
    }
}
