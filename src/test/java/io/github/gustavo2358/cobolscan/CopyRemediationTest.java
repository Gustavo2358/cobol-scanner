package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gustavo2358.cobolscan.output.ScanResult;
import io.github.gustavo2358.cobolscan.scan.Lexer;
import io.github.gustavo2358.cobolscan.source.Normalizer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class CopyRemediationTest {
  @TempDir Path dir;

  private ScanResult scan(String text, String format) throws Exception {
    Path p = dir.resolve("ROOT.cbl");
    Files.writeString(p, text);
    Options o = new Options();
    o.format = format;
    o.copyDirs.add(dir);
    return new Scanner(o).scan(p, "ROOT.cbl");
  }

  static Stream<Arguments> comments() {
    return Stream.of(
        Arguments.of("fixed-star", "fixed", "000100*    COPY FAKECPY.\n000200     COPY REALCPY.\n"),
        Arguments.of(
            "fixed-slash", "fixed", "000100/    COPY FAKECPY.\n000200     COPY REALCPY.\n"),
        Arguments.of("free", "free", "*> COPY FAKECPY.\nCOPY REALCPY.\n"),
        Arguments.of("literal", "free", "DISPLAY 'COPY FAKECPY.'.\nCOPY REALCPY.\n"),
        Arguments.of(
            "fixed-inline",
            "fixed",
            "000100     MOVE A TO B. *> COPY FAKECPY.\n000200     COPY REALCPY.\n"),
        Arguments.of("free-inline", "free", "MOVE A TO B. *> COPY FAKECPY.\nCOPY REALCPY.\n"),
        Arguments.of("fixed-read-as-free", "free", "000100*    COPY FAKECPY.\nCOPY REALCPY.\n"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("comments")
  void commentsNeverReachCopyLookup(String name, String format, String body) throws Exception {
    Files.writeString(dir.resolve("REALCPY.cpy"), "");
    // Test absent AND existing fake member: absence must not diagnose, presence must not expand.
    for (boolean exists : List.of(false, true)) {
      if (exists) Files.writeString(dir.resolve("FAKECPY.cpy"), "       CALL 'INJECTED'.\n");
      var r = scan(body, format);
      assertEquals(Set.of("REALCPY"), r.copybooks, name);
      assertTrue(r.programs.isEmpty());
      assertEquals("OK", r.scanStatus, r.diagnostics.toString());
      assertTrue(r.diagnostics.isEmpty());
      assertFalse(r.programResolutionIncomplete);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"comment-entry.cbl", "early-floating-comment.cbl"})
  void reducedCommentCasesAreRemovedBeforeCopyDetection(String fixture) throws Exception {
    Files.writeString(dir.resolve("REALCPY.cpy"), "");
    String body = Files.readString(Path.of("src/test/resources/fixtures/remediation", fixture));
    ScanResult normalized = new ScanResult(fixture);
    var tokens = Lexer.lex(Normalizer.normalize(body, "fixed", normalized));
    assertFalse(
        tokens.stream().anyMatch(t -> t.is("FAKECPY")), "comment survived normalization/lexing");
    var r = scan(body, "fixed");
    assertEquals(Set.of("REALCPY"), r.copybooks);
    assertTrue(r.programs.isEmpty());
    assertEquals("OK", r.scanStatus, r.diagnostics.toString());
  }

  @Test
  void commentEntryEndsAtAreaAAndDoesNotHideRealCode() throws Exception {
    var r =
        scan(
            "       IDENTIFICATION DIVISION.\n"
                + "       PROGRAM-ID. DEMO.\n"
                + "       AUTHOR. COPY NOPE.\n"
                + "           COMMENT HAS 'UNBALANCED QUOTE\n"
                + "           COPY ALSO-NOPE.\n"
                + "       PROCEDURE DIVISION.\n"
                + "           CALL 'REAL'.\n",
            "fixed");
    assertEquals(Set.of("REAL"), r.programs);
    assertTrue(r.copybooks.isEmpty());
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void nestedReplacementReachesGrandchild() {
    Options o = new Options();
    o.format = "free";
    var r =
        new Scanner(o)
            .scan(
                Path.of("src/test/resources/fixtures/remediation/nested-copy/ROOT.cbl"),
                "ROOT.cbl");
    assertEquals(Set.of("SUB0001"), r.programs);
    assertEquals(Set.of("A", "B"), r.copybooks);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void contextualReplacementDoesNotLeakThroughRawCache() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B.");
    Files.writeString(dir.resolve("B.cpy"), "MOVE TARGET TO P. CALL P.");
    var r =
        scan(
            "COPY A REPLACING ==TARGET== BY =='ONE'==. COPY A REPLACING ==TARGET== BY =='TWO'==.",
            "free");
    assertEquals(Set.of("ONE", "TWO"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void localReplacementWorksBelowUnmodifiedParent() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B REPLACING ==TARGET== BY =='LOCAL'==.");
    Files.writeString(dir.resolve("B.cpy"), "MOVE TARGET TO P. CALL P.");
    var r = scan("COPY A.", "free");
    assertEquals(Set.of("LOCAL"), r.programs);
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void twoReplacementClausesInOneChainAreExplicitlyPartial() throws Exception {
    Files.writeString(
        dir.resolve("A.cpy"), "CALL 'KNOWN'. COPY B REPLACING ==TARGET== BY =='LOCAL'==.");
    Files.writeString(dir.resolve("B.cpy"), "MOVE TARGET TO P. CALL P.");
    var r = scan("COPY A REPLACING ==TARGET== BY =='OUTER'==.", "free");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertEquals("PARTIAL", r.scanStatus);
    assertTrue(r.programResolutionIncomplete);
    assertTrue(
        r.diagnostics.stream().anyMatch(d -> d.contains("REPLACING") && d.contains("nested")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OF", "IN"})
  void libraryQualifierCannotChooseFirstRoot(String qualifier) throws Exception {
    Path lib1 = Files.createDirectory(dir.resolve("LIB1")),
        lib2 = Files.createDirectory(dir.resolve("LIB2"));
    Files.writeString(lib1.resolve("FOO.cpy"), "CALL 'WRONG'.");
    Files.writeString(lib2.resolve("FOO.cpy"), "CALL 'RIGHT'.");
    Path p = dir.resolve("ROOT.cbl");
    Files.writeString(p, "COPY FOO " + qualifier + " LIB2. CALL 'KNOWN'.");
    Options o = new Options();
    o.format = "free";
    o.copyDirs.addAll(List.of(lib1, lib2));
    var r = new Scanner(o).scan(p, "ROOT.cbl");
    assertEquals(Set.of("FOO"), r.copybooks);
    assertEquals(Set.of("KNOWN"), r.programs);
    assertEquals("PARTIAL", r.scanStatus);
    assertTrue(r.programResolutionIncomplete);
    assertTrue(r.diagnostics.stream().anyMatch(d -> d.contains("qualifier") && d.contains("LIB2")));
  }

  @Test
  void inheritedReplacementStillChargesGrowthBudget() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B.");
    Files.writeString(dir.resolve("B.cpy"), "X ".repeat(100));
    Path p = dir.resolve("ROOT.cbl");
    Files.writeString(p, "COPY A REPLACING ==X== BY ==" + "Y ".repeat(100) + "==. CALL 'KNOWN'.");
    Options o = new Options();
    o.format = "free";
    o.maxSourceBytes = 1000;
    var r = new Scanner(o).scan(p, "ROOT.cbl");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertEquals("PARTIAL", r.scanStatus);
    assertTrue(r.programResolutionIncomplete);
    assertTrue(r.diagnostics.stream().anyMatch(d -> d.contains("budget")));
  }

  @Test
  void inheritedContextStillDetectsIncludeCycles() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B.");
    Files.writeString(dir.resolve("B.cpy"), "MOVE TARGET TO P. CALL P. COPY A.");
    var r = scan("COPY A REPLACING ==TARGET== BY =='ONE'==.", "free");
    assertEquals(Set.of("ONE"), r.programs);
    assertEquals("PARTIAL", r.scanStatus);
    assertTrue(r.diagnostics.stream().anyMatch(d -> d.contains("cycle")));
  }

  @Test
  void inheritedContextStillHonorsIncludeDepth() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B.");
    Files.writeString(dir.resolve("B.cpy"), "CALL 'TOO-DEEP'.");
    Path p = dir.resolve("ROOT.cbl");
    Files.writeString(p, "COPY A REPLACING ==X== BY ==Y==. CALL 'KNOWN'.");
    Options o = new Options();
    o.format = "free";
    o.maxIncludeDepth = 2;
    var r = new Scanner(o).scan(p, "ROOT.cbl");
    assertEquals(Set.of("KNOWN"), r.programs);
    assertEquals("PARTIAL", r.scanStatus);
    assertTrue(r.diagnostics.stream().anyMatch(d -> d.contains("depth budget")));
  }
}
