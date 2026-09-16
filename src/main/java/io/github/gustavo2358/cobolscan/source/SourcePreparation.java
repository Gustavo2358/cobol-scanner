package io.github.gustavo2358.cobolscan.source;

import io.github.gustavo2358.cobolscan.Options;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import io.github.gustavo2358.cobolscan.scan.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class SourcePreparation {
  private final Options options;
  private final Map<Path, String> rawCache = new LinkedHashMap<>(16, .75f, true);
  private long cacheBytes;

  private static final class Budget {
    long bytes;
  }

  private synchronized String read(Path path, boolean cache) throws IOException {
    String found = rawCache.get(path);
    if (found != null) return found;
    String raw = Files.readString(path, options.charset);
    long weight = 2L * raw.length(), limit = Runtime.getRuntime().maxMemory() / 16;
    if (cache && weight <= limit) {
      while (cacheBytes + weight > limit && !rawCache.isEmpty()) {
        var eldest = rawCache.entrySet().iterator();
        var entry = eldest.next();
        cacheBytes -= 2L * entry.getValue().length();
        eldest.remove();
      }
      rawCache.put(path, raw);
      cacheBytes += weight;
    }
    return raw;
  }

  public SourcePreparation(Options options) {
    this.options = options;
  }

  public List<Token> prepare(Path source, ScanResult result) throws IOException {
    return expand(source.toRealPath(), List.of(), new HashSet<>(), new Budget(), result);
  }

  private record Replacement(List<Token> from, List<Token> to) {}

  private List<Token> expand(
      Path path, List<Replacement> replacements, Set<Path> active, Budget budget, ScanResult result)
      throws IOException {
    if (active.size() >= options.maxIncludeDepth) {
      result.partial("Include depth budget exceeded at " + path.getFileName());
      result.programResolutionIncomplete = true;
      return List.of();
    }
    long size = Files.size(path);
    if (size > options.sourceBudget() - budget.bytes)
      throw new IOException("Source/expansion byte budget exceeded: " + options.sourceBudget());
    budget.bytes += size;
    if (!active.add(path)) {
      result.partial("Include cycle: " + path.getFileName());
      return List.of();
    }
    try {
      List<Token> tokens =
          replace(
              Lexer.lex(
                  Normalizer.normalize(read(path, active.size() > 1), options.format, result)),
              replacements,
              budget);
      List<Token> out = new ArrayList<>();
      boolean exec = false;
      for (int i = 0; i < tokens.size(); i++) {
        Token t = tokens.get(i);
        if (t.kind() == Token.Kind.INVALID) result.partial("Malformed token near line " + t.line());
        if (t.is("EXEC")
            && i + 3 < tokens.size()
            && tokens.get(i + 1).is("SQL")
            && tokens.get(i + 2).is("INCLUDE")) {
          Token member = tokens.get(i + 3);
          result.sqlIncludes.add(member.upper());
          int end = i + 4;
          while (end < tokens.size() && !tokens.get(end).is("END-EXEC")) end++;
          out.addAll(tokens.subList(i, Math.min(end + 1, tokens.size())));
          if (!Set.of("SQLCA", "SQLDA").contains(member.upper())) {
            List<Path> roots = new ArrayList<>(options.sqlDirs);
            roots.addAll(options.dclgenDirs);
            Path include = find(member.value(), roots, path.getParent());
            if (include == null) result.partial("SQL INCLUDE not found: " + member.value());
            else
              try {
                if (Files.size(include) > options.sourceBudget() - budget.bytes)
                  throw new IOException("SQL include byte budget exceeded");
                String raw = read(include.toRealPath(), true);
                List<Token> content = Lexer.lex(Normalizer.normalize(raw, options.format, result));
                boolean declared = false;
                for (int j = 0; j + 2 < content.size(); j++)
                  if (content.get(j).is("DECLARE")) {
                    for (int k = j + 1; k < Math.min(j + 8, content.size()); k++)
                      if (content.get(k).is("TABLE")) declared = true;
                  }
                boolean configured = false;
                for (Path d : options.dclgenDirs)
                  if (include.toRealPath().startsWith(d.toRealPath())) configured = true;
                if (declared || configured) result.dclgens.add(member.upper());
                out.addAll(expand(include.toRealPath(), List.of(), active, budget, result));
              } catch (IOException e) {
                result.partial(
                    "SQL INCLUDE read failed: " + member.value() + ": " + e.getMessage());
              }
          }
          i = end;
          continue;
        }
        if (t.is("EXEC")) exec = true;
        if (t.is("END-EXEC")) exec = false;
        if (t.is("REPLACE") && !exec) result.partial("Top-level REPLACE unsupported");
        if (!exec && t.is("COPY") && i + 1 < tokens.size()) {
          Token member = tokens.get(++i);
          result.copybooks.add(member.upper());
          int end = copyEnd(tokens, i + 1);
          List<Token> clause = tokens.subList(i + 1, end);
          // There is no library-name -> directory contract. Do not guess by root order.
          if (!clause.isEmpty() && (clause.get(0).is("OF") || clause.get(0).is("IN"))) {
            String library = clause.size() > 1 ? clause.get(1).value() : "<missing>";
            result.partial(
                "COPY qualifier unresolved: "
                    + member.value()
                    + " "
                    + clause.get(0).upper()
                    + " "
                    + library);
            i = end;
            continue;
          }
          List<Replacement> local = replacements(clause, result);
          if (!replacements.isEmpty() && !local.isEmpty()) {
            result.partial("COPY nested REPLACING conflict: " + member.value());
            i = end;
            continue;
          }
          List<Replacement> rules = local.isEmpty() ? replacements : local;
          Path copy = find(member.value(), options.copyDirs, path.getParent());
          if (copy == null) result.partial("COPY not found: " + member.value());
          else
            try {
              out.addAll(expand(copy.toRealPath(), rules, active, budget, result));
            } catch (IOException e) {
              result.partial("COPY read failed: " + member.value() + ": " + e.getMessage());
            }
          i = end;
        } else out.add(t);
      }
      if (exec) result.partial("Unclosed EXEC region");
      if (!result.scanStatus.equals("OK")) result.programResolutionIncomplete = true;
      return out;
    } finally {
      active.remove(path);
    }
  }

  private List<Replacement> replacements(List<Token> clause, ScanResult result) {
    int start = -1;
    for (int i = 0; i < clause.size(); i++)
      if (clause.get(i).is("REPLACING")) {
        start = i + 1;
        break;
      }
    if (start < 0) return List.of();
    List<Replacement> rules = new ArrayList<>();
    int[] index = {start};
    while (index[0] < clause.size()) {
      List<Token> from = operand(clause, index);
      if (index[0] >= clause.size() || !clause.get(index[0]++).is("BY")) {
        result.partial("Unsupported COPY REPLACING");
        break;
      }
      List<Token> to = operand(clause, index);
      if (from.isEmpty()) {
        result.partial("Empty COPY replacement operand");
        break;
      }
      rules.add(new Replacement(from, to));
    }
    return rules;
  }

  private List<Token> operand(List<Token> ts, int[] i) {
    if (i[0] >= ts.size()) return List.of();
    if (!ts.get(i[0]).is("==")) return List.of(ts.get(i[0]++));
    int start = ++i[0];
    while (i[0] < ts.size() && !ts.get(i[0]).is("==")) i[0]++;
    List<Token> result = ts.subList(start, i[0]);
    if (i[0] < ts.size()) i[0]++;
    return result;
  }

  private List<Token> replace(List<Token> ts, List<Replacement> rules, Budget budget)
      throws IOException {
    if (rules.isEmpty()) return ts;
    List<Token> out = new ArrayList<>();
    boolean exec = false;
    for (int i = 0; i < ts.size(); ) {
      if (ts.get(i).is("EXEC")) exec = true;
      if (ts.get(i).is("END-EXEC")) exec = false;
      // Directives carry the replacement context; do not rewrite their own operands.
      if (!exec && ts.get(i).is("COPY")) {
        int end = Math.min(copyEnd(ts, i + 1) + 1, ts.size());
        out.addAll(ts.subList(i, end));
        i = end;
        continue;
      }
      boolean matched = false;
      for (Replacement rule : rules) {
        if (i + rule.from.size() > ts.size()) continue;
        boolean equal = true;
        for (int j = 0; j < rule.from.size(); j++) {
          Token a = ts.get(i + j), b = rule.from.get(j);
          if (a.kind() != b.kind()
              || !(a.kind() == Token.Kind.STRING
                  ? a.value().equals(b.value())
                  : a.text().equalsIgnoreCase(b.text()))) {
            equal = false;
            break;
          }
        }
        if (equal) {
          long growth = Math.max(0, renderedSize(rule.to) - renderedSize(rule.from));
          if (growth > options.sourceBudget() - budget.bytes)
            throw new IOException("COPY REPLACING expansion byte budget exceeded");
          budget.bytes += growth;
          out.addAll(rule.to);
          i += rule.from.size();
          matched = true;
          break;
        }
      }
      if (!matched) out.add(ts.get(i++));
    }
    return out;
  }

  private static int copyEnd(List<Token> tokens, int start) {
    boolean pseudo = false;
    int end = start;
    while (end < tokens.size()) {
      if (tokens.get(end).is("==")) pseudo = !pseudo;
      if (!pseudo && tokens.get(end).is(".")) break;
      end++;
    }
    return end;
  }

  private static long renderedSize(List<Token> tokens) {
    long size = 0;
    for (Token t : tokens) size += t.text().length() + 1L;
    return size;
  }

  public static Path find(String name, List<Path> dirs, Path local) throws IOException {
    // Only direct members of explicitly supplied roots (or including file's directory).
    if (name.contains("/") || name.contains("\\") || name.equals("..")) return null;
    List<Path> roots = new ArrayList<>(dirs);
    roots.add(local);
    for (Path root : roots) {
      if (!Files.isDirectory(root)) continue;
      for (String suffix : List.of("", ".cpy", ".cbl", ".cob", ".inc")) {
        String wanted = name + suffix;
        try (var entries = Files.list(root)) {
          Optional<Path> hit =
              entries
                  .filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().equalsIgnoreCase(wanted))
                  .sorted()
                  .findFirst();
          if (hit.isPresent()) return hit.get();
        }
      }
    }
    return null;
  }
}
