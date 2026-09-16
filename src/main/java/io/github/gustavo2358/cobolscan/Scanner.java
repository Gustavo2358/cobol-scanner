package io.github.gustavo2358.cobolscan;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.nio.file.*;
public final class Scanner {
    private final Options options;
    public Scanner(Options options) { this.options = options; }
    public ScanResult scan(Path source, String label) {
        ScanResult r = new ScanResult(label);
        try { new io.github.gustavo2358.cobolscan.source.SourcePreparation(options).prepare(source, r); }
        catch (Exception e) { r.scanStatus = "ERROR"; r.programResolutionIncomplete = true; r.diagnostics.add(e.getMessage()); }
        return r;
    }
}
