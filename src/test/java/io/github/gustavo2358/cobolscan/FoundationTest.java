package io.github.gustavo2358.cobolscan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class FoundationTest {
    @TempDir Path dir;
    @Test void executableContractIsDeterministic() throws Exception {
        Path source = dir.resolve("empty.cbl"), out = dir.resolve("out.json"); Files.writeString(source, "       GOBACK.\n");
        assertEquals(0, Main.run("--source", source.toString(), "--output", out.toString()));
        String first = Files.readString(out); assertTrue(first.contains("\"schemaVersion\" : 1"));
        assertTrue(first.contains("\"scanStatus\" : \"OK\""));
        assertEquals(0, Main.run("--source", source.toString(), "--output", out.toString())); assertEquals(first, Files.readString(out));
    }
    @Test void protectsInput() throws Exception {
        Path p = dir.resolve("input.cbl"); Files.writeString(p, "original");
        assertEquals(2, Main.run("--source", p.toString(), "--output", p.toString())); assertEquals("original", Files.readString(p));
    }
}
