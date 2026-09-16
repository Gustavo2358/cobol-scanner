package io.github.gustavo2358.cobolscan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BatchTest {
    @TempDir Path dir;
    @Test void parallelDeterminismAndFailureIsolation() throws Exception {
        Path sources=Files.createDirectory(dir.resolve("sources")), nested=Files.createDirectory(sources.resolve("nested"));
        Files.writeString(sources.resolve("A.cbl"),"CALL 'A'."); Files.write(nested.resolve("bad.cbl"),new byte[]{(byte)0xff});
        Files.writeString(nested.resolve("B.CBL"),"CALL 'B'."); Files.writeString(sources.resolve("ignored.cpy"),"CALL 'IGNORE'.");
        Path one=dir.resolve("one.json"), four=dir.resolve("four.json");
        assertEquals(1,Main.run("--source",sources.toString(),"--output",one.toString(),"--source-format","free","--threads","1"));
        assertEquals(1,Main.run("--source",sources.toString(),"--output",four.toString(),"--source-format","free","--threads","4"));
        assertEquals(Files.readString(one),Files.readString(four)); assertFalse(Files.readString(one).contains("IGNORE"));
        assertTrue(Files.readString(one).contains("ERROR")); assertTrue(Files.readString(one).contains("\"B\""));
    }
    @Test void budgetsAreExplicitAndDoNotStopOtherFiles() throws Exception {
        Path sources=Files.createDirectory(dir.resolve("sources")); Files.writeString(sources.resolve("big.cbl")," ".repeat(1000)); Files.writeString(sources.resolve("small.cbl"),"CALL 'OK'.");
        Path out=dir.resolve("out.json"); assertEquals(1,Main.run("--source",sources.toString(),"--output",out.toString(),"--source-format","free","--max-source-bytes","100"));
        assertTrue(Files.readString(out).contains("ERROR")); assertTrue(Files.readString(out).contains("\"OK\""));
    }
    @Test void protectsEveryInputAndPreviousOutputOnConfigFailure() throws Exception {
        Path sources=Files.createDirectory(dir.resolve("sources")), input=sources.resolve("input.cbl"); Files.writeString(input,"CALL 'X'.");
        assertEquals(2,Main.run("--source",sources.toString(),"--output",input.toString())); assertEquals("CALL 'X'.",Files.readString(input));
        Path out=dir.resolve("old.json"); Files.writeString(out,"old");
        assertEquals(2,Main.run("--source",sources.toString(),"--output",out.toString(),"--copy-dir",dir.resolve("absent").toString())); assertEquals("old",Files.readString(out));
    }
    @Test void candidateBudgetIsOpenRemainder() {
        var facts=new io.github.gustavo2358.cobolscan.fact.ValueFacts();
        for(int i=0;i<10;i++) facts.add("P",new io.github.gustavo2358.cobolscan.fact.Value.Literal("P"+i));
        var r=new io.github.gustavo2358.cobolscan.resolve.ValueResolver(facts,3).resolve(new io.github.gustavo2358.cobolscan.fact.Value.Ref("P"));
        assertEquals(3,r.values().size()); assertTrue(r.incomplete());
    }
}
