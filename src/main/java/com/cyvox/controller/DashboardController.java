package com.cyvox.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardController {

    private static final Map<String, String> PRESET_DETAILS = new LinkedHashMap<>();

    static {
        PRESET_DETAILS.put("High Quality", "H.265, CRF 23, AAC 192 kbps for near-transparent visual quality.");
        PRESET_DETAILS.put("Balanced", "H.265, CRF 28, AAC 128 kbps for practical storage savings.");
        PRESET_DETAILS.put("Maximum Compression", "H.265, CRF 33, AAC 96 kbps for the smallest compatible outputs.");
        PRESET_DETAILS.put("Archive", "SVT-AV1 tuned for long-term storage and aggressive space reduction.");
    }

    @FXML
    private Label inputFolderValue;

    @FXML
    private Label outputFolderValue;

    @FXML
    private Label statusValue;

    @FXML
    private ComboBox<String> presetComboBox;

    @FXML
    private Label presetDetailLabel;

    @FXML
    private Label presetSummaryLabel;

    @FXML
    private ListView<String> queueListView;

    @FXML
    private TextArea logTextArea;

    @FXML
    private TextField filenamePatternField;

    @FXML
    private Spinner<Integer> threadSpinner;

    @FXML
    private CheckBox skipExistingCheckBox;

    @FXML
    private CheckBox overwriteExistingCheckBox;

    @FXML
    private CheckBox keepMetadataCheckBox;

    @FXML
    private CheckBox deleteOriginalsCheckBox;

    @FXML
    private Label optionSummaryLabel;

    @FXML
    private Label currentFileValue;

    @FXML
    private Label outputPatternPreviewLabel;

    @FXML
    private Label outputReadinessLabel;

    @FXML
    private Label workspaceStatusValue;

    @FXML
    private Label footerPresetValue;

    @FXML
    private Label footerThreadValue;

    @FXML
    private Label queueStateLabel;

    @FXML
    private void initialize() {
        presetComboBox.getItems().setAll(PRESET_DETAILS.keySet());
        presetComboBox.getSelectionModel().select("Balanced");
        presetComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updatePresetDetails(newValue));
        threadSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 32, 4));
        threadSpinner.valueProperty().addListener((observable, oldValue, newValue) -> updateFooterThreadValue(newValue));
        queueListView.getItems().setAll(
                "Select an input folder to prepare recursive scanning.",
                "Choose an output folder for compressed files and reports.",
                "Pick a preset and review hardware / overwrite settings.",
                "Run Scan to populate the compression queue in Milestone 3."
        );
        logTextArea.setText("""
                [startup] CYVOX dashboard initialized.
                [startup] Workspace directories verified.
                [ui] Waiting for input and output folder selection.
                """);
        updatePresetDetails(presetComboBox.getValue());
        updateOutputPatternPreview();
        refreshOptionSummary();
        updateOutputReadiness();
        statusValue.setText("Ready for workspace setup");
        workspaceStatusValue.setText("Dashboard online");
        queueStateLabel.setText("Onboarding checklist loaded");
        currentFileValue.setText("No active file");
    }

    @FXML
    private void chooseInputFolder() {
        Path selectedPath = chooseDirectory("Select Input Folder");
        if (selectedPath != null) {
            inputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Input folder selected");
            workspaceStatusValue.setText("Input configured");
            appendLog("input", "Input folder set to " + selectedPath);
        }
    }

    @FXML
    private void chooseOutputFolder() {
        Path selectedPath = chooseDirectory("Select Output Folder");
        if (selectedPath != null) {
            outputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Output folder selected");
            workspaceStatusValue.setText("Output configured");
            appendLog("output", "Output folder set to " + selectedPath);
            updateOutputReadiness();
        }
    }

    @FXML
    private void handleScanRequested() {
        statusValue.setText("Scanner milestone is next");
        queueStateLabel.setText("Scan requested from UI shell");
        appendLog("scan", "Scan button pressed. Recursive scanner will be wired in Milestone 3.");
    }

    @FXML
    private void handleCompressionRequested() {
        appendLog("compress", "Compression is not available until the engine milestones are complete.");
    }

    @FXML
    private void handlePauseRequested() {
        appendLog("pause", "Pause control is reserved for Milestone 7.");
    }

    @FXML
    private void handleResumeRequested() {
        appendLog("resume", "Resume control is reserved for Milestone 7.");
    }

    @FXML
    private void handleCancelRequested() {
        appendLog("cancel", "Cancel control is reserved for Milestone 7.");
    }

    @FXML
    private void handleFilenamePatternChange() {
        updateOutputPatternPreview();
        refreshOptionSummary();
    }

    @FXML
    private void refreshOptionSummary() {
        String filenamePattern = filenamePatternField.getText().isBlank() ? "{name}_cyvox" : filenamePatternField.getText().trim();
        optionSummaryLabel.setText(
                "Pattern " + filenamePattern
                        + " | skip existing: " + yesNo(skipExistingCheckBox.isSelected())
                        + " | overwrite: " + yesNo(overwriteExistingCheckBox.isSelected())
                        + " | keep metadata: " + yesNo(keepMetadataCheckBox.isSelected())
                        + " | delete originals: " + yesNo(deleteOriginalsCheckBox.isSelected())
        );
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Window owner = statusValue.getScene() == null ? null : statusValue.getScene().getWindow();
        File selection = chooser.showDialog(owner);
        return selection == null ? null : selection.toPath();
    }

    private void updatePresetDetails(String presetName) {
        String details = PRESET_DETAILS.getOrDefault(presetName, "");
        presetDetailLabel.setText(details);
        presetSummaryLabel.setText(presetName + " preset selected");
        footerPresetValue.setText(presetName);
        appendLog("preset", "Preset changed to " + presetName + ".");
    }

    private void updateOutputPatternPreview() {
        String pattern = filenamePatternField.getText().isBlank() ? "{name}_cyvox" : filenamePatternField.getText().trim();
        outputPatternPreviewLabel.setText(pattern + ".ext");
    }

    private void updateOutputReadiness() {
        boolean outputReady = !outputFolderValue.getText().equals("No folder selected");
        outputReadinessLabel.setText(outputReady
                ? "Output folder is configured and ready for compressed files."
                : "Choose an output folder to enable report and file destinations.");
    }

    private void updateFooterThreadValue(Integer value) {
        footerThreadValue.setText(value == null ? "Auto" : value.toString());
    }

    private void appendLog(String scope, String message) {
        if (logTextArea.getText().isBlank()) {
            logTextArea.setText("[" + scope + "] " + message);
            return;
        }
        logTextArea.appendText(System.lineSeparator() + "[" + scope + "] " + message);
    }

    private String yesNo(boolean selected) {
        return selected ? "yes" : "no";
    }
}
