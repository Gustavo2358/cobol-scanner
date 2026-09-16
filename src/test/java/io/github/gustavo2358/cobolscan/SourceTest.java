package io.github.gustavo2358.cobolscan;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gustavo2358.cobolscan.output.*;
import io.github.gustavo2358.cobolscan.scan.*;
import io.github.gustavo2358.cobolscan.source.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SourceTest {
  @TempDir Path dir;

  List<Token> prepare(String text, ScanResult r) throws Exception {
    Path p = dir.resolve("main.cbl");
    Files.writeString(p, text);
    Options o = new Options();
    o.format = "free";
    o.copyDirs.add(dir);
    return new SourcePreparation(o).prepare(p, r);
  }

  @Test
  void expandsNestedAndContextualReplacementWithoutLosingExec() throws Exception {
    Files.writeString(dir.resolve("A.cpy"), "COPY B. EXEC CICS LINK PROGRAM('OLD') END-EXEC.");
    Files.writeString(dir.resolve("B.cpy"), "EXEC SQL SELECT * FROM T END-EXEC.");
    ScanResult r = new ScanResult("main");
    List<Token> t =
        prepare("COPY A REPLACING == 'OLD' == BY == 'ONE' ==. COPY A REPLACING 'OLD' BY 'TWO'.", r);
    assertEquals(Set.of("A", "B"), r.copybooks);
    assertEquals(2, t.stream().filter(x -> x.is("SQL")).count());
    assertEquals(
        Set.of("ONE", "TWO"),
        new TreeSet<>(
            t.stream().filter(x -> x.kind() == Token.Kind.STRING).map(Token::value).toList()));
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void stringsCommentsMissingAndCycles() throws Exception {
    Files.writeString(dir.resolve("LOOP.cpy"), "COPY LOOP.");
    ScanResult r = new ScanResult("main");
    List<Token> t =
        prepare("DISPLAY 'COPY PHANTOM.'. *> COPY NOPE.\nCOPY ABSENT. COPY LOOP. CALL 'AFTER'.", r);
    assertEquals(Set.of("ABSENT", "LOOP"), r.copybooks);
    assertTrue(t.stream().anyMatch(x -> x.value().equals("AFTER")));
    assertEquals("PARTIAL", r.scanStatus);
  }

  @Test
  void fixedAndLiteralContinuation() {
    ScanResult r = new ScanResult("fixed");
    String n =
        Normalizer.normalize(
            "000100 "
                + " ".repeat(56)
                + "CALL 'ABC\r\n000200-    '00001'.\r\n000300*CALL 'NO'.\r\n",
            "fixed",
            r);
    assertEquals("ABC00001", Lexer.lex(n).get(1).value());
    assertEquals("OK", r.scanStatus);
  }

  @Test
  void escapesAndSqlComments() {
    List<Token> t =
        Lexer.lex("DISPLAY 'CALL ''NO''' EXEC SQL SELECT * FROM A -- FROM FAKE\n END-EXEC");
    assertEquals("CALL 'NO'", t.get(1).value());
    assertFalse(t.stream().anyMatch(x -> x.is("FAKE")));
  }
}
