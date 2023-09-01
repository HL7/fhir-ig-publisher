package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


public class IGPack2NpmConvertorTest {
    public static final String ZIP_NORMAL_ZIP = "zip-normal.zip";
    public static final String ZIP_SLIP_ZIP = "zip-slip.zip";

    public static final String ZIP_SLIP_2_ZIP = "zip-slip-2.zip";

    public static final String ZIP_SLIP_WIN_ZIP = "zip-slip-win.zip";
    Path tempDir;

    @Test
    public void testNormalZip() throws IOException {
        InputStream normalInputStream = getResourceAsInputStream(this.getClass(),  "zip", ZIP_NORMAL_ZIP);

        IGPack2NpmConvertor convertor = new IGPack2NpmConvertor();
        Map<String, byte[]> map = convertor.loadZip( normalInputStream);


        String actualContent = new String(map.get("zip-normal/depth1/test.txt"), StandardCharsets.UTF_8);
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
                        "Entry with an illegal name: ../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_2_ZIP),
                        "Entry with an illegal name: child/../../evil.txt"),
                Arguments.of(
                        getResourceAsInputStream(PublisherTests.class,  "zip", ZIP_SLIP_WIN_ZIP),
                        "Entry with an illegal name: ../evil.txt")
        );
    }

    @ParameterizedTest
    @MethodSource("getParams")
    public void test(InputStream inputStream, String expectedError){
        IOException thrown = Assertions.assertThrows(IOException.class, () -> {

            IGPack2NpmConvertor convertor = new IGPack2NpmConvertor();
            convertor.loadZip( inputStream );
            //Code under test
        });
        assertNotNull(thrown);
        assertEquals(expectedError, thrown.getMessage());
    }
}
