package com.cyvox.controller;

import com.cyvox.model.ScanResult;
import com.cyvox.model.VideoFile;
import com.cyvox.service.VideoScannerService;
import com.cyvox.task.VideoScanTask;
import com.cyvox.util.FileSizeFormatter;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardController {

    private static final Map<String, String> PRESET_DETAILS = new LinkedHashMap<>();

    static {
        PRESET_DETAILS.put("High Quality", "H.265, CRF 23, AAC 192 kbps for near-transparent visual quality.");
        PRESET_DETAILS.put("Balanced", "H.265, CRF 28, AAC 128 kbps for practical storage savings.");
        PRESET_DETAILS.put("Maximum Compression", "H.265, CRF 33, AAC 96 kbps for the smallest compatible outputs.");
        PRESET_DETAILS.put("Archive", "SVT-AV1 tuned for long-term storage and aggressive space reduction.");
    }

    private final VideoScannerService videoScannerService = new VideoScannerService();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread worker = new Thread(runnable, "cyvox-scan-worker");
        worker.setDaemon(true);
        return worker;
    });

    private Path selectedInputFolder;
    private VideoScanTask activeScanTask;

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
    private Label videoCountValue;

    @FXML
    private Label originalSizeValue;

    @FXML
    private Label estimatedSavingsValue;

    @FXML
    private Label compressionSpeedValue;

    @FXML
    private Label reportTargetsLabel;

    @FXML
    private Label elapsedTimeValue;

    @FXML
    private Label remainingTimeValue;

    @FXML
    private Label nvidiaStatusLabel;

    @FXML
    private Label intelStatusLabel;

    @FXML
    private Label amdStatusLabel;

    @FXML
    private Button scanButton;

    @FXML
    private Button compressButton;

    @FXML
    private ProgressBar compressionProgressBar;

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
        updateScanControls(false);
        statusValue.setText("Ready for workspace setup");
        workspaceStatusValue.setText("Dashboard online");
        queueStateLabel.setText("Onboarding checklist loaded");
        currentFileValue.setText("No active file");
        estimatedSavingsValue.setText("Pending");
        compressionSpeedValue.setText("Scanner idle");
        reportTargetsLabel.setText("HTML, CSV, and JSON reports unlock after compression and reporting milestones.");
        nvidiaStatusLabel.setText("CPU fallback");
        intelStatusLabel.setText("CPU fallback");
        amdStatusLabel.setText("CPU fallback");
    }

    @FXML
    private void chooseInputFolder() {
        Path selectedPath = chooseDirectory("Select Input Folder");
        if (selectedPath != null) {
            selectedInputFolder = selectedPath;
            inputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Input folder selected");
            workspaceStatusValue.setText("Input configured");
            appendLog("input", "Input folder set to " + selectedPath);
            updateScanControls(true);
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
        if (selectedInputFolder == null) {
            statusValue.setText("Select an input folder first");
            appendLog("scan", "Scan request ignored because no input folder is selected.");
            return;
        }
        if (activeScanTask != null && activeScanTask.isRunning()) {
            appendLog("scan", "A recursive scan is already in progress.");
            return;
        }

        activeScanTask = new VideoScanTask(videoScannerService, selectedInputFolder);
        compressionProgressBar.progressProperty().unbind();
        compressionProgressBar.progressProperty().bind(activeScanTask.progressProperty());
        statusValue.textProperty().unbind();
        statusValue.textProperty().bind(activeScanTask.messageProperty());
        scanButton.setDisable(true);
        queueStateLabel.setText("Scanning " + selectedInputFolder.getFileName());
        currentFileValue.textProperty().unbind();
        currentFileValue.textProperty().bind(activeScanTask.messageProperty());
        appendLog("scan", "Starting recursive scan for " + selectedInputFolder + ".");

        activeScanTask.setOnSucceeded(event -> handleScanSucceeded(activeScanTask.getValue()));
        activeScanTask.setOnFailed(event -> handleScanFailed(activeScanTask.getException()));
        activeScanTask.setOnCancelled(event -> handleScanCancelled());
        scanExecutor.submit(activeScanTask);
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
        if (presetName == null) {
            return;
        }
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

    public void shutdown() {
        if (activeScanTask != null && activeScanTask.isRunning()) {
            activeScanTask.cancel();
        }
        scanExecutor.shutdownNow();
    }

    private void handleScanSucceeded(ScanResult scanResult) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();

        videoCountValue.setText(Long.toString(scanResult.statistics().videoCount()));
        originalSizeValue.setText(FileSizeFormatter.format(scanResult.statistics().totalSizeBytes()));
        queueStateLabel.setText(scanResult.statistics().videoCount() + " videos ready");
        workspaceStatusValue.setText("Scan complete");
        statusValue.setText("Scan complete");
        currentFileValue.setText("No active file");
        elapsedTimeValue.setText("00:00:00");
        remainingTimeValue.setText("--:--:--");
        compressionSpeedValue.setText("Scan complete");

        queueListView.getItems().setAll(scanResult.videos().stream()
                .map(this::describeVideo)
                .toList());
        if (scanResult.videos().isEmpty()) {
            queueListView.getItems().setAll("No supported video files were found in the selected folder.");
        }

        updateScanControls(selectedInputFolder != null);
        compressButton.setDisable(true);
        appendLog("scan", "Scan complete: found " + scanResult.statistics().videoCount()
                + " supported videos totaling " + FileSizeFormatter.format(scanResult.statistics().totalSizeBytes()) + ".");
    }

    private void handleScanFailed(Throwable throwable) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        compressionProgressBar.setProgress(0);
        currentFileValue.setText("No active file");
        queueStateLabel.setText("Scan failed");
        workspaceStatusValue.setText("Scan failed");
        statusValue.setText("Scan failed");
        updateScanControls(selectedInputFolder != null);
        compressButton.setDisable(true);
        appendLog("scan", "Scan failed: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
    }

    private void handleScanCancelled() {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        compressionProgressBar.setProgress(0);
        currentFileValue.setText("No active file");
        queueStateLabel.setText("Scan cancelled");
        workspaceStatusValue.setText("Scan cancelled");
        statusValue.setText("Scan cancelled");
        updateScanControls(selectedInputFolder != null);
        compressButton.setDisable(true);
        appendLog("scan", "Scan cancelled.");
    }

    private void updateScanControls(boolean scanReady) {
        scanButton.setDisable(!scanReady);
    }

    private String describeVideo(VideoFile videoFile) {
        return videoFile.fileName() + "  |  " + videoFile.extension().toUpperCase()
                + "  |  " + FileSizeFormatter.format(videoFile.sizeBytes());
    }

    private String yesNo(boolean selected) {
        return selected ? "yes" : "no";
    }
}
