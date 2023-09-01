package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PublisherTests {

    public static final String ZIP_NORMAL_ZIP = "zip-normal.zip";
    public static final String ZIP_SLIP_ZIP = "zip-slip.zip";

    public static final String ZIP_SLIP_2_ZIP = "zip-slip-2.zip";

    public static final String ZIP_SLIP_WIN_ZIP = "zip-slip-win.zip";
    Path tempDir;

    @BeforeAll
    public void beforeAll() throws IOException {
        tempDir = Files.createTempDirectory("publisher-zip");
        tempDir.resolve("child").toFile().mkdir();
    }
    @Test
    public void testNormalZip() throws IOException {
            InputStream normalInputStream = getResourceAsInputStream(this.getClass(),  "zip", ZIP_NORMAL_ZIP);
            Publisher.unzipToDirectory( normalInputStream, tempDir.toFile().getAbsolutePath());

            Path expectedFilePath = tempDir.resolve("zip-normal").resolve("depth1").resolve("test.txt");
            String actualContent = Files.readString(expectedFilePath);
            assertEquals("dummy file content", actualContent);
        }

    static final String PATH_DELIMITER = "/";

    public static InputStream getResourceAsInputStream(Class<?> clazz, String... resourcePath) {
        return clazz.getClassLoader().getResourceAsStream(String.join(PATH_DELIMITER, resourcePath));
    }

    private static Stream<Arguments> getParams() {
        return Stream.of(
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_ZIP),
                        "../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_2_ZIP),
                        "child/../../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_WIN_ZIP),
                        "../evil.txt")
        );
    }

    @ParameterizedTest
    @MethodSource("getParams")
    public void test(InputStream inputStream, String expectedError){
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {

            Publisher.unzipToDirectory( inputStream, tempDir.toFile().getAbsolutePath());
            //Code under test
        });
        assertNotNull(thrown);
        assertTrue(thrown.getMessage().endsWith(expectedError.replace('/', File.separatorChar)));
    }
}
