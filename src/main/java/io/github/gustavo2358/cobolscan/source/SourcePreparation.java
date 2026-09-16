package io.github.gustavo2358.cobolscan.source;
import io.github.gustavo2358.cobolscan.Options;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import io.github.gustavo2358.cobolscan.scan.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class SourcePreparation {
    private final Options options;
    public SourcePreparation(Options options) { this.options = options; }
    public List<Token> prepare(Path source, ScanResult result) throws IOException {
        return expand(source.toRealPath(), List.of(), new HashSet<>(), result);
    }
    private record Replacement(List<Token> from, List<Token> to) {}
    private List<Token> expand(Path path, List<Replacement> replacements, Set<Path> active, ScanResult result) throws IOException {
        if (!active.add(path)) { result.partial("Include cycle: " + path.getFileName()); return List.of(); }
        try {
            List<Token> tokens = replace(Lexer.lex(Normalizer.normalize(Files.readString(path, options.charset), options.format, result)), replacements);
            List<Token> out = new ArrayList<>(); boolean exec = false;
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.kind() == Token.Kind.INVALID) result.partial("Malformed token near line " + t.line());
                if (t.is("EXEC")) exec = true;
                if (t.is("END-EXEC")) exec = false;
                if (t.is("REPLACE") && !exec) result.partial("Top-level REPLACE unsupported");
                if (!exec && t.is("COPY") && i+1 < tokens.size()) {
                    Token member = tokens.get(++i); result.copybooks.add(member.upper()); int end = i+1;
                    boolean pseudo = false;
                    while (end < tokens.size()) { if (tokens.get(end).is("==")) pseudo = !pseudo;
                        if (!pseudo && tokens.get(end).is(".")) break; end++; }
                    List<Replacement> rules = replacements(tokens.subList(i+1, end), result);
                    Path copy = find(member.value(), options.copyDirs, path.getParent());
                    if (copy == null) result.partial("COPY not found: " + member.value());
                    else try { out.addAll(expand(copy.toRealPath(), rules, active, result)); }
                    catch (IOException e) { result.partial("COPY read failed: " + member.value()); }
                    i = end;
                } else out.add(t);
            }
            if (exec) result.partial("Unclosed EXEC region");
            if (!result.scanStatus.equals("OK")) result.programResolutionIncomplete = true;
            return out;
        } finally { active.remove(path); }
    }
    private List<Replacement> replacements(List<Token> clause, ScanResult result) {
        int start = -1; for (int i=0;i<clause.size();i++) if (clause.get(i).is("REPLACING")) { start=i+1; break; }
        if (start < 0) return List.of();
        List<Replacement> rules = new ArrayList<>(); int[] index = {start};
        while (index[0] < clause.size()) {
            List<Token> from = operand(clause, index);
            if (index[0] >= clause.size() || !clause.get(index[0]++).is("BY")) { result.partial("Unsupported COPY REPLACING"); break; }
            List<Token> to = operand(clause, index);
            if (from.isEmpty()) { result.partial("Empty COPY replacement operand"); break; }
            rules.add(new Replacement(from, to));
        }
        return rules;
    }
    private List<Token> operand(List<Token> ts, int[] i) {
        if (i[0] >= ts.size()) return List.of();
        if (!ts.get(i[0]).is("==")) return List.of(ts.get(i[0]++));
        int start = ++i[0]; while (i[0] < ts.size() && !ts.get(i[0]).is("==")) i[0]++;
        List<Token> result = ts.subList(start, i[0]); if (i[0] < ts.size()) i[0]++; return result;
    }
    private List<Token> replace(List<Token> ts, List<Replacement> rules) {
        if (rules.isEmpty()) return ts; List<Token> out = new ArrayList<>();
        for (int i=0;i<ts.size();) {
            boolean matched=false;
            for (Replacement rule : rules) {
                if (i+rule.from.size() > ts.size()) continue; boolean equal=true;
                for (int j=0;j<rule.from.size();j++) {
                    Token a=ts.get(i+j), b=rule.from.get(j);
                    if (a.kind()!=b.kind() || !(a.kind()==Token.Kind.STRING ? a.value().equals(b.value()) : a.text().equalsIgnoreCase(b.text()))) { equal=false; break; }
                }
                if (equal) { out.addAll(rule.to); i+=rule.from.size(); matched=true; break; }
            }
            if (!matched) out.add(ts.get(i++));
        }
        return out;
    }
    public static Path find(String name, List<Path> dirs, Path local) throws IOException {
        // Only direct members of explicitly supplied roots (or including file's directory).
        if (name.contains("/") || name.contains("\\") || name.equals("..")) return null;
        List<Path> roots = new ArrayList<>(dirs); roots.add(local);
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            for (String suffix : List.of("", ".cpy", ".cbl", ".cob", ".inc")) {
                String wanted = name + suffix;
                try (var entries = Files.list(root)) {
                    Optional<Path> hit = entries.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equalsIgnoreCase(wanted)).sorted().findFirst();
                    if (hit.isPresent()) return hit.get();
                }
            }
        }
        return null;
    }
}
