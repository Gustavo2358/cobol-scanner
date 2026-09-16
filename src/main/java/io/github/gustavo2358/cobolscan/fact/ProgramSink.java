package io.github.gustavo2358.cobolscan.fact;

public record ProgramSink(Kind kind, Value target) {
  public enum Kind {
    COBOL_CALL,
    CICS_LINK,
    CICS_XCTL
  }
}
