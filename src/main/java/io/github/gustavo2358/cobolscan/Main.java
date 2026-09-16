package io.github.gustavo2358.cobolscan;
import io.github.gustavo2358.cobolscan.output.*;
import java.nio.file.*;
import java.util.*;
public final class Main {
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String... args) {
        try {
            Options o = Options.parse(args);
            if (o.help) { System.out.println("COBOL Dependency Scanner\n--source FILE|DIR --output FILE [--copy-dir DIR] [--sql-include-dir DIR]\n[--dclgen-dir DIR] [--charset UTF-8] [--source-format fixed|free] [--threads N]"); return 0; }
            if (!Files.exists(o.source)) throw new IllegalArgumentException("Source does not exist: " + o.source);
            Path source = o.source.toRealPath(), output = o.output.toAbsolutePath().normalize();
            if (source.equals(output) || (Files.exists(output) && Files.isSameFile(source, output))) throw new IllegalArgumentException("Output must not overwrite source");
            List<Path> paths;
            if (Files.isDirectory(source)) {
                try (var walk = Files.walk(source)) { paths = walk.filter(Files::isRegularFile).filter(Main::isSource).sorted().toList(); }
            } else paths = List.of(source);
            int code = 0; Scanner scanner = new Scanner(o);
            try (JsonOutput out = new JsonOutput(Files.newOutputStream(output))) {
                for (Path p : paths) {
                    String label = Files.isDirectory(source) ? source.relativize(p).toString().replace('\\', '/') : p.getFileName().toString();
                    ScanResult r = scanner.scan(p, label); out.write(r);
                    for (String d : r.diagnostics) System.err.println(label + ": " + d);
                    if (!r.scanStatus.equals("OK")) code = 1;
                }
            }
            return code;
        } catch (Exception e) { System.err.println("cobol-dependency-scan: " + e.getMessage()); return 2; }
    }
    static boolean isSource(Path p) { String s = p.toString().toLowerCase(Locale.ROOT); return s.endsWith(".cbl") || s.endsWith(".cob") || s.endsWith(".cobol"); }
}
