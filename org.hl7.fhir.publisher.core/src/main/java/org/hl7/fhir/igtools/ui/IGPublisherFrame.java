package org.hl7.fhir.igtools.ui;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.utilities.IniFile;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.settings.FhirSettings;

public class IGPublisherFrame extends javax.swing.JFrame {

  private static final String LOG_PREFIX = "--$%^^---";

  JCheckBox noValidateCheckbox;
  JCheckBox noNarrativeCheckbox;

  JCheckBox noSushiCheckbox;

  JCheckBox debugCheckbox;

  private javax.swing.JButton executeButton;
  private javax.swing.JButton chooseIGButton;
  private javax.swing.JButton debugSummaryButton;
  private javax.swing.JButton viewQAButton;
  private javax.swing.JButton viewIgButton;

  private javax.swing.JPanel optionsPanel;
  private javax.swing.JPanel resultPanel;


  private javax.swing.JTextArea txtLogTextArea;
  private javax.swing.JComboBox<String> igNameComboBox;
  private javax.swing.JToolBar mainToolBar;
  private IniFile ini;

  private BackgroundPublisherTask task;
  private StringBuilder fullLog = new StringBuilder();
  private String qa;
  
  /**
   * Creates new form IGPublisherFrame
   * @throws IOException 
   */
  public IGPublisherFrame() throws IOException {
    ini = new IniFile(Utilities.path(System.getProperty("user.home"), "fhir-ig.ini"));

    createComponents();
    createLayout();
  }

  @SuppressWarnings("unchecked")
  private void createLayout() {

    setTitle("FHIR Implementation Guide Publisher");
    setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/favicon.ico")));
    setBounds(100, 100, 785, 449);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        frameClose();
      }
    });

    optionsPanel = new javax.swing.JPanel();

    javax.swing.GroupLayout optionsPanelLayout = new javax.swing.GroupLayout(optionsPanel );
    optionsPanel.setLayout(optionsPanelLayout);

    optionsPanelLayout.setHorizontalGroup(
            optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsPanelLayout.createSequentialGroup()
                            .addContainerGap()
                            .addComponent(noValidateCheckbox)
                            .addComponent(noNarrativeCheckbox)
                            .addComponent(noSushiCheckbox)
                            .addComponent(debugCheckbox)
                            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );
    optionsPanelLayout.setVerticalGroup(
            optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsPanelLayout.createParallelGroup()
                            .addComponent(noValidateCheckbox)
                            .addComponent(noNarrativeCheckbox)
                            .addComponent(noSushiCheckbox)
                            .addComponent(debugCheckbox)
                            .addGap(0, 13, Short.MAX_VALUE))
    );



    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

    mainToolBar = new javax.swing.JToolBar();
    mainToolBar.setRollover(true);
    mainToolBar.setFocusable(false);

    mainToolBar.add(executeButton);
    mainToolBar.add(chooseIGButton);
    mainToolBar.add(igNameComboBox);


    resultPanel = new javax.swing.JPanel();
    javax.swing.GroupLayout resultPanelLayout = new javax.swing.GroupLayout(resultPanel);
    resultPanel.setLayout(resultPanelLayout);
    resultPanelLayout.setHorizontalGroup(
        resultPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(resultPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addComponent(debugSummaryButton)
            .addComponent(viewQAButton)
            .addComponent(viewIgButton)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    resultPanelLayout.setVerticalGroup(
        resultPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(resultPanelLayout.createParallelGroup()
            .addComponent(debugSummaryButton)
            .addComponent(viewQAButton)
            .addComponent(viewIgButton)
            .addGap(0, 13, Short.MAX_VALUE))
        );


    JScrollPane logScrollPane = new JScrollPane(txtLogTextArea);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
    getContentPane().setLayout(layout);

    layout.setHorizontalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(mainToolBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addComponent(optionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(resultPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(logScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 627, Short.MAX_VALUE)
        );
    layout.setVerticalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
            .addComponent(mainToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(optionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(logScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 175, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(resultPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

    pack();
    if (ini.getProperties("layout") != null && ini.getProperties("layout").containsKey("X")) {
      setLocation(ini.getIntegerProperty("layout", "X"), ini.getIntegerProperty("layout", "Y")); 
      setSize(ini.getIntegerProperty("layout", "W"), ini.getIntegerProperty("layout", "H")); 
    }

  }

  private void createComponents() {
    txtLogTextArea = createTxLogTextArea();

    noNarrativeCheckbox = new JCheckBox("no-narrative");
    noValidateCheckbox = new JCheckBox("no-validate");
    noSushiCheckbox = new JCheckBox("no-sushi");
    debugCheckbox = new JCheckBox("debug");

    executeButton = createExecuteButton();
    chooseIGButton = createChooseIGButton();
    igNameComboBox = createIgNameComboBox();

    debugSummaryButton = createDebugSumaryButton();
    viewQAButton = createViewQAButton();
    viewIgButton = createViewIGButton();
  }

  private JComboBox<String> createIgNameComboBox() {
    JComboBox igNameComboBox = new javax.swing.JComboBox<String>();
    if (ini.getProperties("igs") != null && ini.getProperties("igs").containsKey("selected")) {
      for (int i = 0; i < ini.getIntegerProperty("igs", "count"); i++)
        igNameComboBox.addItem(ini.getStringProperty("igs", "file"+Integer.toString(i)));
      igNameComboBox.setSelectedIndex(ini.getIntegerProperty("igs", "selected"));
    }
    igNameComboBox.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        changeIGName(evt);
      }
    });
    return igNameComboBox;
  }

  private JButton createChooseIGButton() {
    JButton chooseIGButton = new javax.swing.JButton();
    chooseIGButton.setFocusable(false);
    chooseIGButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    chooseIGButton.setLabel("Choose");
    chooseIGButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    chooseIGButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnChooseClick(evt);
      }
    });
    return chooseIGButton;
  }

  private JButton createViewQAButton() {
    JButton viewQAButton = new javax.swing.JButton();
    viewQAButton.setText("View QA");
    viewQAButton.setEnabled(false);
    viewQAButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnQAClick(evt);
      }
    });
    return viewQAButton;
  }

  private JButton createViewIGButton() {
    JButton viewIgButton =new javax.swing.JButton();
    viewIgButton.setText("View IG");
    viewIgButton.setEnabled(false);
    viewIgButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnIGClick(evt);
      }
    });
    return viewIgButton;
  }

  private JTextArea createTxLogTextArea() {
    JTextArea txtLogTextArea = new javax.swing.JTextArea();
    txtLogTextArea.setColumns(20);
    txtLogTextArea.setRows(5);
    txtLogTextArea.setEditable(false);
    txtLogTextArea.getCaret().setVisible(false);
    return txtLogTextArea;
  }

  private JButton createDebugSumaryButton() {
    JButton button = new javax.swing.JButton();
    button.setText("Debug Summary");
    button.setEnabled(false);
    button.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnGetHelpClick(evt);
      }
    });
    return button;
  }

  private JButton createExecuteButton() {
    JButton executeButton = new javax.swing.JButton();
    executeButton.setFocusable(false);
    executeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    executeButton.setLabel("Execute");
    executeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    executeButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnExecuteClick(evt);
      }
    });
    return executeButton;
  }


  private void btnChooseClick(java.awt.event.ActionEvent evt) {                                         
    JFileChooser igFileChooser = new JFileChooser();
    igFileChooser.setFileFilter(new FileNameExtensionFilter("IG ini file or IG Directory", "ini"));
    igFileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    if (igNameComboBox.getSelectedItem() != null)
      igFileChooser.setCurrentDirectory(new File(Utilities.getDirectoryForFile((String) igNameComboBox.getSelectedItem())));
    if (igFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      int index = -1;

      String selectedFile = igFileChooser.getSelectedFile().isDirectory()
              ? igFileChooser.getSelectedFile().getAbsolutePath() + File.separatorChar + "ig.ini"
              : igFileChooser.getSelectedFile().getAbsolutePath();

      for (int i = 0; i < igNameComboBox.getItemCount(); i++) {
        if (selectedFile.equals(igNameComboBox.getItemAt(i)))
          index = i;;
      }
      if (index == -1) {
        index = ini.getProperties("igs") == null ? 0 : ini.getIntegerProperty("igs", "count");
        ini.setStringProperty("igs", "file"+Integer.toString(index), selectedFile, null);
        ini.setIntegerProperty("igs", "count", index+1, null);
        igNameComboBox.addItem(ini.getStringProperty("igs", "file"+Integer.toString(index)));
      }
      ini.setIntegerProperty("igs", "selected", index, null);
      igNameComboBox.setSelectedIndex(ini.getIntegerProperty("igs", "selected"));
    }
  } 

  private void changeIGName(java.awt.event.ActionEvent evt) {
    int index = igNameComboBox.getSelectedIndex();
    ini.setIntegerProperty("igs", "selected", index, null);
  }                                          

  protected void frameClose() {
    ini.setIntegerProperty("layout", "X", getX(), null); 
    ini.setIntegerProperty("layout", "Y", getY(), null); 
    ini.setIntegerProperty("layout", "W", getWidth(), null); 
    ini.setIntegerProperty("layout", "H", getHeight(), null); 
    ini.save();    
  }

  // ------ Execcution ------------------------------------------------------------------------------------------

  public class BackgroundPublisherTask extends SwingWorker<String, String> implements ILoggingService  {

    
    @Override
    public String doInBackground() {
      qa = null;
      Publisher pu = new Publisher();
      pu.setConfigFile((String) igNameComboBox.getSelectedItem());
      pu.setLogger(this);
      pu.setCacheOption(CacheOption.LEAVE);
      try {
        pu.execute();
        qa = pu.getQAFile();
      } catch (Exception e) {
        logMessage("Error : "+e.getMessage());
        for (StackTraceElement m : e.getStackTrace()) 
          logMessage("   "+m.toString());
      } 
      return "Finished";
    }

    @Override
    public void logMessage(String msg) {
      publish(msg);
    }

    @Override
    public void logDebugMessage(LogCategory category, String msg) {
      publish(LOG_PREFIX+msg);
      
    }

    @Override
    protected void process(List<String> msgs) {
      for (String msg : msgs) {
        if (msg.startsWith(LOG_PREFIX)) {
          fullLog.append(msg.substring(LOG_PREFIX.length())+"\r\n");
        } else {
          txtLogTextArea.append(msg+"\r\n");
          fullLog.append(msg+"\r\n");
        }
      }
      txtLogTextArea.setCaretPosition(txtLogTextArea.getText().length() - 1);
    }

    @Override
    protected void done() {
      executeButton.setEnabled(true);
      chooseIGButton.setEnabled(true);
      igNameComboBox.setEnabled(true);
      debugSummaryButton.setEnabled(true);
      viewQAButton.setEnabled(true);
      viewIgButton.setEnabled(true);
      executeButton.setLabel("Execute");
    }

    @Override
    public boolean isDebugLogging() {
      return false;
    }


  }

  private void btnExecuteClick(java.awt.event.ActionEvent evt) {
    executeButton.setEnabled(false);
    chooseIGButton.setEnabled(false);
    igNameComboBox.setEnabled(false);
    debugSummaryButton.setEnabled(false);
    viewQAButton.setEnabled(false);
    viewIgButton.setEnabled(false);
    executeButton.setLabel("Running");
    txtLogTextArea.setText("");
    fullLog.setLength(0);
    task = new BackgroundPublisherTask();
    task.execute();
  }

  private String folder() {
    return Utilities.getDirectoryForFile((String) igNameComboBox.getSelectedItem());
  }
  
  protected void btnQAClick(ActionEvent evt) {
    try {
      String path = Utilities.path(folder(), "output", "qa.html");
      openFile(path);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private void openFile(String url) throws IOException {
    File htmlFile = new File(url);
    Desktop.getDesktop().browse(htmlFile.toURI());  
  }

  protected void btnIGClick(ActionEvent evt) {
    try {
      String path = Utilities.path(folder(), "output", "index.html");
      openFile(path);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  protected void btnGetHelpClick(ActionEvent evt) {
    try {
      String text = Publisher.buildReport((String) igNameComboBox.getSelectedItem(), null, fullLog.toString(), qa == null ? null : Utilities.changeFileExt(qa, ".txt"), FhirSettings.getTxFhirProduction());
      StringSelection stringSelection = new StringSelection(text);
      Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
      clpbrd.setContents(stringSelection, null);
      JOptionPane.showMessageDialog(this, "Report copied to clipboard. Now paste it into an email to grahame@hl7.org");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
      e.printStackTrace();
    }
  }


}
