package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Works out whether some output is smaller than a limit, writing only as much of it as it takes to
 * find out.
 */
class OutputSizeCheck {

  /** Writes the output being measured. */
  interface Output {
    void writeTo(OutputStream stream) throws IOException;
  }

  private static class LimitReached extends IOException {
    private static final long serialVersionUID = 1L;
  }

  /** Counts the bytes written to it, and stops the writer once there are at least {@code limit}. */
  private static class CountingStream extends OutputStream {
    private final int limit;
    private long count;
    private boolean full;

    CountingStream(int limit) {
      this.limit = limit;
    }

    private void add(int n) throws LimitReached {
      count += n;
      if (count >= limit) {
        full = true;
        throw new LimitReached();
      }
    }

    @Override
    public void write(int b) throws IOException {
      add(1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      add(len);
    }
  }

  /**
   * @return true if {@code output} writes fewer than {@code limit} bytes. Once it has written
   *   {@code limit} bytes, the write that took it there fails and the rest isn't written.
   */
  static boolean isSmallerThan(Output output, int limit) throws IOException {
    CountingStream stream = new CountingStream(limit);
    try {
      output.writeTo(stream);
    } catch (IOException | RuntimeException e) {
      if (stream.full) {
        // the answer is known, whatever the writer made of being stopped
        return false;
      }
      throw e;
    }
    return stream.count < limit;
  }
}
