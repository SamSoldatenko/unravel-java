package unravel.java;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;


class AppTest {

    @TempDir
    Path tempDir;

    private PrintStream oldOut;
    private ByteArrayOutputStream testOutput;

    @BeforeEach
    void redirectOut() throws Exception {
        oldOut = System.out;
        testOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOutput, true, UTF_8));
    }

    @AfterEach
    void restoreOut() throws Exception {
        System.setOut(oldOut);
    }

    @Test
    void mainShouldAnalizeFile() throws Exception {
        Path path = Files.createTempFile(tempDir, "empty", ".zip");
        ZipOutputStream emptyZip = new ZipOutputStream(Files.newOutputStream(path));
        emptyZip.close();

        App.main(new String[]{path.toString()});

        JsonObject json = Json.createReader(new ByteArrayInputStream(testOutput.toByteArray()))
                .readObject();
        assertEquals(0, json.get("units").asJsonArray().size());
    }
}
