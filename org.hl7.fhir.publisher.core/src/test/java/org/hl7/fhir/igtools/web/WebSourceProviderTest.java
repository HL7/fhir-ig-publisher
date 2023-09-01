package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.hl7.fhir.igtools.publisher.PublisherTests;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WebSourceProviderTest {
    public static final String ZIP_NORMAL_ZIP = "zip-normal-no-depth.zip";
    public static final String ZIP_SLIP_ZIP = "zip-slip.zip";

    public static final String ZIP_SLIP_2_ZIP = "zip-slip-2.zip";

    public static final String ZIP_SLIP_WIN_ZIP = "zip-slip-win.zip";
    Path tempDir;

    Path testTxtPath;
    private Path depth1Path;

    @BeforeAll
    public void beforeAll() throws IOException {
        tempDir = Files.createTempDirectory("web-source-provider-zip");
        testTxtPath = tempDir.resolve("test.txt");
        testTxtPath.toFile().createNewFile();

    }
    @Test
    public void testNormalZip() throws IOException {
        InputStream normalInputStream = getResourceAsInputStream(this.getClass(),  "zip", ZIP_NORMAL_ZIP);

        assertTrue(Files.exists(testTxtPath));

        WebSourceProvider.cleanZipTargets( tempDir, normalInputStream);

        assertFalse(Files.exists(testTxtPath));

    }

    static final String PATH_DELIMITER = "/";

    public static InputStream getResourceAsInputStream(Class<?> clazz, String... resourcePath) {
        return clazz.getClassLoader().getResourceAsStream(String.join(PATH_DELIMITER, resourcePath));
    }

    private static Stream<Arguments> getParams() {
        return Stream.of(
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_ZIP),
                        "Bad zip entry: ../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_2_ZIP),
                        "Bad zip entry: child/../../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_WIN_ZIP),
                        "Bad zip entry: ../evil.txt")
        );
    }

    @ParameterizedTest
    @MethodSource("getParams")
    public void test(InputStream inputStream, String expectedError){
        IOException thrown = Assertions.assertThrows(IOException.class, () -> {

            WebSourceProvider.cleanZipTargets(tempDir, inputStream);
            //Code under test
        });
        assertNotNull(thrown);
        assertEquals(expectedError.replace('/', File.separatorChar), thrown.getMessage());
    }
}
