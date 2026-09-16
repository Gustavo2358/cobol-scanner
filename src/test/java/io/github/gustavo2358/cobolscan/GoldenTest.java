package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
  @TempDir Path dir;

  @Test
  void fullSourceToExactJson() throws Exception {
    Path out = dir.resolve("result.json");
    assertEquals(
        0,
        Main.run(
            "--source", "src/test/resources/fixtures/complete.cbl", "--output", out.toString()));
    assertEquals(
        Files.readString(Path.of("src/test/resources/fixtures/complete.json")),
        Files.readString(out));
  }
}
