package io.github.gustavo2358.cobolscan;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StructuralTest extends ProgramsTest {
    @Test void multilineAssignAndCopy() throws Exception {
        Files.writeString(dir.resolve("FILES.cpy"),"SELECT F ASSIGN\n TO INPUTDD. SELECT G ASSIGN TO 'MixedDD'.");
        var r=scan("COPY FILES. SELECT H ASSIGN TO OUTPUTDD.");
        assertEquals(Set.of("INPUTDD","MixedDD","OUTPUTDD"),r.externalFileNames);
    }
    @Test void includesBuiltinsNestedDclgenAndMissing() throws Exception {
        Files.writeString(dir.resolve("DCLCLIENT.cpy"),"EXEC SQL DECLARE CUSTOMER TABLE (ID INT) END-EXEC. EXEC SQL INCLUDE INNER END-EXEC.");
        Files.writeString(dir.resolve("INNER.cpy"),"01 WS-PGM PIC X(8) VALUE 'SUB00001'.");
        var r=scan("EXEC SQL INCLUDE DCLCLIENT END-EXEC. EXEC SQL INCLUDE SQLCA END-EXEC. EXEC SQL INCLUDE SQLDA END-EXEC. EXEC SQL INCLUDE MISSING END-EXEC. CALL WS-PGM.");
        assertEquals(Set.of("DCLCLIENT","INNER","SQLCA","SQLDA","MISSING"),r.sqlIncludes);
        assertEquals(Set.of("DCLCLIENT"),r.dclgens); assertEquals(Set.of("SUB00001"),r.programs); assertEquals("PARTIAL",r.scanStatus);
    }
    @Test void directoryClassifiesDclgen() throws Exception {
        Path includes=Files.createDirectory(dir.resolve("dcl")); Files.writeString(includes.resolve("MEMBER.cpy"),"01 X PIC X.");
        Path source=dir.resolve("main.cbl"); Files.writeString(source,"EXEC SQL INCLUDE MEMBER END-EXEC.");
        Options o=new Options(); o.format="free"; o.dclgenDirs.add(includes);
        assertEquals(Set.of("MEMBER"),new Scanner(o).scan(source,"main").dclgens);
    }
}
