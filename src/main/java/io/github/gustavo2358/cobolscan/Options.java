package io.github.gustavo2358.cobolscan;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
public final class Options {
    public Path source, output;
    public final List<Path> copyDirs = new ArrayList<>(), sqlDirs = new ArrayList<>(), dclgenDirs = new ArrayList<>();
    public Charset charset = StandardCharsets.UTF_8;
    public String format = "fixed";
    public int threads = 1;
    public boolean help;
    public static Options parse(String... args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String key = args[i]; if (key.equals("--help")) { o.help = true; continue; }
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing value for " + key);
            String value = args[++i];
            switch (key) {
                case "--source" -> o.source = Path.of(value);
                case "--output" -> o.output = Path.of(value);
                case "--copy-dir" -> o.copyDirs.add(Path.of(value));
                case "--sql-include-dir" -> o.sqlDirs.add(Path.of(value));
                case "--dclgen-dir" -> o.dclgenDirs.add(Path.of(value));
                case "--charset" -> o.charset = Charset.forName(value);
                case "--source-format" -> o.format = value.toLowerCase(Locale.ROOT);
                case "--threads" -> o.threads = Integer.parseInt(value);
                default -> throw new IllegalArgumentException("Unknown option: " + key);
            }
        }
        if (!o.help && (o.source == null || o.output == null)) throw new IllegalArgumentException("--source and --output required");
        if (!Set.of("fixed", "free").contains(o.format)) throw new IllegalArgumentException("--source-format: fixed|free");
        if (o.threads < 1 || o.threads > 64) throw new IllegalArgumentException("--threads: 1..64");
        return o;
    }
}
