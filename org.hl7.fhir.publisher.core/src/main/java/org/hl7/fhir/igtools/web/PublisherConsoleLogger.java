package org.hl7.fhir.igtools.web;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

public class PublisherConsoleLogger {
  private FileOutputStream file;
  private PrintStream previousOut;
  private PrintStream previousErr;
  private boolean capturing;
  private String filename;

  public void start(String filename) throws FileNotFoundException {
    if (capturing) {
      return;
    }

    this.filename = filename;
    capturing = true;
    previousOut = System.out;      
    previousErr = System.err;      
    file = new FileOutputStream(filename);

    OutputStream outputStreamCombinerOut = new OutputStreamCombiner(Arrays.asList(previousOut, file)); 
    OutputStream outputStreamCombinerErr = new OutputStreamCombiner(Arrays.asList(previousErr, file)); 
    PrintStream customOut = new PrintStream(outputStreamCombinerOut);
    PrintStream customErr = new PrintStream(outputStreamCombinerErr);

    System.setOut(customOut);
    System.setErr(customErr);
  }

  public void stop() throws IOException {
    if (capturing) {

      System.setOut(previousOut);
      System.setErr(previousErr);

      file.close();
      file = null;
      previousOut = null;
      previousErr = null;
      capturing = false;
    }
  }

  public String getFilename() {
    return filename;
  }

  private static class OutputStreamCombiner extends OutputStream {
    private List<OutputStream> outputStreams;

    public OutputStreamCombiner(List<OutputStream> outputStreams) {
      this.outputStreams = outputStreams;
    }

    public void write(int b) throws IOException {
      for (OutputStream os : outputStreams) {
        os.write(b);
      }
    }

    public void flush() throws IOException {
      for (OutputStream os : outputStreams) {
        os.flush();
      }
    }

    public void close() throws IOException {
      for (OutputStream os : outputStreams) {
        os.close();
      }
    }
  }

  public boolean started() {
    return filename != null;
  }
}
