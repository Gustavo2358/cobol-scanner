package io.github.gustavo2358.cobolscan;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.nio.file.*;
public final class Scanner {
    private final Options options;
    public Scanner(Options options) { this.options = options; }
    public ScanResult scan(Path source, String label) {
        ScanResult r = new ScanResult(label);
        try { var tokens = new io.github.gustavo2358.cobolscan.source.SourcePreparation(options).prepare(source, r);
            var sinks = new io.github.gustavo2358.cobolscan.scan.IslandScanner().scan(tokens, r);
            for (var sink : sinks) {
                if (sink.target() instanceof io.github.gustavo2358.cobolscan.fact.Value.Literal literal) r.programs.add(literal.text());
                else r.programResolutionIncomplete = true;
            }
            if (r.programResolutionIncomplete) r.partial("Unresolved program target or source gap"); }
        catch (Exception e) { r.scanStatus = "ERROR"; r.programResolutionIncomplete = true; r.diagnostics.add(e.getMessage()); }
        return r;
    }
}
