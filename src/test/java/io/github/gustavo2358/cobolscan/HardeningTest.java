package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class HardeningTest extends ProgramsTest {
  @Test
  void qualificationAndSubscripts() throws Exception {
    var r =
        scan(
            "MOVE 'ONE' TO P OF BLOCK-A. MOVE P IN BLOCK-A TO Q(IX). CALL Q(3). EXEC CICS LINK"
                + " PROGRAM(P OF BLOCK-A) END-EXEC.");
    assertEquals(Set.of("ONE"), r.programs);
    assertFalse(r.programResolutionIncomplete);
  }

  @Test
  void redefinesBothDirections() throws Exception {
    var r =
        scan(
            "01 ORIGINAL PIC X(8) VALUE 'ONE'.\n"
                + "01 ALIAS REDEFINES ORIGINAL PIC X(8).\n"
                + "PROCEDURE DIVISION. MOVE 'TWO' TO ALIAS. CALL ORIGINAL. CALL ALIAS.");
    assertEquals(Set.of("ONE", "TWO"), r.programs);
  }

  @Test
  void staticSliceAndDynamicSliceRemainder() throws Exception {
    var r = scan("MOVE 'PRE-PGM00001-SUFFIX' TO BLOCK-X. MOVE BLOCK-X(5:8) TO P. CALL P.");
    assertEquals(Set.of("PGM00001"), r.programs);
    assertFalse(r.programResolutionIncomplete);
    assertTrue(scan("MOVE 'ABCDE' TO X. CALL X(I:2).").programResolutionIncomplete);
  }

  @Test
  void stringCartesianValuesAndDelimiters() throws Exception {
    var r =
        scan(
            "MOVE 'ABC' TO PREFIX. MOVE 'XYZ' TO PREFIX. MOVE '00001' TO SUFFIX. STRING PREFIX"
                + " SUFFIX DELIMITED BY SIZE INTO P END-STRING. CALL P.");
    assertEquals(Set.of("ABC00001", "XYZ00001"), r.programs);
    var delimited =
        scan(
            "STRING 'ABC STOP' DELIMITED BY SPACE '00001' DELIMITED BY SIZE INTO P END-STRING. CALL"
                + " P.");
    assertEquals(Set.of("ABC00001"), delimited.programs);
  }

  @Test
  void transformsCycleAndPartialWritesTerminateAndStayIncomplete() throws Exception {
    var r = scan("MOVE 'A' TO X. STRING X 'B' INTO X END-STRING. CALL X.");
    assertTrue(r.programs.contains("A"));
    assertTrue(r.programResolutionIncomplete);
    assertTrue(scan("MOVE 'KNOWN' TO X. MOVE 'A' TO X(1:1). CALL X.").programResolutionIncomplete);
  }
}
