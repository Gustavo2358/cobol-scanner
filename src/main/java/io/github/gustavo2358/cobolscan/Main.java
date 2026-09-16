package io.github.gustavo2358.cobolscan;
import io.github.gustavo2358.cobolscan.output.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public final class Main {
    public static void main(String[] args) { System.exit(run(args)); }
    private record Pending(Path path,Future<ScanResult> result) {}
    public static int run(String... args) {
        Path temporary=null; ExecutorService workers=null;
        try {
            Options o = Options.parse(args);
            if(o.help) { System.out.println("COBOL Dependency Scanner\n--source FILE|DIR --output FILE [--copy-dir DIR] [--sql-include-dir DIR]\n[--dclgen-dir DIR] [--charset UTF-8] [--source-format fixed|free] [--threads N]\n[--metrics FILE.tsv] [--max-source-bytes N] [--max-candidates N] [--max-include-depth N]\nExit codes: 0 OK, 1 partial/error sources, 2 invocation/output failure."); return 0; }
            Path source=o.source.toRealPath(), output=o.output.toAbsolutePath().normalize(); boolean directory=Files.isDirectory(source);
            List<Path> paths;
            if(directory) { try(var walk=Files.walk(source)) { paths=walk.filter(Files::isRegularFile).filter(Main::isSource).sorted().toList(); } }
            else paths=List.of(source);
            protect(output,paths);
            if(directory && output.startsWith(source) && isSource(output)) throw new IllegalArgumentException("Output cannot be a COBOL source in input tree");
            if(o.metrics!=null) {
                o.metrics=o.metrics.toAbsolutePath().normalize(); protect(o.metrics,paths);
                if(o.metrics.equals(output) || (Files.exists(output) && Files.exists(o.metrics) && Files.isSameFile(output,o.metrics))) throw new IllegalArgumentException("Metrics and JSON outputs must differ");
            }
            for(Path dir:concatDirs(o)) if(!Files.isDirectory(dir)) throw new IllegalArgumentException("Include directory does not exist: "+dir);
            temporary=Files.createTempFile(output.getParent(),".cobol-scan-",".json.tmp");
            Scanner scanner=new Scanner(o); workers=Executors.newFixedThreadPool(o.threads);
            int code=0, next=0; Deque<Pending> pending=new ArrayDeque<>();
            try(JsonOutput out=new JsonOutput(Files.newOutputStream(temporary));
                BufferedWriter metrics=o.metrics==null ? null : Files.newBufferedWriter(o.metrics)) {
                if(metrics!=null) metrics.write("source\tloc\tbytes\tpreparation_ms\tscan_ms\tresolution_ms\ttotal_ms\tstatus\n");
                while(next<paths.size() || !pending.isEmpty()) {
                    while(next<paths.size() && pending.size()<o.threads*2) {
                        Path p=paths.get(next++); String label=directory?source.relativize(p).toString().replace('\\','/'):p.getFileName().toString();
                        pending.addLast(new Pending(p,workers.submit(()->scanner.scan(p,label))));
                    }
                    ScanResult r=pending.removeFirst().result.get(); out.write(r);
                    for(String d:r.diagnostics) System.err.println(r.source+": "+d);
                    if(!r.scanStatus.equals("OK")) code=1;
                    if(metrics!=null) metrics.write(String.format(Locale.ROOT,"%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%s%n",r.source.replace('\t',' ').replace('\n',' '),r.loc,r.bytes,r.preparationNanos/1e6,r.scanNanos/1e6,r.resolutionNanos/1e6,r.totalNanos/1e6,r.scanStatus));
                }
            }
            try { Files.move(temporary,output,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING); }
            temporary=null; return code;
        } catch(Exception e) { if(e instanceof InterruptedException) Thread.currentThread().interrupt(); System.err.println("cobol-dependency-scan: "+e.getMessage()); return 2; }
        finally {
            if(workers!=null) workers.shutdownNow();
            if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
        }
    }
    private static List<Path> concatDirs(Options o) { List<Path> dirs=new ArrayList<>(o.copyDirs); dirs.addAll(o.sqlDirs); dirs.addAll(o.dclgenDirs); return dirs; }
    private static void protect(Path out,List<Path> paths) throws IOException {
        for(Path input:paths) if(input.toAbsolutePath().normalize().equals(out) || Files.exists(out) && Files.isSameFile(input,out)) throw new IllegalArgumentException("Output must not overwrite source");
    }
    static boolean isSource(Path p) { String s=p.toString().toLowerCase(Locale.ROOT); return s.endsWith(".cbl") || s.endsWith(".cob") || s.endsWith(".cobol"); }
}
