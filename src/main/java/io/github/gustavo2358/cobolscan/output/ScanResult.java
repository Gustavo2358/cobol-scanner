package io.github.gustavo2358.cobolscan.output;
import java.util.*;
public final class ScanResult {
    public final String source;
    public final SortedSet<String> programs = new TreeSet<>(), externalFileNames = new TreeSet<>(),
        tables = new TreeSet<>(), copybooks = new TreeSet<>(), sqlIncludes = new TreeSet<>(), dclgens = new TreeSet<>();
    public final List<String> diagnostics = new ArrayList<>();
    public boolean programResolutionIncomplete;
    public String scanStatus = "OK";
    public ScanResult(String source) { this.source = source; }
    public void partial(String message) { diagnostics.add(message); if (!scanStatus.equals("ERROR")) scanStatus = "PARTIAL"; }
}
