package org.hl7.fhir.igtools.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import org.hl7.fhir.igtools.publisher.Publisher;

public class IGPublisherUI extends Application {
    // Preferences keys
    private static final String PREF_RECENT_FOLDERS = "recentFolders";
    private static final String PREF_TERMINOLOGY_SERVER = "terminologyServer";
    private static final String PREF_OPT_NO_NARRATIVE = "optNoNarrative";
    private static final String PREF_OPT_NO_VALIDATION = "optNoValidation";
    private static final String PREF_OPT_NO_NETWORK = "optNoNetwork";
    private static final String PREF_OPT_TRACK_FRAGMENT = "optTrackFragment";
    private static final String PREF_OPT_CLEAR_TERM_CACHE = "optClearTermCache";
    private static final String PREF_OPT_NO_SUSHI = "optNoSushi";
    private static final String PREF_OPT_DEBUG = "optDebug";
    private static final String PREF_OPT_ALLOW_NONCONF_TX = "optAllowNonConformantTx";
    
    // Command line parameter names
    private static final String CMD_TERM_SERVER = "-tx";
    private static final String CMD_NO_NARRATIVE = "-noNarrative";
    private static final String CMD_NARRATIVE = "-narrative";
    private static final String CMD_NO_VALIDATION = "-noValidation";
    private static final String CMD_VALIDATION = "-validation";
    private static final String CMD_NO_NETWORK = "-noNetwork";
    private static final String CMD_NETWORK = "-network";
    private static final String CMD_TRACK_FRAGMENT = "-trackFragments";
    private static final String CMD_NO_TRACK_FRAGMENT = "-noTrackFragments";
    private static final String CMD_CLEAR_TERM_CACHE = "-clearTxCache";
    private static final String CMD_NO_CLEAR_TERM_CACHE = "-noClearTxCache";
    private static final String CMD_NO_SUSHI = "-noSushi";
    private static final String CMD_SUSHI = "-sushi";
    private static final String CMD_DEBUG = "-debug";
    private static final String CMD_NO_DEBUG = "-noDebug";
    private static final String CMD_ALLOW_NONCONF_TX = "-allowNonConformantTx";
    private static final String CMD_NO_ALLOW_NONCONF_TX = "-noAllowNonConformantTx";
    private static final String CMD_AUTO_RUN = "-autoRun";
    private static final String CMD_HELP = "-help";
    
    // Max number of recent folders to remember
    private static final int MAX_RECENT_FOLDERS = 10;
    
    // UI Components
    private ComboBox<String> folderComboBox;
    private TextField terminologyServerField;
    private CheckBox noNarrativeCheckBox;
    private CheckBox noValidationCheckBox;
    private CheckBox noNetworkCheckBox;
    private CheckBox trackFragmentCheckBox;
    private CheckBox clearTermCacheCheckBox;
    private CheckBox noSushiCheckBox;
    private CheckBox debugCheckBox;
    private CheckBox allowNonConformantTxCheckBox;
    private TextArea logTextArea;
    private Button runButton;
    private Button cancelButton;
    private Button copyLogButton;
    private Button clearLogButton;
    private Button saveLogButton;
    
    // Other fields
    private Preferences prefs;
    private Process currentProcess;
    private Thread currentThread;
    private ExecutorService executorService;
    private String[] cmdArgs;
    private boolean wasCanceled;
    private boolean autoRun = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        prefs = Preferences.userNodeForPackage(IGPublisherUI.class);
        executorService = Executors.newFixedThreadPool(2);
        cmdArgs = getParameters().getRaw().toArray(new String[0]);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("IG Publisher UI");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(700);
        
        // Create the UI components
        VBox mainLayout = new VBox(10);
        mainLayout.setPadding(new Insets(15));
        mainLayout.setFillWidth(true);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);
        
        // Folder selection
        HBox folderBox = new HBox(10);
        folderBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        folderBox.setFillHeight(true);
        Label folderLabel = new Label("IG Folder:");
        folderLabel.setMinWidth(120);
        
        folderComboBox = new ComboBox<>();
        folderComboBox.setEditable(true);
        HBox.setHgrow(folderComboBox, Priority.ALWAYS);
        folderComboBox.setMaxWidth(Double.MAX_VALUE);
        loadRecentFolders();
        
        Button chooseFolderButton = new Button("Browse...");
        chooseFolderButton.setOnAction(e -> chooseFolder(primaryStage));
        
        folderBox.getChildren().addAll(folderLabel, folderComboBox, chooseFolderButton);
        
        // Terminology server
        HBox termServerBox = new HBox(10);
        termServerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label termServerLabel = new Label("Terminology Server:");
        termServerLabel.setMinWidth(120);
        
        terminologyServerField = new TextField();
        HBox.setHgrow(terminologyServerField, Priority.ALWAYS);
        terminologyServerField.setMaxWidth(Double.MAX_VALUE);
        terminologyServerField.setText(prefs.get(PREF_TERMINOLOGY_SERVER, ""));
        
        termServerBox.getChildren().addAll(termServerLabel, terminologyServerField);
        
        // Options checkboxes
        TitledPane optionsPane = createOptionsPane();
        
        // Run and Cancel buttons
        HBox runButtonsBox = new HBox(10);
        runButtonsBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        
        runButton = new Button("Run IG Publisher");
        runButton.setPrefWidth(180);
        runButton.getStyleClass().add("run-button");
        runButton.setOnAction(e -> runIGPublisher());
        
        cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.getStyleClass().add("cancel-button");
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> cancelIGPublisher());
        
        runButtonsBox.getChildren().addAll(cancelButton, runButton);
        HBox.setHgrow(runButtonsBox, Priority.ALWAYS);
        
        // Log area
        Label logLabel = new Label("Output Log:");
        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        logTextArea.setPrefHeight(400);
        logTextArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace;");
        logTextArea.getStyleClass().add("inactive-log"); // Initial state is inactive
        VBox.setVgrow(logTextArea, Priority.ALWAYS); // Make log area grow when window resizes
        
        // Log buttons
        HBox logButtonsBox = new HBox(10);
        logButtonsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        copyLogButton = new Button("Copy Log");
        copyLogButton.setOnAction(e -> copyLogToClipboard());
        
        clearLogButton = new Button("Clear Log");
        clearLogButton.setOnAction(e -> logTextArea.clear());
        
        saveLogButton = new Button("Save Log");
        saveLogButton.setOnAction(e -> saveLogToFile(primaryStage));
        
        logButtonsBox.getChildren().addAll(copyLogButton, clearLogButton, saveLogButton);
        
        // Add all components to the main layout
        mainLayout.getChildren().addAll(
                folderBox, 
                termServerBox, 
                optionsPane,
                runButtonsBox,
                logLabel, 
                logTextArea, 
                logButtonsBox
        );
        
        // Set up the scene
        Scene scene = new Scene(mainLayout);
        URL cssResource = getClass().getResource("/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        primaryStage.setScene(scene);
        
        // Handle window close
        primaryStage.setOnCloseRequest(e -> {
            if (currentProcess != null) {
                currentProcess.destroy();
            }
            executorService.shutdown();
        });
        
        // Process command line arguments before showing the window
        processCommandLineArgs();
        
        primaryStage.show();
        
        // Auto-run if specified
        if (autoRun) {
            Platform.runLater(this::runIGPublisher);
        }
    }

    /**
     * Process command line arguments to override saved preferences
     */
    private void processCommandLineArgs() {
        if (cmdArgs.length == 0) {
            return;
        }
        
        String folderPath = null;
        
        // First check for help parameter
        for (String arg : cmdArgs) {
            if (CMD_HELP.equals(arg)) {
                showHelp();
                // Exit after showing help
                System.exit(0);
            }
        }
        
        // Process parameters
        for (int i = 0; i < cmdArgs.length; i++) {
            String arg = cmdArgs[i];
            
            // First parameter without a dash is assumed to be the folder path
            if (!arg.startsWith("-") && folderPath == null) {
                folderPath = arg;
                continue;
            }
            
            // Process known parameters
            switch (arg) {
                case CMD_TERM_SERVER:
                    if (i + 1 < cmdArgs.length && !cmdArgs[i + 1].startsWith("-")) {
                        terminologyServerField.setText(cmdArgs[i + 1]);
                        i++; // Skip the next argument as it's the value
                    }
                    break;
                case CMD_NO_NARRATIVE:
                    noNarrativeCheckBox.setSelected(true);
                    break;
                case CMD_NARRATIVE:
                    noNarrativeCheckBox.setSelected(false);
                    break;
                case CMD_NO_VALIDATION:
                    noValidationCheckBox.setSelected(true);
                    break;
                case CMD_VALIDATION:
                    noValidationCheckBox.setSelected(false);
                    break;
                case CMD_NO_NETWORK:
                    noNetworkCheckBox.setSelected(true);
                    break;
                case CMD_NETWORK:
                    noNetworkCheckBox.setSelected(false);
                    break;
                case CMD_TRACK_FRAGMENT:
                    trackFragmentCheckBox.setSelected(true);
                    break;
                case CMD_NO_TRACK_FRAGMENT:
                    trackFragmentCheckBox.setSelected(false);
                    break;
                case CMD_CLEAR_TERM_CACHE:
                    clearTermCacheCheckBox.setSelected(true);
                    break;
                case CMD_NO_CLEAR_TERM_CACHE:
                    clearTermCacheCheckBox.setSelected(false);
                    break;
                case CMD_NO_SUSHI:
                    noSushiCheckBox.setSelected(true);
                    break;
                case CMD_SUSHI:
                    noSushiCheckBox.setSelected(false);
                    break;
                case CMD_DEBUG:
                    debugCheckBox.setSelected(true);
                    break;
                case CMD_NO_DEBUG:
                    debugCheckBox.setSelected(false);
                    break;
                case CMD_ALLOW_NONCONF_TX:
                    allowNonConformantTxCheckBox.setSelected(true);
                    break;
                case CMD_NO_ALLOW_NONCONF_TX:
                    allowNonConformantTxCheckBox.setSelected(false);
                    break;
                case CMD_AUTO_RUN:
                    autoRun = true;
                    break;
            }
        }
        
        // Set folder if provided
        if (folderPath != null) {
            File folder = new File(folderPath);
            if (folder.isDirectory()) {
                folderComboBox.setValue(folderPath);
            }
        }
    }

    /**
     * Display command line help information
     */
    private void showHelp() {
        System.out.println("IG Publisher UI Command Line Parameters");
        System.out.println("=======================================");
        System.out.println();
        System.out.println("Usage: java -jar igpublisher-ui.jar [folder] [options]");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  [folder]                      IG folder path");
        System.out.println("  -tx <server-url>              Set terminology server URL");
        System.out.println("  -noNarrative | -narrative     Enable/disable narrative generation");
        System.out.println("  -noValidation | -validation   Enable/disable validation");
        System.out.println("  -noNetwork | -network         Enable/disable network access");
        System.out.println("  -trackFragments | -noTrackFragments  Enable/disable fragment tracking");
        System.out.println("  -clearTxCache | -noClearTxCache     Clear/keep terminology cache");
        System.out.println("  -noSushi | -sushi             Disable/enable SUSHI");
        System.out.println("  -debug | -noDebug             Enable/disable debug mode");
        System.out.println("  -allowNonConformantTx | -noAllowNonConformantTx");
        System.out.println("                                Allow/disallow non-conformant terminology servers");
        System.out.println("  -autoRun                      Automatically run the publisher after starting");
        System.out.println("  -help                         Display this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar igpublisher-ui.jar C:\\path\\to\\ig -noValidation -tx http://tx.fhir.org/r4");
        System.out.println("  java -jar igpublisher-ui.jar -noSushi -autoRun");
    }

    private TitledPane createOptionsPane() {
        FlowPane optionsPane = new FlowPane();
        optionsPane.setHgap(20);
        optionsPane.setVgap(10);
        optionsPane.setPadding(new Insets(10));
        optionsPane.setPrefWrapLength(700); // Helps control wrapping behavior
        
        noNarrativeCheckBox = new CheckBox("No Narrative");
        noNarrativeCheckBox.setMinWidth(150);
        noNarrativeCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_NARRATIVE, false));
        
        noValidationCheckBox = new CheckBox("No Validation");
        noValidationCheckBox.setMinWidth(150);
        noValidationCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_VALIDATION, false));
        
        noNetworkCheckBox = new CheckBox("No Network");
        noNetworkCheckBox.setMinWidth(150);
        noNetworkCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_NETWORK, false));
        
        trackFragmentCheckBox = new CheckBox("Track Fragment Usage");
        trackFragmentCheckBox.setMinWidth(180);
        trackFragmentCheckBox.setSelected(prefs.getBoolean(PREF_OPT_TRACK_FRAGMENT, false));
        
        clearTermCacheCheckBox = new CheckBox("Clear Terminology Cache");
        clearTermCacheCheckBox.setMinWidth(180);
        clearTermCacheCheckBox.setSelected(prefs.getBoolean(PREF_OPT_CLEAR_TERM_CACHE, false));
        
        noSushiCheckBox = new CheckBox("No Sushi");
        noSushiCheckBox.setMinWidth(150);
        noSushiCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_SUSHI, false));
        
        debugCheckBox = new CheckBox("Debug");
        debugCheckBox.setMinWidth(150);
        debugCheckBox.setSelected(prefs.getBoolean(PREF_OPT_DEBUG, false));
        
        allowNonConformantTxCheckBox = new CheckBox("Allow Non-conformant Terminology Servers");
        allowNonConformantTxCheckBox.setMinWidth(280);
        allowNonConformantTxCheckBox.setSelected(prefs.getBoolean(PREF_OPT_ALLOW_NONCONF_TX, false));
        
        // Add all checkboxes to the flow pane so they can flow across the available space
        optionsPane.getChildren().addAll(
            noNarrativeCheckBox,
            noValidationCheckBox,
            noNetworkCheckBox,
            trackFragmentCheckBox,
            clearTermCacheCheckBox,
            noSushiCheckBox,
            debugCheckBox,
            allowNonConformantTxCheckBox
        );
        
        TitledPane titledOptionsPane = new TitledPane("Options", optionsPane);
        titledOptionsPane.setCollapsible(true);
        titledOptionsPane.setExpanded(true);
        
        return titledOptionsPane;
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select IG Folder");
        
        // Set initial directory if available
        String currentFolder = folderComboBox.getValue();
        if (currentFolder != null && !currentFolder.isEmpty()) {
            File currentDir = new File(currentFolder);
            if (currentDir.exists()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }
        
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            String folderPath = selectedDirectory.getAbsolutePath();
            folderComboBox.setValue(folderPath);
            addRecentFolder(folderPath);
        }
    }

    private void loadRecentFolders() {
        String foldersStr = prefs.get(PREF_RECENT_FOLDERS, "");
        if (!foldersStr.isEmpty()) {
            String[] folders = foldersStr.split("\\|");
            ObservableList<String> folderList = FXCollections.observableArrayList(folders);
            folderComboBox.setItems(folderList);
            if (!folderList.isEmpty()) {
                folderComboBox.setValue(folderList.get(0));
            }
        }
    }

    private void addRecentFolder(String folder) {
        ObservableList<String> items = folderComboBox.getItems();
        
        // Remove if already exists
        items.remove(folder);
        
        // Add to the top
        items.add(0, folder);
        
        // Limit size
        if (items.size() > MAX_RECENT_FOLDERS) {
            items = FXCollections.observableArrayList(items.subList(0, MAX_RECENT_FOLDERS));
            folderComboBox.setItems(items);
        }
        
        // Save to preferences
        StringBuilder foldersStr = new StringBuilder();
        for (String item : items) {
            if (foldersStr.length() > 0) {
                foldersStr.append("|");
            }
            foldersStr.append(item);
        }
        prefs.put(PREF_RECENT_FOLDERS, foldersStr.toString());
        
        folderComboBox.setValue(folder);
    }

    private void savePreferences() {
        prefs.put(PREF_TERMINOLOGY_SERVER, terminologyServerField.getText());
        prefs.putBoolean(PREF_OPT_NO_NARRATIVE, noNarrativeCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_NO_VALIDATION, noValidationCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_NO_NETWORK, noNetworkCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_TRACK_FRAGMENT, trackFragmentCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_CLEAR_TERM_CACHE, clearTermCacheCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_NO_SUSHI, noSushiCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_DEBUG, debugCheckBox.isSelected());
        prefs.putBoolean(PREF_OPT_ALLOW_NONCONF_TX, allowNonConformantTxCheckBox.isSelected());
    }

    private void runIGPublisher() {
        String folderPath = folderComboBox.getValue();
        if (folderPath == null || folderPath.isEmpty()) {
            showErrorDialog("Please select an IG folder first.");
            return;
        }
        
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            showErrorDialog("The selected folder does not exist or is not a directory.");
            return;
        }
        
        // Save current settings
        addRecentFolder(folderPath);
        savePreferences();
        
        // Update button states and log appearance
        runButton.setDisable(true);
        cancelButton.setDisable(false);
        logTextArea.getStyleClass().remove("inactive-log");
        logTextArea.getStyleClass().add("active-log");
        
        // Clear log
        logTextArea.clear();
        
        // Collect parameters
        boolean noNarrative = noNarrativeCheckBox.isSelected();
        boolean noValidation = noValidationCheckBox.isSelected();
        boolean noNetwork = noNetworkCheckBox.isSelected();
        boolean trackFragmentUsage = trackFragmentCheckBox.isSelected();
        boolean clearTermCache = clearTermCacheCheckBox.isSelected();
        boolean noSushi = noSushiCheckBox.isSelected();
        boolean debug = debugCheckBox.isSelected();
        boolean allowNonConformantTx = allowNonConformantTxCheckBox.isSelected();
        String termServer = terminologyServerField.getText().trim();
        
        // Create a string representation of the command for logging
        StringBuilder commandStr = new StringBuilder("Running IG Publisher directly with parameters:\n");
        commandStr.append("- Folder: ").append(folderPath).append("\n");
        if (!termServer.isEmpty()) commandStr.append("- Terminology Server: ").append(termServer).append("\n");
        commandStr.append("- No Narrative: ").append(noNarrative).append("\n");
        commandStr.append("- No Validation: ").append(noValidation).append("\n");
        commandStr.append("- No Network: ").append(noNetwork).append("\n");
        commandStr.append("- Track Fragment Usage: ").append(trackFragmentUsage).append("\n");
        commandStr.append("- Clear Terminology Cache: ").append(clearTermCache).append("\n");
        commandStr.append("- No Sushi: ").append(noSushi).append("\n");
        commandStr.append("- Debug: ").append(debug).append("\n");
        commandStr.append("- Allow Non-conformant Terminology Servers: ").append(allowNonConformantTx).append("\n\n");
        
        logTextArea.appendText(commandStr.toString());
        
        // Create a custom output stream that redirects to the TextArea
        OutputStream customOutputStream = new OutputStream() {
            private StringBuilder lineBuffer = new StringBuilder();
            
            @Override
            public void write(int b) {
                char c = (char) b;
                if (c == '\n') {
                    // End of line, update the TextArea
                    final String line = lineBuffer.toString();
                    Platform.runLater(() -> logTextArea.appendText(line + "\n"));
                    lineBuffer = new StringBuilder();
                } else {
                    lineBuffer.append(c);
                }
            }
            
            @Override
            public void flush() {
                if (lineBuffer.length() > 0) {
                    final String line = lineBuffer.toString();
                    Platform.runLater(() -> logTextArea.appendText(line));
                    lineBuffer = new StringBuilder();
                }
            }
        };
        
        // Create a PrintStream wrapper for our custom output stream
        PrintStream customPrintStream = new PrintStream(customOutputStream, true);
        
        wasCanceled = false;
        
        // Create a Thread object so we can interrupt it if needed
        Thread publisherThread = new Thread(() -> {
            // Save the original System.out and System.err
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            
            try {
                // Redirect System.out and System.err to our custom stream
                System.setOut(customPrintStream);
                System.setErr(customPrintStream);
                
                // Call the Publisher directly - adding the new parameter
                Publisher.runDirectly(
                    folderPath,
                    termServer.isEmpty() ? null : termServer,
                    noNarrative,
                    noValidation,
                    noNetwork,
                    trackFragmentUsage,
                    clearTermCache,
                    noSushi,
                    debug,
                    allowNonConformantTx
                );
                
                // Success message
                Platform.runLater(() -> {
                    logTextArea.appendText("\nIG Publisher completed successfully.\n");
                    runButton.setDisable(false);
                    cancelButton.setDisable(true);
                    
                    // Change log appearance back to inactive
                    logTextArea.getStyleClass().remove("active-log");
                    logTextArea.getStyleClass().add("inactive-log");
                });
                            
            } catch (Exception e) {
                // Handle any other exceptions
                if (!wasCanceled) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    final String stackTrace = sw.toString();
                    
                    Platform.runLater(() -> {
                        logTextArea.appendText("\nIG Publisher encountered an error: " + e.getMessage() + "\n");
                        logTextArea.appendText(stackTrace);
                        
                        // Only show error dialog if not canceled
                        if (!wasCanceled) {
                            showErrorDialogWithOutput("IG Publisher Error", e.getMessage() + "\n\n" + stackTrace);
                        }
                        runButton.setDisable(false);
                        cancelButton.setDisable(true);
                        
                        // Change log appearance back to inactive
                        logTextArea.getStyleClass().remove("active-log");
                        logTextArea.getStyleClass().add("inactive-log");
                    });
                }
            } finally {
                // Restore the original System.out and System.err
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        });
        
        // Store the thread so we can interrupt it if cancel is pressed
        currentThread = publisherThread;
        
        // Start the thread
        publisherThread.start();
    }
    
    private void cancelIGPublisher() {
        if (currentThread != null && currentThread.isAlive()) {
            // Set the canceled flag so we don't show error dialog
            logTextArea.appendText("\nCanceling IG Publisher operation...\n");
            
            wasCanceled = true;
            // Interrupt the thread
            currentThread.interrupt();
            
            // Reset the UI state
            runButton.setDisable(false);
            cancelButton.setDisable(true);
            
            // Change log appearance back to inactive
            logTextArea.getStyleClass().remove("active-log");
            logTextArea.getStyleClass().add("inactive-log");
            
            // Reset thread reference
            currentThread = null;
        }
    }

    private void copyLogToClipboard() {
        String logText = logTextArea.getText();
        if (logText != null && !logText.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(logText);
            clipboard.setContent(content);
        }
    }

    private void saveLogToFile(Stage stage) {
        String logText = logTextArea.getText();
        if (logText == null || logText.isEmpty()) {
            return;
        }
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Log File");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt")
        );
        fileChooser.setInitialFileName("igpublisher.log");
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.writeString(
                        Paths.get(file.getAbsolutePath()),
                        logText,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            } catch (IOException e) {
                showErrorDialog("Error saving log: " + e.getMessage());
            }
        }
    }

    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorDialogWithOutput(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        
        // Create expandable content
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(300);
        textArea.setPrefWidth(600);
        
        // Add copy button
        Button copyButton = new Button("Copy to Clipboard");
        copyButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
            clipContent.putString(content);
            clipboard.setContent(clipContent);
        });
        
        VBox vbox = new VBox(10, textArea, copyButton);
        
        alert.getDialogPane().setExpandableContent(vbox);
        alert.getDialogPane().setExpanded(true);
        
        alert.showAndWait();
    }
}