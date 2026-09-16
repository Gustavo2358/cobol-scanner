package io.github.gustavo2358.cobolscan;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.nio.file.*;
public final class Scanner {
    private final Options options;
    public Scanner(Options options) { this.options = options; }
    public ScanResult scan(Path source, String label) {
        ScanResult r = new ScanResult(label);
        try { var tokens = new io.github.gustavo2358.cobolscan.source.SourcePreparation(options).prepare(source, r);
            new io.github.gustavo2358.cobolscan.extract.FileScanner().scan(tokens, r);
            new io.github.gustavo2358.cobolscan.extract.SqlScanner().scan(tokens, r);
            var sinks = new io.github.gustavo2358.cobolscan.scan.IslandScanner().scan(tokens, r);
            var facts = new io.github.gustavo2358.cobolscan.scan.FactScanner().scan(tokens);
            var resolver = new io.github.gustavo2358.cobolscan.resolve.ValueResolver(facts);
            for (var sink : sinks) {
                var resolution = resolver.resolve(sink.target());
                for (String value : resolution.values()) if (!value.isBlank()) r.programs.add(value.stripTrailing());
                r.programResolutionIncomplete |= resolution.incomplete();
            }
            if (r.programResolutionIncomplete) r.partial("Unresolved program target or source gap"); }
        catch (Exception e) { r.scanStatus = "ERROR"; r.programResolutionIncomplete = true; r.diagnostics.add(e.getMessage()); }
        return r;
    }
}
