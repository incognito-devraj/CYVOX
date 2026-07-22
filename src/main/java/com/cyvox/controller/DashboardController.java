package com.cyvox.controller;

import com.cyvox.exception.FfmpegException;
import com.cyvox.exception.FfprobeException;
import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionPreset;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.CompressionStatus;
import com.cyvox.model.ScanResult;
import com.cyvox.model.VideoFile;
import com.cyvox.service.FfmpegResolver;
import com.cyvox.service.FfprobeResolver;
import com.cyvox.service.VideoCompressionService;
import com.cyvox.service.VideoAnalysisService;
import com.cyvox.service.VideoScannerService;
import com.cyvox.service.ReportService;
import com.cyvox.task.VideoAnalysisTask;
import com.cyvox.task.BatchCompressionTask;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);
    private static final Map<String, String> PRESET_DETAILS = new LinkedHashMap<>();

    static {
        PRESET_DETAILS.put("High Quality", "H.265, CRF 23, AAC 192 kbps for near-transparent visual quality.");
        PRESET_DETAILS.put("Balanced", "H.265, CRF 28, AAC 128 kbps for practical storage savings.");
        PRESET_DETAILS.put("Maximum Compression", "H.265, CRF 33, AAC 96 kbps for the smallest compatible outputs.");
        PRESET_DETAILS.put("Archive", "SVT-AV1 tuned for long-term storage and aggressive space reduction.");
    }

    private final VideoScannerService videoScannerService = new VideoScannerService();
    private final VideoAnalysisService videoAnalysisService = new VideoAnalysisService(new FfprobeResolver());
    private final VideoCompressionService videoCompressionService = new VideoCompressionService(new FfmpegResolver());
    private final ReportService reportService = new ReportService();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread worker = new Thread(runnable, "cyvox-scan-worker");
        worker.setDaemon(true);
        return worker;
    });

    private Path selectedInputFolder;
    private Path selectedOutputFolder;
    private VideoScanTask activeScanTask;
    private VideoAnalysisTask activeAnalysisTask;
    private BatchCompressionTask activeBatchCompressionTask;
    private List<VideoFile> analyzedVideos = new ArrayList<>();

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
    private Button pauseButton;

    @FXML
    private Button resumeButton;

    @FXML
    private Button cancelButton;

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
                "Run Scan to populate the compression queue and analyze videos."
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
        updateActionControls();
        statusValue.setText("Ready for workspace setup");
        workspaceStatusValue.setText("Dashboard online");
        queueStateLabel.setText("Onboarding checklist loaded");
        currentFileValue.setText("No active file");
        estimatedSavingsValue.setText("Pending");
        compressionSpeedValue.setText("Scanner idle");
        reportTargetsLabel.setText("Reports unlock later. Metadata analysis will use bundled ffprobe when available.");
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
            updateActionControls();
        }
    }

    @FXML
    private void chooseOutputFolder() {
        Path selectedPath = chooseDirectory("Select Output Folder");
        if (selectedPath != null) {
            selectedOutputFolder = selectedPath;
            outputFolderValue.setText(selectedPath.toString());
            statusValue.setText("Output folder selected");
            workspaceStatusValue.setText("Output configured");
            appendLog("output", "Output folder set to " + selectedPath);
            updateOutputReadiness();
            updateActionControls();
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
        if (activeAnalysisTask != null && activeAnalysisTask.isRunning()) {
            appendLog("scan", "Video analysis is already in progress.");
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
        if (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning()) {
            appendLog("compress", "A batch compression task is already in progress.");
            return;
        }
        if (selectedOutputFolder == null) {
            statusValue.setText("Select an output folder first");
            appendLog("compress", "Compression request ignored because no output folder is selected.");
            return;
        }
        if (analyzedVideos.isEmpty()) {
            statusValue.setText("Scan and analyze videos first");
            appendLog("compress", "Compression request ignored because there are no analyzed videos.");
            return;
        }

        activeBatchCompressionTask = new BatchCompressionTask(
                videoCompressionService,
                analyzedVideos,
                selectedOutputFolder,
                CompressionPreset.fromDisplayName(presetComboBox.getValue()),
                filenamePatternField.getText().trim(),
                overwriteExistingCheckBox.isSelected(),
                skipExistingCheckBox.isSelected(),
                keepMetadataCheckBox.isSelected()
        );

        compressionProgressBar.progressProperty().unbind();
        compressionProgressBar.progressProperty().bind(activeBatchCompressionTask.progressProperty());
        statusValue.textProperty().unbind();
        statusValue.textProperty().bind(activeBatchCompressionTask.messageProperty());
        currentFileValue.textProperty().unbind();
        currentFileValue.textProperty().bind(activeBatchCompressionTask.messageProperty());
        workspaceStatusValue.setText("Compressing batch");
        queueStateLabel.setText("Encoding queue");
        compressionSpeedValue.setText("Batch encoding");
        elapsedTimeValue.setText("In progress");
        remainingTimeValue.setText("Estimating");
        appendLog("compress", "Starting batch compression for " + analyzedVideos.size() + " videos.");
        updateActionControls();

        activeBatchCompressionTask.setOnSucceeded(event -> handleBatchCompressionSucceeded(activeBatchCompressionTask.getValue()));
        activeBatchCompressionTask.setOnFailed(event -> handleBatchCompressionFailed(activeBatchCompressionTask.getException()));
        activeBatchCompressionTask.setOnCancelled(event -> handleBatchCompressionCancelled());
        scanExecutor.submit(activeBatchCompressionTask);
    }

    @FXML
    private void handlePauseRequested() {
        if (activeBatchCompressionTask == null || !activeBatchCompressionTask.isRunning()) {
            appendLog("pause", "No active batch to pause.");
            return;
        }
        activeBatchCompressionTask.pause();
        workspaceStatusValue.setText("Batch paused");
        queueStateLabel.setText("Paused");
        appendLog("pause", "Batch pause requested.");
        updateActionControls();
    }

    @FXML
    private void handleResumeRequested() {
        if (activeBatchCompressionTask == null || !activeBatchCompressionTask.isPaused()) {
            appendLog("resume", "No paused batch to resume.");
            return;
        }
        activeBatchCompressionTask.resume();
        workspaceStatusValue.setText("Compressing batch");
        queueStateLabel.setText("Encoding queue");
        appendLog("resume", "Batch resumed.");
        updateActionControls();
    }

    @FXML
    private void handleCancelRequested() {
        if (activeScanTask != null && activeScanTask.isRunning()) {
            activeScanTask.cancel();
            appendLog("cancel", "Scan cancellation requested.");
            return;
        }
        if (activeAnalysisTask != null && activeAnalysisTask.isRunning()) {
            activeAnalysisTask.cancel();
            appendLog("cancel", "Analysis cancellation requested.");
            return;
        }
        if (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning()) {
            activeBatchCompressionTask.cancel();
            activeBatchCompressionTask.resume();
            appendLog("cancel", "Batch cancellation requested.");
            return;
        }
        appendLog("cancel", "No active work to cancel.");
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
        LOGGER.info("[{}] {}", scope, message);
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
        if (activeAnalysisTask != null && activeAnalysisTask.isRunning()) {
            activeAnalysisTask.cancel();
        }
        if (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning()) {
            activeBatchCompressionTask.cancel();
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
        compressionSpeedValue.setText("Metadata pending");

        queueListView.getItems().setAll(scanResult.videos().stream()
                .map(this::describeVideo)
                .toList());
        if (scanResult.videos().isEmpty()) {
            analyzedVideos = List.of();
            queueListView.getItems().setAll("No supported video files were found in the selected folder.");
            compressionSpeedValue.setText("No videos found");
            estimatedSavingsValue.setText("N/A");
            reportTargetsLabel.setText("Nothing to analyze in the selected folder.");
            activeScanTask = null;
            updateActionControls();
            return;
        }

        updateActionControls();
        appendLog("scan", "Scan complete: found " + scanResult.statistics().videoCount()
                + " supported videos totaling " + FileSizeFormatter.format(scanResult.statistics().totalSizeBytes()) + ".");
        activeScanTask = null;
        startVideoAnalysis(scanResult);
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
        analyzedVideos = List.of();
        updateActionControls();
        appendLog("scan", "Scan failed: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
        activeScanTask = null;
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
        analyzedVideos = List.of();
        updateActionControls();
        appendLog("scan", "Scan cancelled.");
        activeScanTask = null;
    }

    private String describeVideo(VideoFile videoFile) {
        if (videoFile.metadata() == null) {
            return videoFile.fileName() + "  |  " + videoFile.extension().toUpperCase()
                    + "  |  " + FileSizeFormatter.format(videoFile.sizeBytes());
        }
        return videoFile.fileName()
                + "  |  " + videoFile.metadata().resolution()
                + "  |  " + videoFile.metadata().videoCodec().toUpperCase()
                + "  |  " + formatSeconds(videoFile.metadata().durationSeconds())
                + "  |  " + FileSizeFormatter.format(videoFile.sizeBytes());
    }

    private void startVideoAnalysis(ScanResult scanResult) {
        try {
            activeAnalysisTask = new VideoAnalysisTask(videoAnalysisService, scanResult.videos());
        } catch (FfprobeException exception) {
            handleFfprobeUnavailable(exception);
            return;
        }

        compressionProgressBar.progressProperty().unbind();
        compressionProgressBar.progressProperty().bind(activeAnalysisTask.progressProperty());
        statusValue.textProperty().unbind();
        statusValue.textProperty().bind(activeAnalysisTask.messageProperty());
        currentFileValue.textProperty().unbind();
        currentFileValue.textProperty().bind(activeAnalysisTask.messageProperty());
        queueStateLabel.setText("Analyzing metadata");
        workspaceStatusValue.setText("Analyzing metadata");
        appendLog("analysis", "Starting FFprobe metadata extraction for " + scanResult.videos().size() + " videos.");

        activeAnalysisTask.setOnSucceeded(event -> handleAnalysisSucceeded(activeAnalysisTask.getValue()));
        activeAnalysisTask.setOnFailed(event -> handleAnalysisFailed(activeAnalysisTask.getException()));
        activeAnalysisTask.setOnCancelled(event -> handleAnalysisCancelled());
        scanExecutor.submit(activeAnalysisTask);
    }

    private void handleAnalysisSucceeded(List<VideoFile> analyzedVideoResults) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        statusValue.setText("Analysis complete");
        currentFileValue.setText("No active file");
        queueStateLabel.setText(analyzedVideoResults.size() + " videos analyzed");
        workspaceStatusValue.setText("Metadata ready");
        compressionSpeedValue.setText("Analysis complete");
        estimatedSavingsValue.setText("Estimator next");
        reportTargetsLabel.setText("Duration, resolution, codec, bitrate, frame rate, and audio codec are now available.");
        analyzedVideos = List.copyOf(analyzedVideoResults);
        queueListView.getItems().setAll(analyzedVideos.stream().map(this::describeVideo).toList());
        appendLog("analysis", "FFprobe analysis complete for " + analyzedVideos.size() + " videos.");
        activeAnalysisTask = null;
        updateActionControls();
    }

    private void handleAnalysisFailed(Throwable throwable) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        currentFileValue.setText("No active file");
        compressionSpeedValue.setText("Analysis unavailable");
        estimatedSavingsValue.setText("Pending");
        queueStateLabel.setText("Scan complete, analysis failed");
        workspaceStatusValue.setText("Analysis failed");
        statusValue.setText("Analysis failed");
        analyzedVideos = Collections.emptyList();
        appendLog("analysis", "FFprobe analysis failed: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
        activeAnalysisTask = null;
        updateActionControls();
    }

    private void handleAnalysisCancelled() {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        currentFileValue.setText("No active file");
        queueStateLabel.setText("Analysis cancelled");
        workspaceStatusValue.setText("Analysis cancelled");
        statusValue.setText("Analysis cancelled");
        compressionSpeedValue.setText("Analysis cancelled");
        analyzedVideos = Collections.emptyList();
        appendLog("analysis", "FFprobe analysis cancelled.");
        activeAnalysisTask = null;
        updateActionControls();
    }

    private void handleFfprobeUnavailable(FfprobeException exception) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        currentFileValue.setText("No active file");
        queueStateLabel.setText("Scan complete, ffprobe unavailable");
        workspaceStatusValue.setText("Waiting for ffprobe");
        statusValue.setText("ffprobe unavailable");
        compressionSpeedValue.setText("Analysis unavailable");
        estimatedSavingsValue.setText("Pending");
        reportTargetsLabel.setText("Bundle ffprobe.exe in the ffmpeg folder to enable metadata extraction.");
        analyzedVideos = Collections.emptyList();
        appendLog("analysis", exception.getMessage());
        updateActionControls();
    }

    private void handleBatchCompressionSucceeded(BatchCompressionResult batchCompressionResult) {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        statusValue.setText("Batch compression complete");
        workspaceStatusValue.setText("Batch complete");
        queueStateLabel.setText(batchCompressionResult.completedCount() + " completed, "
                + batchCompressionResult.skippedCount() + " skipped, "
                + batchCompressionResult.failedCount() + " failed");
        currentFileValue.setText("No active file");
        compressionSpeedValue.setText("Batch complete");
        elapsedTimeValue.setText(formatDuration(batchCompressionResult.elapsedTime()));
        remainingTimeValue.setText("00:00:00");
        estimatedSavingsValue.setText("%.0f%%".formatted(batchCompressionResult.overallSavingsRatio() * 100));
        List<Path> reports = reportService.generate(batchCompressionResult);
        reportTargetsLabel.setText("Reports generated: " + reports.stream()
                .map(path -> path.getFileName().toString())
                .collect(java.util.stream.Collectors.joining(", ")));
        queueListView.getItems().setAll(buildBatchResultRows(batchCompressionResult.results()));
        appendLog("compress", "Batch complete: " + batchCompressionResult.completedCount()
                + " completed, " + batchCompressionResult.skippedCount()
                + " skipped, " + batchCompressionResult.failedCount()
                + " failed, savings " + "%.0f%%".formatted(batchCompressionResult.overallSavingsRatio() * 100) + ".");
        appendLog("reports", "Generated " + reports.size() + " batch reports.");
        activeBatchCompressionTask = null;
        updateActionControls();
    }

    private void handleBatchCompressionFailed(Throwable throwable) {
        if (throwable instanceof FfmpegException ffmpegException) {
            handleFfmpegUnavailable(ffmpegException);
            activeBatchCompressionTask = null;
            return;
        }
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        statusValue.setText("Batch compression failed");
        workspaceStatusValue.setText("Batch failed");
        queueStateLabel.setText("Batch compression failed");
        currentFileValue.setText("No active file");
        compressionSpeedValue.setText("Batch failed");
        remainingTimeValue.setText("--:--:--");
        appendLog("compress", "Batch compression failed: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
        activeBatchCompressionTask = null;
        updateActionControls();
    }

    private void handleBatchCompressionCancelled() {
        compressionProgressBar.progressProperty().unbind();
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        statusValue.setText("Batch compression cancelled");
        workspaceStatusValue.setText("Batch cancelled");
        queueStateLabel.setText("Batch compression cancelled");
        currentFileValue.setText("No active file");
        compressionSpeedValue.setText("Batch cancelled");
        appendLog("compress", "Batch compression cancelled.");
        activeBatchCompressionTask = null;
        updateActionControls();
    }

    private void handleFfmpegUnavailable(FfmpegException exception) {
        statusValue.textProperty().unbind();
        currentFileValue.textProperty().unbind();
        statusValue.setText("ffmpeg unavailable");
        workspaceStatusValue.setText("Waiting for ffmpeg");
        queueStateLabel.setText("Compression unavailable");
        currentFileValue.setText("No active file");
        compressionSpeedValue.setText("Compression unavailable");
        appendLog("compress", exception.getMessage());
        updateActionControls();
    }

    private void updateActionControls() {
        boolean busy = (activeScanTask != null && activeScanTask.isRunning())
                || (activeAnalysisTask != null && activeAnalysisTask.isRunning())
                || (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning());
        scanButton.setDisable(selectedInputFolder == null || busy);
        boolean compressionReady = selectedOutputFolder != null && !analyzedVideos.isEmpty() && !busy;
        compressButton.setDisable(!compressionReady);
        boolean batchRunning = activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning();
        boolean batchPaused = batchRunning && activeBatchCompressionTask.isPaused();
        pauseButton.setDisable(!batchRunning || batchPaused);
        resumeButton.setDisable(!batchPaused);
        cancelButton.setDisable(!busy);
    }

    private String formatSeconds(double seconds) {
        long roundedSeconds = Math.round(seconds);
        long hours = roundedSeconds / 3600;
        long minutes = (roundedSeconds % 3600) / 60;
        long remainingSeconds = roundedSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    private List<String> buildBatchResultRows(List<CompressionResult> results) {
        return results.stream()
                .map(result -> {
                    String status = switch (result.status()) {
                        case SKIPPED -> "SKIPPED";
                        case FAILED -> "FAILED";
                        case COMPLETED -> "DONE";
                    };
                    String outputName = result.outputFile() == null ? "No output" : result.outputFile().getFileName().toString();
                    return status + "  |  " + outputName
                            + "  |  " + FileSizeFormatter.format(result.compressedSizeBytes())
                            + "  |  " + result.message();
                })
                .toList();
    }

    private String yesNo(boolean selected) {
        return selected ? "yes" : "no";
    }
}
