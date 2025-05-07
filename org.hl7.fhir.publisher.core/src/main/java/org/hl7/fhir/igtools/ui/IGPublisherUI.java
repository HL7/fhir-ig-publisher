package org.hl7.fhir.igtools.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.utilities.settings.FhirSettings;

public class IGPublisherUI extends JFrame {
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
    private JComboBox<String> folderComboBox;
    private JTextField terminologyServerField;
    private JCheckBox noNarrativeCheckBox;
    private JCheckBox noValidationCheckBox;
    private JCheckBox noNetworkCheckBox;
    private JCheckBox trackFragmentCheckBox;
    private JCheckBox clearTermCacheCheckBox;
    private JCheckBox noSushiCheckBox;
    private JCheckBox debugCheckBox;
    private JCheckBox allowNonConformantTxCheckBox;
    private JTextArea logTextArea;
    private JButton runButton;
    private JButton cancelButton;
    private JButton copyLogButton;
    private JButton clearLogButton;
    private JButton saveLogButton;
    
    // Other fields
    private Preferences prefs;
    private Process currentProcess;
    private Thread currentThread;
    private ExecutorService executorService;
    private String[] cmdArgs;
    private boolean wasCanceled;
    private boolean autoRun = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IGPublisherUI ui = new IGPublisherUI(args);
            ui.setVisible(true);
        });
    }

    public IGPublisherUI(String[] args) {
        // Initialize fields
        prefs = Preferences.userNodeForPackage(IGPublisherUI.class);
        executorService = Executors.newFixedThreadPool(2);
        cmdArgs = args;
        
        // Set up the frame
        setTitle("IG Publisher UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(800, 700));
        
        // Create the main panel with a vertical layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Create and add components
        mainPanel.add(createFolderPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(createTerminologyServerPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(createOptionsPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(createRunButtonsPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(createLogPanel());
        
        // Add the main panel to the frame
        setContentPane(mainPanel);
        
        // Process command line args
        processCommandLineArgs();
        
        // Pack the frame
        pack();
        setLocationRelativeTo(null); // Center on screen
        
        // Add window listener for cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (currentProcess != null) {
                    currentProcess.destroy();
                }
                executorService.shutdown();
            }
        });
        
        // Auto-run if specified
        if (autoRun) {
            SwingUtilities.invokeLater(this::runIGPublisher);
        }
    }
    
    /**
     * Create the folder selection panel
     */
    private JPanel createFolderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        
        JLabel folderLabel = new JLabel("IG Folder:");
        folderLabel.setPreferredSize(new Dimension(120, 25));
        panel.add(folderLabel, BorderLayout.WEST);
        
        folderComboBox = new JComboBox<>();
        folderComboBox.setEditable(true);
        loadRecentFolders();
        panel.add(folderComboBox, BorderLayout.CENTER);
        
        JButton chooseFolderButton = new JButton("Browse...");
        chooseFolderButton.addActionListener(e -> chooseFolder());
        panel.add(chooseFolderButton, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * Create the terminology server panel
     */
    private JPanel createTerminologyServerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        
        JLabel termServerLabel = new JLabel("Terminology Server:");
        termServerLabel.setPreferredSize(new Dimension(120, 25));
        panel.add(termServerLabel, BorderLayout.WEST);
        
        terminologyServerField = new JTextField();
        terminologyServerField.setText(prefs.get(PREF_TERMINOLOGY_SERVER, ""));
        panel.add(terminologyServerField, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create the options panel with all checkboxes
     */
    private JPanel createOptionsPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Options", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION));
        
        JPanel panel = new JPanel(new GridLayout(0, 3, 20, 10)); // 0 rows means as many as needed
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        noNarrativeCheckBox = new JCheckBox("No Narrative");
        noNarrativeCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_NARRATIVE, false));
        panel.add(noNarrativeCheckBox);
        
        noValidationCheckBox = new JCheckBox("No Validation");
        noValidationCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_VALIDATION, false));
        panel.add(noValidationCheckBox);
        
        noNetworkCheckBox = new JCheckBox("No Network");
        noNetworkCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_NETWORK, false));
        panel.add(noNetworkCheckBox);
        
        trackFragmentCheckBox = new JCheckBox("Track Fragment Usage");
        trackFragmentCheckBox.setSelected(prefs.getBoolean(PREF_OPT_TRACK_FRAGMENT, false));
        panel.add(trackFragmentCheckBox);
        
        clearTermCacheCheckBox = new JCheckBox("Clear Terminology Cache");
        clearTermCacheCheckBox.setSelected(prefs.getBoolean(PREF_OPT_CLEAR_TERM_CACHE, false));
        panel.add(clearTermCacheCheckBox);
        
        noSushiCheckBox = new JCheckBox("No Sushi");
        noSushiCheckBox.setSelected(prefs.getBoolean(PREF_OPT_NO_SUSHI, false));
        panel.add(noSushiCheckBox);
        
        debugCheckBox = new JCheckBox("Debug");
        debugCheckBox.setSelected(prefs.getBoolean(PREF_OPT_DEBUG, false));
        panel.add(debugCheckBox);
        
        allowNonConformantTxCheckBox = new JCheckBox("Allow Non-conformant Terminology Servers");
        allowNonConformantTxCheckBox.setSelected(prefs.getBoolean(PREF_OPT_ALLOW_NONCONF_TX, false));
        panel.add(allowNonConformantTxCheckBox);
        
        outerPanel.add(panel, BorderLayout.CENTER);
        
        return outerPanel;
    }
    
    /**
     * Create the panel with run/cancel buttons
     */
    private JPanel createRunButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        runButton = new JButton("Run IG Publisher");
        runButton.setPreferredSize(new Dimension(180, 30));
        runButton.addActionListener(e -> runIGPublisher());
        
        cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelIGPublisher());
        
        panel.add(cancelButton);
        panel.add(runButton);
        
        return panel;
    }
    
    /**
     * Create the log panel with text area and buttons
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        
        // Log label
        JLabel logLabel = new JLabel("Output Log:");
        panel.add(logLabel, BorderLayout.NORTH);
        
        // Log text area in a scroll pane
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(750, 400));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Log buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        copyLogButton = new JButton("Copy Log");
        copyLogButton.addActionListener(e -> copyLogToClipboard());
        buttonPanel.add(copyLogButton);
        
        clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> logTextArea.setText(""));
        buttonPanel.add(clearLogButton);
        
        saveLogButton = new JButton("Save Log");
        saveLogButton.addActionListener(e -> saveLogToFile());
        buttonPanel.add(saveLogButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
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
                folderComboBox.setSelectedItem(folderPath);
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
    
    /**
     * Open folder chooser dialog
     */
    private void chooseFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select IG Folder");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        // Set initial directory if available
        String currentFolder = (String) folderComboBox.getSelectedItem();
        if (currentFolder != null && !currentFolder.isEmpty()) {
            File currentDir = new File(currentFolder);
            if (currentDir.exists()) {
                fileChooser.setCurrentDirectory(currentDir);
            }
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String folderPath = fileChooser.getSelectedFile().getAbsolutePath();
            folderComboBox.setSelectedItem(folderPath);
            addRecentFolder(folderPath);
        }
    }
    
    /**
     * Load recent folders from preferences
     */
    private void loadRecentFolders() {
        String foldersStr = prefs.get(PREF_RECENT_FOLDERS, "");
        if (!foldersStr.isEmpty()) {
            String[] folders = foldersStr.split("\\|");
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(folders);
            folderComboBox.setModel(model);
            if (folders.length > 0) {
                folderComboBox.setSelectedItem(folders[0]);
            }
        }
    }
    
    /**
     * Add a folder to recent folders list
     */
    private void addRecentFolder(String folder) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) folderComboBox.getModel();
        
        // Remove if already exists
        model.removeElement(folder);
        
        // Add to the top
        model.insertElementAt(folder, 0);
        folderComboBox.setSelectedItem(folder);
        
        // Limit size
        while (model.getSize() > MAX_RECENT_FOLDERS) {
            model.removeElementAt(model.getSize() - 1);
        }
        
        // Save to preferences
        StringBuilder foldersStr = new StringBuilder();
        for (int i = 0; i < model.getSize(); i++) {
            if (foldersStr.length() > 0) {
                foldersStr.append("|");
            }
            foldersStr.append(model.getElementAt(i));
        }
        prefs.put(PREF_RECENT_FOLDERS, foldersStr.toString());
    }
    
    /**
     * Save current settings to preferences
     */
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
    
    /**
     * Run the IG Publisher
     */
    private void runIGPublisher() {
        String folderPath = (String) folderComboBox.getSelectedItem();
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
        
        // Update button states
        runButton.setEnabled(false);
        cancelButton.setEnabled(true);
        
        // Set log area appearance for active state
        logTextArea.setBackground(new Color(240, 240, 240));
        
        // Clear log
        logTextArea.setText("");
        
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
        
        logTextArea.append(commandStr.toString());
        
        // Create a custom output stream that redirects to the TextArea
        OutputStream customOutputStream = new OutputStream() {
            private StringBuilder lineBuffer = new StringBuilder();
            
            @Override
            public void write(int b) {
                char c = (char) b;
                if (c == '\n') {
                    // End of line, update the TextArea
                    final String line = lineBuffer.toString();
                    SwingUtilities.invokeLater(() -> logTextArea.append(line + "\n"));
                    lineBuffer = new StringBuilder();
                } else {
                    lineBuffer.append(c);
                }
            }
            
            @Override
            public void flush() {
                if (lineBuffer.length() > 0) {
                    final String line = lineBuffer.toString();
                    SwingUtilities.invokeLater(() -> logTextArea.append(line));
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
                
                // Call the Publisher directly
                Publisher.runDirectly(
                    folderPath,
                    termServer.isEmpty() ? FhirSettings.getTxFhirProduction() : termServer,
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
                SwingUtilities.invokeLater(() -> {
                    logTextArea.append("\nIG Publisher completed successfully.\n");
                    runButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    
                    // Change log appearance back to inactive
                    logTextArea.setBackground(UIManager.getColor("TextArea.background"));
                });
                            
            } catch (Exception e) {
                // Handle any other exceptions
                if (!wasCanceled) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    final String stackTrace = sw.toString();
                    
                    SwingUtilities.invokeLater(() -> {
                        logTextArea.append("\nIG Publisher encountered an error: " + e.getMessage() + "\n");
                        logTextArea.append(stackTrace);
                        
                        // Only show error dialog if not canceled
                        if (!wasCanceled) {
                            showErrorDialogWithOutput("IG Publisher Error", e.getMessage() + "\n\n" + stackTrace);
                        }
                        runButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                        
                        // Change log appearance back to inactive
                        logTextArea.setBackground(UIManager.getColor("TextArea.background"));
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
    
    /**
     * Cancel the running IG Publisher process
     */
    private void cancelIGPublisher() {
        if (currentThread != null && currentThread.isAlive()) {
            // Set the canceled flag so we don't show error dialog
            logTextArea.append("\nCanceling IG Publisher operation...\n");
            
            wasCanceled = true;
            // Interrupt the thread
            currentThread.interrupt();
            
            // Reset the UI state
            runButton.setEnabled(true);
            cancelButton.setEnabled(false);
            
            // Change log appearance back to inactive
            logTextArea.setBackground(UIManager.getColor("TextArea.background"));
            
            // Reset thread reference
            currentThread = null;
        }
    }
    
    /**
     * Copy log contents to clipboard
     */
    private void copyLogToClipboard() {
        String logText = logTextArea.getText();
        if (logText != null && !logText.isEmpty()) {
            logTextArea.selectAll();
            logTextArea.copy();
            logTextArea.select(0, 0); // Deselect
        }
    }
    
    /**
     * Save log to a file
     */
    private void saveLogToFile() {
        String logText = logTextArea.getText();
        if (logText == null || logText.isEmpty()) {
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Log File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Log Files (*.log, *.txt)", "log", "txt"));
        fileChooser.setSelectedFile(new File("igpublisher.log"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
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
    
    /**
     * Show a simple error dialog
     */
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Show an error dialog with detailed output
     */
    private void showErrorDialogWithOutput(String title, String content) {
        // Create a dialog with expandable details
        JDialog dialog = new JDialog(this, "Error", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(700, 400);
        dialog.setLocationRelativeTo(this);
        
        // Add the error message at the top
        JLabel errorLabel = new JLabel("<html><b>" + title + "</b></html>");
        errorLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(errorLabel, BorderLayout.NORTH);
        
        // Add the details in a scrollable text area
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        // Add buttons at the bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
            textArea.select(0, 0); // Deselect
        });
        buttonPanel.add(copyButton);
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Show the dialog
        dialog.setVisible(true);
    }
}