package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WriterRemediationTest {
  @TempDir Path dir;

  private ScanResult scan(String text) throws Exception {
    Path p = dir.resolve("ROOT.cbl");
    Files.writeString(p, text);
    Options o = new Options();
    o.format = "free";
    return new Scanner(o).scan(p, "ROOT.cbl");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "READ F INTO P",
        "READ F NEXT RECORD INTO P",
        "READ F PREVIOUS RECORD INTO P",
        "READ F WITH NO LOCK INTO P"
      })
  void readReceiverStaysIncompleteAcrossClauses(String statement) throws Exception {
    var r = scan("01 P PIC X(8) VALUE 'KNOWN'.\nPROCEDURE DIVISION. " + statement + ". CALL P.");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertTrue(r.programResolutionIncomplete);
    assertEquals("PARTIAL", r.scanStatus);
  }

  @Test
  void setConditionWritesParentOnlyWhenSelected() throws Exception {
    var r =
        scan(
            "01 P PIC X(8) VALUE 'OLDPGM'.\n"
                + "88 TARGET-NEW VALUE 'NEWPGM'.\n"
                + "88 UNUSED VALUE 'NOISE'.\n"
                + "PROCEDURE DIVISION. SET TARGET-NEW TO TRUE. CALL P.");
    assertEquals(Set.of("OLDPGM", "NEWPGM"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void unselectedConditionsDoNotInitializeParent() throws Exception {
    var r =
        scan(
            "01 P PIC X(8).\n"
                + "88 TARGET-A VALUE 'PGMA'.\n"
                + "88 TARGET-B VALUE 'PGMB'.\n"
                + "PROCEDURE DIVISION. SET TARGET-A TO TRUE. CALL P.");
    assertEquals(Set.of("PGMA"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void unrelatedConditionLiteralIsNotAProgram() throws Exception {
    var r =
        scan(
            "01 FLAG PIC X.\n"
                + "88 OK-FLAG VALUE 'Y'.\n"
                + "01 P PIC X(8) VALUE 'REALPGM'.\n"
                + "PROCEDURE DIVISION. SET OK-FLAG TO TRUE. CALL P.");
    assertEquals(Set.of("REALPGM"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void multipleSetTargetsAndFirstValueSemantics() throws Exception {
    var r =
        scan(
            "01 P PIC X(8).\n"
                + "88 SEL-A VALUES 'FIRST' 'NOT-SELECTED'.\n"
                + "01 Q PIC X(8).\n"
                + "88 SEL-B VALUE 'SECOND'.\n"
                + "PROCEDURE DIVISION. SET SEL-A SEL-B TO TRUE. CALL P. CALL Q.");
    assertEquals(Set.of("FIRST", "SECOND"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void unsupportedSetFalseContaminatesParent() throws Exception {
    var r =
        scan(
            "01 P PIC X(8) VALUE 'OLD'.\n"
                + "88 SEL VALUE 'NEW' WHEN SET TO FALSE IS 'FALSEPGM'.\n"
                + "PROCEDURE DIVISION. SET SEL TO FALSE. CALL P.");
    assertTrue(r.programs.contains("OLD"));
    assertTrue(r.programResolutionIncomplete);
    assertEquals("PARTIAL", r.scanStatus);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "MOVE ALL 'A' TO P",
        "MOVE CORRESPONDING G TO H",
        "COMPUTE X P = 1",
        "COMPUTE X ROUNDED P = 1",
        "INITIALIZE X P",
        "INITIALIZE H",
        "SET X P TO NULL",
        "INSPECT X TALLYING P FOR ALL 'A'",
        "ADD 1 TO X ROUNDED P",
        "SUBTRACT 1 FROM X, P",
        "MULTIPLY 2 BY X GIVING Y, P",
        "DIVIDE X BY Y GIVING Z REMAINDER P",
        "UNSTRING X DELIMITED BY SPACE INTO P",
        "UNSTRING X INTO Y COUNT IN P",
        "UNSTRING X INTO Y DELIMITER IN P",
        "UNSTRING X INTO Y WITH POINTER P",
        "UNSTRING X INTO Y TALLYING IN P",
        "STRING 'A' INTO X WITH POINTER P",
        "MOVE X TO P(1:LEN)",
        "READ F INTO H",
        "MOVE X TO H",
        "MOVE 'A' TO H(1:1)"
      })
  void supportedWriterFamiliesCannotLeaveFalseCompleteResult(String statement) throws Exception {
    var r =
        scan(
            "01 G.\n05 P PIC X(8).\n01 H.\n05 P PIC X(8) VALUE 'KNOWN'.\nPROCEDURE DIVISION. "
                + statement
                + ". CALL P.");
    assertTrue(r.programs.contains("KNOWN"), r.programs.toString());
    assertTrue(r.programResolutionIncomplete, statement + " remained complete");
    assertEquals("PARTIAL", r.scanStatus);
  }

  @Test
  void valueAndStringKeywordLiteralsAreValuesNotStatementBoundaries() throws Exception {
    var r =
        scan(
            "01 P PIC X(8) VALUE 'OLD'.\n"
                + "PROCEDURE DIVISION. STRING 'MOVE' 'PGM' INTO P END-STRING. CALL P.");
    assertEquals(Set.of("OLD", "MOVEPGM"), r.programs);
  }

  @Test
  void unsupportedValueAllIsIncomplete() throws Exception {
    var r = scan("01 P PIC X(8) VALUE ALL 'A'.\nPROCEDURE DIVISION. CALL P.");
    assertTrue(r.programResolutionIncomplete);
    assertTrue(r.programs.isEmpty());
  }

  @Test
  void scanStopsBeforeNextStatementAndDoesNotPoisonUnrelatedSinks() throws Exception {
    var r =
        scan(
            "01 P PIC X(8) VALUE 'REAL'.\n"
                + "PROCEDURE DIVISION. READ F NEXT RECORD INTO X AT END DISPLAY 'FIM' END-READ"
                + " STRING 'A' INTO Y END-STRING CALL P.");
    assertEquals(Set.of("REAL"), r.programs);
    assertFalse(r.programResolutionIncomplete);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "01 G PIC X(8) VALUE 'KNOWN'.\n01 H REDEFINES G.\n05 P PIC X(8).",
        "01 G VALUE 'KNOWN'.\n05 P PIC X(8).",
        "01 G VALUE 'KNOWN'.\n05 H.\n10 P PIC X(8).",
        "01 G VALUE 'KNOWN'.\n05 H PIC X(8).\n05 P REDEFINES H PIC X(8)."
      })
  void childWritesContaminateContainingAndOverlappingSinks(String declarations) throws Exception {
    var r = scan(declarations + "\nPROCEDURE DIVISION. ACCEPT P. CALL G.");
    assertTrue(r.programs.contains("KNOWN"));
    assertTrue(r.programResolutionIncomplete);
    assertEquals("PARTIAL", r.scanStatus);
  }
}
