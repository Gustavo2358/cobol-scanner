package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class CorpusRegressionTest extends ProgramsTest {
  // Reduced from CBACT01C/COTRTLIC: a dynamic length in unrelated data must not kill direct CALL.
  @Test
  void dynamicReferenceLengthDoesNotUnboxNull() throws Exception {
    var r = scan("MOVE BUFFER(1:WS-LENGTH) TO UNUSED. CALL 'KNOWN'.");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertNotEquals("ERROR", r.scanStatus);
    assertTrue(scan("MOVE BUFFER(1:WS-LENGTH) TO P. CALL P.").programResolutionIncomplete);
  }

  @Test
  void shortFixedLiteralContinuationIncludesSpacesToColumn72() {
    var r = new io.github.gustavo2358.cobolscan.output.ScanResult("fixed");
    String n =
        io.github.gustavo2358.cobolscan.source.Normalizer.normalize(
            "       CALL 'ABC\n      -    'DEF'.\n", "fixed", r);
    assertEquals(
        "ABC" + " ".repeat(56) + "DEF",
        io.github.gustavo2358.cobolscan.scan.Lexer.lex(n).get(1).value());
  }

  @Test
  void unsupportedArithmeticAndStringPointerKeepRemainder() throws Exception {
    assertTrue(scan("MOVE 'KNOWN' TO P. ADD 1 TO P. CALL P.").programResolutionIncomplete);
    assertTrue(
        scan("MOVE 'KNOWN' TO P. STRING 'X' INTO P POINTER PTR END-STRING. CALL P.")
            .programResolutionIncomplete);
  }
}
