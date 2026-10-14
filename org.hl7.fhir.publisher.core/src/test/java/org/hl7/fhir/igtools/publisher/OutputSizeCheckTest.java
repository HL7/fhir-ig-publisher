package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutputSizeCheckTest {

  private static final int LIMIT = 16384;

  private enum Style {
    BYTES, BLOCKS, WRITER
  }

  /** Writes {@code size} bytes in the given style, and counts how many it got through. */
  private static class Fixture implements OutputSizeCheck.Output {
    private final int size;
    private final Style style;
    private int written;

    Fixture(int size, Style style) {
      this.size = size;
      this.style = style;
    }

    @Override
    public void writeTo(OutputStream stream) throws IOException {
      switch (style) {
      case BYTES:
        for (int i = 0; i < size; i++) {
          stream.write('a');
          written++;
        }
        break;
      case BLOCKS:
        byte[] block = new byte[1000];
        Arrays.fill(block, (byte) 'a');
        for (int i = 0; i < size; i += block.length) {
          int n = Math.min(block.length, size - i);
          stream.write(block, 0, n);
          written += n;
        }
        break;
      case WRITER:
        // the way XMLWriter writes: characters through a buffered writer, flushed at the end
        Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
          w.write('a');
          written++;
        }
        w.flush();
        break;
      }
    }
  }

  static Stream<Arguments> cases() {
    return Arrays.stream(Style.values()).flatMap(style -> Stream.of(0, 1, LIMIT - 1, LIMIT, LIMIT + 1, 10 * LIMIT)
      .map(size -> Arguments.of(style, size)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("cases")
  void sameAnswerAsMeasuringAllTheOutput(Style style, int size) throws IOException {
    Fixture output = new Fixture(size, style);
    assertEquals(size < LIMIT, OutputSizeCheck.isSmallerThan(output, LIMIT));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("styles")
  void stopsWritingOnceTheLimitIsReached(Style style) throws IOException {
    Fixture output = new Fixture(10 * LIMIT, style);
    OutputSizeCheck.isSmallerThan(output, LIMIT);
    // the writer's own buffer can hold up to 8 KB that hasn't reached the stream yet
    assertTrue(output.written < 2 * LIMIT, "wrote " + output.written + " bytes");
  }

  static Stream<Style> styles() {
    return Arrays.stream(Style.values());
  }

  @Test
  void writerThatWrapsTheStopStillCountsAsTooBig() throws IOException {
    OutputSizeCheck.Output output = stream -> {
      try {
        stream.write(new byte[LIMIT]);
      } catch (IOException e) {
        throw new IllegalStateException("wrapped", e);
      }
    };
    assertEquals(false, OutputSizeCheck.isSmallerThan(output, LIMIT));
  }

  @Test
  void otherFailuresArePassedOn() {
    IOException failure = new IOException("broken");
    OutputSizeCheck.Output output = stream -> {
      stream.write(new byte[10]);
      throw failure;
    };
    assertSame(failure, assertThrows(IOException.class, () -> OutputSizeCheck.isSmallerThan(output, LIMIT)));
  }
}
