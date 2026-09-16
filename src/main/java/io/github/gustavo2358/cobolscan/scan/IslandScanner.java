package io.github.gustavo2358.cobolscan.scan;

import io.github.gustavo2358.cobolscan.fact.*;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.util.*;

/** Only dependency islands; all other COBOL is water. EXEC is an opaque boundary. */
public final class IslandScanner {
  public List<ProgramSink> scan(List<Token> ts, ScanResult result) {
    List<ProgramSink> sinks = new ArrayList<>();
    for (int i = 0; i < ts.size(); i++) {
      Token t = ts.get(i);
      if (t.is("EXEC")) {
        int end = i + 1;
        while (end < ts.size() && !ts.get(end).is("END-EXEC") && !ts.get(end).is("EXEC")) end++;
        if (end == ts.size() || !ts.get(end).is("END-EXEC")) {
          result.partial("Unterminated EXEC");
          result.programResolutionIncomplete = true;
        }
        if (i + 2 < end && ts.get(i + 1).is("CICS")) cics(ts.subList(i + 2, end), sinks, result);
        i = end < ts.size() && ts.get(end).is("EXEC") ? end - 1 : end;
      } else if (t.is("CALL"))
        sinks.add(new ProgramSink(ProgramSink.Kind.COBOL_CALL, Operands.read(ts, i + 1).value()));
    }
    return sinks;
  }

  private void cics(List<Token> ts, List<ProgramSink> sinks, ScanResult result) {
    if (ts.isEmpty()) return;
    ProgramSink.Kind kind =
        ts.get(0).is("LINK")
            ? ProgramSink.Kind.CICS_LINK
            : ts.get(0).is("XCTL") ? ProgramSink.Kind.CICS_XCTL : null;
    if (kind == null) return;
    for (int i = 1; i + 1 < ts.size(); i++)
      if (ts.get(i).is("PROGRAM") && ts.get(i + 1).is("(")) {
        sinks.add(new ProgramSink(kind, Operands.read(ts, i + 2).value()));
        return;
      }
    sinks.add(new ProgramSink(kind, new Value.Unknown("CICS PROGRAM missing")));
  }
}
