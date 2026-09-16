package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ProgramsTest {
  @TempDir Path dir;

  ScanResult scan(String text) throws Exception {
    Path p = dir.resolve("test.cbl");
    Files.writeString(p, text);
    Options o = new Options();
    o.format = "free";
    return new Scanner(o).scan(p, "test.cbl");
  }

  @Test
  void directAndVariableSinks() throws Exception {
    ScanResult r =
        scan(
            "CALL 'ONE' USING X. CALL \"TWO\". CALL WS-PGM. EXEC CICS LINK PROGRAM('THREE')"
                + " LENGTH(100) END-EXEC. EXEC CICS XCTL PROGRAM('FOUR') END-EXEC.");
    assertEquals(Set.of("ONE", "TWO", "THREE", "FOUR"), r.programs);
    assertTrue(r.programResolutionIncomplete);
  }

  @Test
  void lexicalNoiseCannotBecomePrograms() throws Exception {
    ScanResult r =
        scan(
            "DISPLAY 'CALL BAD'. *> CALL 'NOPE'\n"
                + "EXEC SQL SELECT 'CALL SQLBAD' FROM T END-EXEC. EXEC DLI CALL 'DLIBAD' END-EXEC."
                + " CALL 'REAL'.");
    assertEquals(Set.of("REAL"), r.programs);
    assertFalse(r.programResolutionIncomplete);
  }
}
