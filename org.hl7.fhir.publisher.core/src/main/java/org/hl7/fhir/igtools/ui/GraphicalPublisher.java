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


import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.igtools.publisher.CliParams;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.utilities.Utilities;

import javax.swing.*;
import java.awt.EventQueue;
import java.io.IOException;

public class GraphicalPublisher {

  public IGPublisherFrame frame;

  @Getter
  @Setter
  protected String configFile;

  /**
   * Launch the application.
   */
  public static void main(String[] args) {
    launchUI(args);
  }

  public static void launchUI(String[] args) {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          GraphicalPublisher graphicalPublisher = new GraphicalPublisher();

          final String configFile = CliParams.hasNamedParam(args, "-ig")
                  ? Publisher.determineActualIG(CliParams.getNamedParam(args, "-ig"), Publisher.IGBuildMode.PUBLICATION)
                  : null;

          graphicalPublisher.frame.setVisible(true);

          if (!Utilities.noString(configFile)) {
            graphicalPublisher.frame.runIGFromCLI(configFile);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Create the application.
   * @throws IOException 
   */
  public GraphicalPublisher() throws IOException {
    initialize();
  }

  /**
   * Initialize the contents of the frame.
   * @throws IOException 
   */
  private void initialize() throws IOException {
    frame = new IGPublisherFrame();
  }

}
