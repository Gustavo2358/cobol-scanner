package io.github.gustavo2358.cobolscan;

import io.github.gustavo2358.cobolscan.extract.*;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import io.github.gustavo2358.cobolscan.resolve.ValueResolver;
import io.github.gustavo2358.cobolscan.scan.*;
import io.github.gustavo2358.cobolscan.source.SourcePreparation;
import java.nio.file.*;

public final class Scanner {
  private final Options options;
  private final SourcePreparation preparation;

  public Scanner(Options options) {
    this.options = options;
    preparation = new SourcePreparation(options);
  }

  public ScanResult scan(Path source, String label) {
    ScanResult r = new ScanResult(label);
    long start = System.nanoTime(), phase = start;
    try {
      r.bytes = Files.size(source);
      if (r.bytes > options.sourceBudget())
        throw new java.io.IOException("Source byte budget exceeded: " + options.sourceBudget());
      try (var lines = Files.lines(source, options.charset)) {
        r.loc = lines.count();
      }
      var tokens = preparation.prepare(source, r);
      r.preparationNanos = System.nanoTime() - phase;
      phase = System.nanoTime();
      new FileScanner().scan(tokens, r);
      new SqlScanner().scan(tokens, r);
      var sinks = new IslandScanner().scan(tokens, r);
      var facts = new FactScanner().scan(tokens);
      r.scanNanos = System.nanoTime() - phase;
      phase = System.nanoTime();
      var resolver =
          new ValueResolver(
              facts, options.maxCandidates, options.maxValueChars, options.sourceBudget());
      for (var sink : sinks) {
        var resolution = resolver.resolve(sink.target());
        for (String value : resolution.values())
          if (!value.isBlank()) {
            if (r.programs.size() < options.maxCandidates
                || r.programs.contains(value.stripTrailing()))
              r.programs.add(value.stripTrailing());
            else r.programResolutionIncomplete = true;
          }
        r.programResolutionIncomplete |= resolution.incomplete();
      }
      r.resolutionNanos = System.nanoTime() - phase;
      if (r.programResolutionIncomplete)
        r.partial("Unresolved program target, source gap or value budget/cycle");
    } catch (Exception e) {
      r.scanStatus = "ERROR";
      r.programResolutionIncomplete = true;
      r.diagnostics.add(e.getClass().getSimpleName() + ": " + e.getMessage());
    } finally {
      r.totalNanos = System.nanoTime() - start;
    }
    return r;
  }
}
