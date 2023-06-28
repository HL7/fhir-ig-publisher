package org.hl7.fhir.igtools.publisher;

import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link FSHRunner} class.
 *
 * @author Quentin Ligier
 **/
class FSHRunnerTest {

    @Test
    void testMySushiHandler() throws IOException {

        final StringBuilder stringBuilder = new StringBuilder(512);
        final FSHRunner.MySushiHandler mySushiHandler = getMySushiHandler(stringBuilder);
        for (final int codePoint : "1234".codePoints().toArray()) {
            mySushiHandler.write(codePoint);
        }
        assertEquals("1234", mySushiHandler.getBufferString());
        assertEquals(0, stringBuilder.length());
        mySushiHandler.write(10); // EOL
        assertEquals("", mySushiHandler.getBufferString());
        assertEquals("Sushi: 1234", stringBuilder.toString());
    }

    @Nonnull
    private static FSHRunner.MySushiHandler getMySushiHandler(StringBuilder stringBuilder) {
        final Consumer<String> stringConsumer = stringBuilder::append;
        return new FSHRunner.MySushiHandler(stringConsumer);
    }

    @Test
    public void testMySushiHandlerLongStrings() throws IOException {
        final StringBuilder stringBuilder = new StringBuilder(512);
        final FSHRunner.MySushiHandler mySushiHandler = getMySushiHandler(stringBuilder);
        // Test long strings, larger than the initial buffer size
        final int largeLength = 500;
        for (int i = 0; i < largeLength; ++i) {
            mySushiHandler.write('a');
        }
        assertEquals(largeLength, mySushiHandler.getBufferString().length());
        assertEquals(0, stringBuilder.length());
        mySushiHandler.write(10); // EOL
        assertEquals("", mySushiHandler.getBufferString());
        assertEquals(largeLength + 7, stringBuilder.toString().length());
    }

    @Test
    void testMySushiHandlerErrors() throws IOException {

        final StringBuilder stringBuilder = new StringBuilder(512);
        final FSHRunner.MySushiHandler mySushiHandler = getMySushiHandler(stringBuilder);
        for (final int codePoint : "1234".codePoints().toArray()) {
            mySushiHandler.write(codePoint);
        }
        mySushiHandler.write(10); // EOL
        for (final int codePoint : "  Errors: 13".codePoints().toArray()) {
            mySushiHandler.write(codePoint);
        }
        mySushiHandler.write(10); // EOL
        assertEquals("", mySushiHandler.getBufferString());
        assertEquals("Sushi: 1234Sushi:   Errors: 13", stringBuilder.toString());
        assertEquals(13, mySushiHandler.getErrorCount());
    }
}
