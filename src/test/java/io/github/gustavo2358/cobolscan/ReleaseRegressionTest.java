package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ReleaseRegressionTest extends ProgramsTest {
  @Test
  void commaSeparatedMoveReceivers() throws Exception {
    var r = scan("MOVE 'ONE' TO A, B. CALL B.");
    assertEquals(Set.of("ONE"), r.programs);
    assertFalse(r.programResolutionIncomplete);
  }

  @Test
  void sqlFunctionsAndForUpdateDoNotCreateTables() throws Exception {
    var r =
        scan(
            "EXEC SQL SELECT EXTRACT(YEAR FROM TS), SUBSTRING(X FROM POS FOR LEN) FROM T FOR UPDATE"
                + " OF COL END-EXEC.");
    assertEquals(Set.of("T"), r.tables);
  }

  @Test
  void copyReplacementGrowthConsumesBudget() throws Exception {
    Files.writeString(dir.resolve("M.cpy"), "X ".repeat(100));
    Path p = dir.resolve("root.cbl");
    Files.writeString(p, "COPY M REPLACING ==X== BY ==" + "Y ".repeat(100) + "==. CALL 'KNOWN'.");
    Options o = new Options();
    o.format = "free";
    o.maxSourceBytes = 1000;
    var r = new Scanner(o).scan(p, "root.cbl");
    assertEquals("PARTIAL", r.scanStatus);
    assertEquals(Set.of("KNOWN"), r.programs);
    assertTrue(r.programResolutionIncomplete);
  }

  @Test
  void allQualifiedConsumersUseSharedResolver() throws Exception {
    for (String sink :
        List.of(
            "CALL P OF G.",
            "EXEC CICS LINK PROGRAM(P OF G) END-EXEC.",
            "EXEC CICS XCTL PROGRAM(P OF G) END-EXEC.")) {
      var r = scan("MOVE 'ONE' TO P IN G. MOVE 'TWO' TO P OF G. " + sink);
      assertEquals(Set.of("ONE", "TWO"), r.programs);
      assertFalse(r.programResolutionIncomplete);
    }
  }
}
