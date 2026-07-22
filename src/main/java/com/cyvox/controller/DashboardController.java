package com.cyvox.controller;

import com.cyvox.animation.UiAnimator;
import com.cyvox.exception.FfmpegException;
import com.cyvox.exception.FfprobeException;
import com.cyvox.model.ApplicationState;
import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionPreset;
import com.cyvox.model.ScanResult;
import com.cyvox.model.VideoFile;
import com.cyvox.service.FfmpegResolver;
import com.cyvox.service.FfprobeResolver;
import com.cyvox.service.ReportService;
import com.cyvox.service.VideoAnalysisService;
import com.cyvox.service.VideoCompressionService;
import com.cyvox.service.VideoScannerService;
import com.cyvox.task.BatchCompressionTask;
import com.cyvox.task.VideoAnalysisTask;
import com.cyvox.task.VideoScanTask;
import com.cyvox.util.FileSizeFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);
    private static final String DEFAULT_PATTERN = "{name}_cyvox";

    private final VideoScannerService videoScannerService = new VideoScannerService();
    private final VideoAnalysisService videoAnalysisService = new VideoAnalysisService(new FfprobeResolver());
    private final VideoCompressionService videoCompressionService = new VideoCompressionService(new FfmpegResolver());
    private final ReportService reportService = new ReportService();
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread worker = new Thread(runnable, "cyvox-ui-worker");
        worker.setDaemon(true);
        return worker;
    });

    private ApplicationState state = ApplicationState.EMPTY;
    private Path selectedInputFolder;
    private Path selectedOutputFolder;
    private VideoScanTask activeScanTask;
    private VideoAnalysisTask activeAnalysisTask;
    private BatchCompressionTask activeBatchCompressionTask;
    private List<VideoFile> analyzedVideos = new ArrayList<>();
    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    private StackPane windowRoot;

    private Region titleBarHandle;
    private Button settingsButton;
    private Button settingsOpenButton;
    private Button minimizeButton;
    private Button maximizeButton;
    private Button closeButton;
    private Label heroSubtitle;
    private Label inputFolderValue;
    private Label outputFolderValue;
    private ComboBox<String> presetComboBox;
    private Button inputBrowseButton;
    private Button outputBrowseButton;
    private Button scanButton;
    private Button compressButton;
    private Region statsPanel;
    private Label estimatedSizeValue;
    private Label originalSizeValue;
    private Label spaceSavedValue;
    private Label compressionRatioValue;
    private Label currentStatusValue;
    private Region dropZonePane;
    private Button dropChooseButton;
    private Region progressView;
    private Label progressTitle;
    private Label currentFileValue;
    private Label progressPercentageValue;
    private ProgressBar compressionProgressBar;
    private Label remainingTimeValue;
    private Button pauseButton;
    private Button cancelButton;
    private Button openFolderButton;
    private Button compressAnotherButton;
    private Region settingsOverlay;
    private TextField filenamePatternField;
    private Spinner<Integer> threadSpinner;
    private CheckBox skipExistingCheckBox;
    private CheckBox overwriteExistingCheckBox;
    private CheckBox keepMetadataCheckBox;
    private Button settingsCloseButton;
    private Label toastTitle;
    private Label toastMessage;

    @FXML
    private void initialize() {
        bindNodes();
        configureControls();
        configureDropZone();
        transitionTo(ApplicationState.EMPTY, "Ready", "Select a folder to scan your videos.");
    }

    public void configureWindow(Stage stage) {
        titleBarHandle.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        titleBarHandle.setOnMouseDragged(event -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        minimizeButton.setOnAction(event -> stage.setIconified(true));
        maximizeButton.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        closeButton.setOnAction(event -> {
            shutdown();
            stage.close();
        });
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
            activeBatchCompressionTask.resume();
        }
        workerExecutor.shutdownNow();
    }

    private void bindNodes() {
        titleBarHandle = lookup("titleBarHandle");
        settingsButton = lookup("settingsButton");
        settingsOpenButton = lookup("settingsOpenButton");
        minimizeButton = lookup("minimizeButton");
        maximizeButton = lookup("maximizeButton");
        closeButton = lookup("closeButton");
        heroSubtitle = lookup("heroSubtitle");
        inputFolderValue = lookup("inputFolderValue");
        outputFolderValue = lookup("outputFolderValue");
        presetComboBox = lookup("presetComboBox");
        inputBrowseButton = lookup("inputBrowseButton");
        outputBrowseButton = lookup("outputBrowseButton");
        scanButton = lookup("scanButton");
        compressButton = lookup("compressButton");
        statsPanel = lookup("statsPanel");
        estimatedSizeValue = lookup("estimatedSizeValue");
        originalSizeValue = lookup("originalSizeValue");
        spaceSavedValue = lookup("spaceSavedValue");
        compressionRatioValue = lookup("compressionRatioValue");
        currentStatusValue = lookup("currentStatusValue");
        dropZonePane = lookup("dropZonePane");
        dropChooseButton = lookup("dropChooseButton");
        progressView = lookup("progressView");
        progressTitle = lookup("progressTitle");
        currentFileValue = lookup("currentFileValue");
        progressPercentageValue = lookup("progressPercentageValue");
        compressionProgressBar = lookup("compressionProgressBar");
        remainingTimeValue = lookup("remainingTimeValue");
        pauseButton = lookup("pauseButton");
        cancelButton = lookup("cancelButton");
        openFolderButton = lookup("openFolderButton");
        compressAnotherButton = lookup("compressAnotherButton");
        settingsOverlay = lookup("settingsOverlay");
        filenamePatternField = lookup("filenamePatternField");
        threadSpinner = lookup("threadSpinner");
        skipExistingCheckBox = lookup("skipExistingCheckBox");
        overwriteExistingCheckBox = lookup("overwriteExistingCheckBox");
        keepMetadataCheckBox = lookup("keepMetadataCheckBox");
        settingsCloseButton = lookup("settingsCloseButton");
        toastTitle = lookup("toastTitle");
        toastMessage = lookup("toastMessage");
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T lookup(String id) {
        Node node = windowRoot.lookup("#" + id);
        if (node == null) {
            throw new IllegalStateException("Missing UI node: " + id);
        }
        return (T) node;
    }

    private void configureControls() {
        statsPanel.setVisible(false);
        statsPanel.setManaged(false);
        presetComboBox.getItems().setAll(List.of(
                CompressionPreset.HIGH_QUALITY.displayName() + " (H.265 - CRF 23)",
                CompressionPreset.BALANCED.displayName() + " (H.265 - CRF 28)",
                CompressionPreset.MAXIMUM_COMPRESSION.displayName() + " (H.265 - CRF 33)",
                CompressionPreset.ARCHIVE.displayName() + " (AV1 - CRF 35)"
        ));
        presetComboBox.getSelectionModel().select(1);
        presetComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshReadyEstimate());
        threadSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 32, 4));

        inputBrowseButton.setOnAction(event -> chooseInputFolder());
        outputBrowseButton.setOnAction(event -> chooseOutputFolder());
        dropChooseButton.setOnAction(event -> chooseInputFolder());
        scanButton.setOnAction(event -> startScan());
        compressButton.setOnAction(event -> startCompression());
        pauseButton.setOnAction(event -> togglePause());
        cancelButton.setOnAction(event -> cancelActiveWork());
        settingsButton.setOnAction(event -> showSettings());
        settingsOpenButton.setOnAction(event -> showSettings());
        settingsCloseButton.setOnAction(event -> hideSettings());
        openFolderButton.setOnAction(event -> openOutputFolder());
        compressAnotherButton.setOnAction(event -> resetForAnotherFolder());
    }

    private void configureDropZone() {
        dropZonePane.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().stream().anyMatch(File::isDirectory)) {
                event.acceptTransferModes(TransferMode.COPY);
                dropZonePane.getStyleClass().add("drop-zone-active");
            }
            event.consume();
        });
        dropZonePane.setOnDragExited(event -> dropZonePane.getStyleClass().remove("drop-zone-active"));
        dropZonePane.setOnDragDropped(event -> {
            boolean completed = event.getDragboard().getFiles().stream()
                    .filter(File::isDirectory)
                    .findFirst()
                    .map(folder -> {
                        setInputFolder(folder.toPath());
                        return true;
                    })
                    .orElse(false);
            dropZonePane.getStyleClass().remove("drop-zone-active");
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void chooseInputFolder() {
        Path selectedPath = chooseDirectory("Choose input folder");
        if (selectedPath != null) {
            setInputFolder(selectedPath);
        }
    }

    private void setInputFolder(Path selectedPath) {
        selectedInputFolder = selectedPath;
        inputFolderValue.setText(selectedPath.toString());
        if (selectedOutputFolder == null) {
            selectedOutputFolder = selectedPath.resolve("CYVOX Compressed");
            outputFolderValue.setText(selectedOutputFolder.toString());
        }
        transitionTo(ApplicationState.EMPTY, "Folder selected", "Scan the folder to prepare compression.");
        UiAnimator.pulse(dropZonePane);
        LOGGER.info("Input folder set to {}", selectedPath);
    }

    private void chooseOutputFolder() {
        Path selectedPath = chooseDirectory("Choose output folder");
        if (selectedPath != null) {
            selectedOutputFolder = selectedPath;
            outputFolderValue.setText(selectedPath.toString());
            transitionTo(state, "Output ready", "Compressed files will be saved there.");
            LOGGER.info("Output folder set to {}", selectedPath);
        }
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File selection = chooser.showDialog(windowRoot.getScene().getWindow());
        return selection == null ? null : selection.toPath();
    }

    private void startScan() {
        if (selectedInputFolder == null) {
            transitionTo(ApplicationState.EMPTY, "Choose input folder", "Select or drop a folder first.");
            return;
        }
        activeScanTask = new VideoScanTask(videoScannerService, selectedInputFolder);
        bindTaskProgress(activeScanTask.messageProperty(), activeScanTask.progressProperty());
        transitionTo(ApplicationState.SCANNING, "Scanning", "Finding supported videos...");
        activeScanTask.setOnSucceeded(event -> handleScanSucceeded(activeScanTask.getValue()));
        activeScanTask.setOnFailed(event -> handleScanFailed(activeScanTask.getException()));
        activeScanTask.setOnCancelled(event -> handleScanCancelled());
        workerExecutor.submit(activeScanTask);
    }

    private void handleScanSucceeded(ScanResult scanResult) {
        activeScanTask = null;
        if (scanResult.videos().isEmpty()) {
            analyzedVideos = List.of();
            unbindProgress();
            transitionTo(ApplicationState.EMPTY, "No videos found", "Choose a folder with supported video files.");
            return;
        }
        startVideoAnalysis(scanResult);
    }

    private void startVideoAnalysis(ScanResult scanResult) {
        try {
            activeAnalysisTask = new VideoAnalysisTask(videoAnalysisService, scanResult.videos());
        } catch (FfprobeException exception) {
            handleFailure("FFprobe unavailable", exception.getMessage());
            return;
        }

        bindTaskProgress(activeAnalysisTask.messageProperty(), activeAnalysisTask.progressProperty());
        transitionTo(ApplicationState.SCANNING, "Analyzing", "Reading video metadata...");
        activeAnalysisTask.setOnSucceeded(event -> handleAnalysisSucceeded(activeAnalysisTask.getValue()));
        activeAnalysisTask.setOnFailed(event -> handleAnalysisFailed(activeAnalysisTask.getException()));
        activeAnalysisTask.setOnCancelled(event -> handleAnalysisCancelled());
        workerExecutor.submit(activeAnalysisTask);
    }

    private void handleAnalysisSucceeded(List<VideoFile> analyzedVideoResults) {
        activeAnalysisTask = null;
        analyzedVideos = List.copyOf(analyzedVideoResults);
        unbindProgress();
        refreshReadyEstimate();
        transitionTo(ApplicationState.READY, "Ready to compress", analyzedVideos.size() + " videos prepared.");
    }

    private void refreshReadyEstimate() {
        if (analyzedVideos.isEmpty()) {
            return;
        }
        long originalBytes = analyzedVideos.stream().mapToLong(VideoFile::sizeBytes).sum();
        double ratio = estimateSavingsRatio(resolveSelectedPreset());
        long savedBytes = Math.round(originalBytes * ratio);
        long estimatedBytes = Math.max(0, originalBytes - savedBytes);

        originalSizeValue.setText(FileSizeFormatter.format(originalBytes));
        estimatedSizeValue.setText(FileSizeFormatter.format(estimatedBytes));
        spaceSavedValue.setText(FileSizeFormatter.format(savedBytes));
        compressionRatioValue.setText("%.0f%%".formatted(ratio * 100));
        currentStatusValue.setText(analyzedVideos.size() + " videos ready.");
    }

    private void startCompression() {
        if (selectedOutputFolder == null || analyzedVideos.isEmpty()) {
            transitionTo(state, "Not ready", "Scan videos and choose an output folder first.");
            return;
        }
        activeBatchCompressionTask = new BatchCompressionTask(
                videoCompressionService,
                analyzedVideos,
                selectedOutputFolder,
                resolveSelectedPreset(),
                filenamePatternField.getText().isBlank() ? DEFAULT_PATTERN : filenamePatternField.getText().trim(),
                overwriteExistingCheckBox.isSelected(),
                skipExistingCheckBox.isSelected(),
                keepMetadataCheckBox.isSelected()
        );

        bindTaskProgress(activeBatchCompressionTask.messageProperty(), activeBatchCompressionTask.progressProperty());
        transitionTo(ApplicationState.COMPRESSING, "Compressing", "Encoding your videos...");
        activeBatchCompressionTask.setOnSucceeded(event -> handleBatchCompressionSucceeded(activeBatchCompressionTask.getValue()));
        activeBatchCompressionTask.setOnFailed(event -> handleBatchCompressionFailed(activeBatchCompressionTask.getException()));
        activeBatchCompressionTask.setOnCancelled(event -> handleBatchCompressionCancelled());
        workerExecutor.submit(activeBatchCompressionTask);
    }

    private void handleBatchCompressionSucceeded(BatchCompressionResult result) {
        activeBatchCompressionTask = null;
        unbindProgress();
        compressionProgressBar.setProgress(1);
        progressPercentageValue.setText("100%");
        progressTitle.setText("Compression complete");
        currentFileValue.setText("Saved " + FileSizeFormatter.format(result.totalOriginalSizeBytes() - result.totalCompressedSizeBytes()));
        remainingTimeValue.setText(formatDuration(result.elapsedTime()));
        List<Path> reports = reportService.generate(result);
        currentStatusValue.setText("Reports generated: " + reports.size());
        transitionTo(ApplicationState.COMPLETED, "Done", "Your compressed videos are ready.");
        LOGGER.info("Compression complete with {} generated reports", reports.size());
    }

    private void togglePause() {
        if (activeBatchCompressionTask == null || !activeBatchCompressionTask.isRunning()) {
            return;
        }
        if (activeBatchCompressionTask.isPaused()) {
            activeBatchCompressionTask.resume();
            pauseButton.setText("Pause");
            transitionTo(ApplicationState.COMPRESSING, "Resumed", "Compression is running again.");
        } else {
            activeBatchCompressionTask.pause();
            pauseButton.setText("Resume");
            transitionTo(ApplicationState.COMPRESSING, "Paused", "Compression is paused.");
        }
    }

    private void cancelActiveWork() {
        if (activeScanTask != null && activeScanTask.isRunning()) {
            activeScanTask.cancel();
            return;
        }
        if (activeAnalysisTask != null && activeAnalysisTask.isRunning()) {
            activeAnalysisTask.cancel();
            return;
        }
        if (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning()) {
            activeBatchCompressionTask.cancel();
            activeBatchCompressionTask.resume();
        }
    }

    private void resetForAnotherFolder() {
        selectedInputFolder = null;
        analyzedVideos = List.of();
        inputFolderValue.setText("Choose input folder");
        compressionProgressBar.setProgress(0);
        progressPercentageValue.setText("0%");
        transitionTo(ApplicationState.EMPTY, "Ready", "Select a folder to scan your videos.");
    }

    private void openOutputFolder() {
        if (selectedOutputFolder == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(selectedOutputFolder.toFile());
        } catch (IOException exception) {
            handleFailure("Open folder failed", exception.getMessage());
        }
    }

    private void showSettings() {
        UiAnimator.reveal(settingsOverlay);
    }

    private void hideSettings() {
        UiAnimator.hide(settingsOverlay);
    }

    private void bindTaskProgress(javafx.beans.value.ObservableValue<String> message, javafx.beans.value.ObservableValue<Number> progress) {
        currentFileValue.textProperty().unbind();
        currentFileValue.textProperty().bind(message);
        compressionProgressBar.progressProperty().unbind();
        compressionProgressBar.progressProperty().bind(progress);
        progressPercentageValue.textProperty().unbind();
        progressPercentageValue.textProperty().bind(compressionProgressBar.progressProperty().multiply(100).asString("%.0f%%"));
    }

    private void unbindProgress() {
        currentFileValue.textProperty().unbind();
        compressionProgressBar.progressProperty().unbind();
        progressPercentageValue.textProperty().unbind();
    }

    private void handleScanFailed(Throwable throwable) {
        activeScanTask = null;
        handleFailure("Scan failed", Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
    }

    private void handleAnalysisFailed(Throwable throwable) {
        activeAnalysisTask = null;
        handleFailure("Analysis failed", Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
    }

    private void handleBatchCompressionFailed(Throwable throwable) {
        activeBatchCompressionTask = null;
        if (throwable instanceof FfmpegException) {
            handleFailure("FFmpeg unavailable", throwable.getMessage());
            return;
        }
        handleFailure("Compression failed", Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
    }

    private void handleScanCancelled() {
        activeScanTask = null;
        handleFailure("Scan cancelled", "No changes were made.");
    }

    private void handleAnalysisCancelled() {
        activeAnalysisTask = null;
        handleFailure("Analysis cancelled", "No changes were made.");
    }

    private void handleBatchCompressionCancelled() {
        activeBatchCompressionTask = null;
        handleFailure("Compression cancelled", "Partial outputs may exist in the output folder.");
    }

    private void handleFailure(String title, String message) {
        unbindProgress();
        transitionTo(analyzedVideos.isEmpty() ? ApplicationState.EMPTY : ApplicationState.READY, title, message);
        compressionProgressBar.setProgress(0);
        progressPercentageValue.setText("0%");
        LOGGER.warn("{}: {}", title, message);
    }

    private void transitionTo(ApplicationState newState, String title, String message) {
        state = newState;
        toastTitle.setText(title);
        toastMessage.setText(message);
        heroSubtitle.setText(switch (newState) {
            case EMPTY -> selectedInputFolder == null ? "Select a folder to begin." : "Scan the folder to continue.";
            case SCANNING -> "Preparing your videos.";
            case READY -> "Review the estimate, then compress.";
            case COMPRESSING -> "Compression is in progress.";
            case COMPLETED -> "Your compressed videos are ready.";
        });

        scanButton.setDisable(selectedInputFolder == null || isBusy());
        compressButton.setDisable(selectedOutputFolder == null || analyzedVideos.isEmpty() || isBusy());
        pauseButton.setDisable(newState != ApplicationState.COMPRESSING);
        cancelButton.setDisable(!isBusy());
        openFolderButton.setVisible(newState == ApplicationState.COMPLETED);
        openFolderButton.setManaged(newState == ApplicationState.COMPLETED);
        compressAnotherButton.setVisible(newState == ApplicationState.COMPLETED);
        compressAnotherButton.setManaged(newState == ApplicationState.COMPLETED);

        if (newState == ApplicationState.READY && !statsPanel.isVisible()) {
            UiAnimator.reveal(statsPanel);
        }
        if (newState == ApplicationState.COMPRESSING || newState == ApplicationState.COMPLETED) {
            if (!progressView.isVisible()) {
                UiAnimator.reveal(progressView);
            }
        } else if (progressView.isVisible()) {
            UiAnimator.hide(progressView);
        }
        if (newState == ApplicationState.COMPLETED) {
            progressTitle.setText("Compression complete");
            pauseButton.setManaged(false);
            pauseButton.setVisible(false);
            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
        } else {
            pauseButton.setManaged(true);
            pauseButton.setVisible(true);
            cancelButton.setManaged(true);
            cancelButton.setVisible(true);
        }
        Platform.runLater(() -> UiAnimator.pulse(toastTitle));
    }

    private boolean isBusy() {
        return (activeScanTask != null && activeScanTask.isRunning())
                || (activeAnalysisTask != null && activeAnalysisTask.isRunning())
                || (activeBatchCompressionTask != null && activeBatchCompressionTask.isRunning());
    }

    private CompressionPreset resolveSelectedPreset() {
        String selected = presetComboBox.getValue();
        if (selected == null) {
            return CompressionPreset.BALANCED;
        }
        String displayName = selected.contains(" (") ? selected.substring(0, selected.indexOf(" (")) : selected;
        return CompressionPreset.fromDisplayName(displayName);
    }

    private double estimateSavingsRatio(CompressionPreset preset) {
        return switch (preset) {
            case HIGH_QUALITY -> 0.32;
            case BALANCED -> 0.48;
            case MAXIMUM_COMPRESSION -> 0.62;
            case ARCHIVE -> 0.68;
        };
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }
}
