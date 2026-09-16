package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gustavo2358.cobolscan.fact.*;
import io.github.gustavo2358.cobolscan.resolve.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ValueTest extends ProgramsTest {
  @Test
  void manifesto() {
    Options o = new Options();
    var r =
        new Scanner(o).scan(Path.of("src/test/resources/fixtures/manifesto.cbl"), "manifesto.cbl");
    assertEquals(Set.of("SUB00001", "SUB00002"), r.programs);
    assertFalse(r.programs.contains("FIM"));
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void transitiveDiamondMultipleReceiversAndFlowInsensitive() throws Exception {
    var r =
        scan(
            "01 A PIC X VALUE 'ONE'.\n"
                + "PROCEDURE DIVISION. CALL Z. GOBACK. MOVE A TO B C. MOVE B TO Z. MOVE C TO Z. IF"
                + " IMPOSSIBLE MOVE 'TWO' TO Z END-IF. EXEC CICS XCTL PROGRAM(Z) END-EXEC.");
    assertEquals(Set.of("ONE", "TWO"), r.programs);
    assertFalse(r.programResolutionIncomplete);
  }

  @Test
  void longChainAndDemand() {
    ValueFacts f = new ValueFacts();
    f.add("V0", new Value.Literal("END"));
    for (int i = 1; i <= 10000; i++) f.add("V" + i, new Value.Ref("V" + (i - 1)));
    f.add("UNUSED", new Value.Literal("NOISE"));
    ValueResolver r = new ValueResolver(f);
    assertEquals(Set.of("END"), r.resolve(new Value.Ref("V10000")).values());
    assertEquals(10001, r.visitedCount());
    r.resolve(new Value.Ref("V10000"));
    assertEquals(10001, r.visitedCount());
  }

  @Test
  void cyclesPreserveAllAnchorsRegardlessOfSinkOrder() throws Exception {
    var r = scan("MOVE A TO B. MOVE B TO A. MOVE 'ONE' TO A. MOVE 'TWO' TO B. CALL A. CALL B.");
    assertEquals(Set.of("ONE", "TWO"), r.programs);
    assertTrue(scan("MOVE A TO B. MOVE B TO A. CALL A.").programResolutionIncomplete);
  }

  @Test
  void externalWritersKeepKnownCandidatesAndOpenRemainder() throws Exception {
    var r = scan("MOVE 'KNOWN' TO X. ACCEPT X. CALL X.");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertTrue(r.programResolutionIncomplete);
    assertTrue(scan("MOVE 'KNOWN' TO X. COMPUTE X = 3. CALL X.").programResolutionIncomplete);
  }

  @Test
  void metamorphicNoiseAndCase() throws Exception {
    String base = "MOVE 'ONE' TO A. MOVE A TO B. CALL B.";
    assertEquals(
        scan(base).programs,
        scan("DISPLAY 'FIM'. *> CALL 'NOPE'\n"
                + base.toLowerCase(Locale.ROOT).replace("'one'", "'ONE'").replace(" TO ", "\nTO "))
            .programs);
  }
}
