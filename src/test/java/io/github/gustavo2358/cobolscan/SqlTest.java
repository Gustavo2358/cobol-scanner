package io.github.gustavo2358.cobolscan;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SqlTest extends ProgramsTest {
    @Test void requiredVerbsAndNestedQueries() throws Exception {
        var r=scan("EXEC SQL SELECT * FROM A, B JOIN S.C ON B.K=C.K WHERE EXISTS (SELECT 1 FROM DB.S.D) END-EXEC. EXEC SQL INSERT INTO E VALUES(1) END-EXEC. EXEC SQL UPDATE F SET X=1 END-EXEC. EXEC SQL DELETE FROM G END-EXEC. EXEC SQL MERGE INTO H USING I ON H.K=I.K WHEN MATCHED THEN UPDATE SET X=1 END-EXEC.");
        assertEquals(Set.of("A","B","S.C","DB.S.D","E","F","G","H","I"),r.tables);
    }
    @Test void quotedNamesAndComments() throws Exception {
        var r=scan("EXEC SQL SELECT 'FROM NOPE', X FROM \"Mixed Schema\".\"Some\"\"Table\", plain -- JOIN NOPE\n /* FROM BAD */ WHERE X='JOIN LIE' END-EXEC.");
        assertEquals(Set.of("\"Mixed Schema\".\"Some\"\"Table\"","PLAIN"),r.tables); assertTrue(r.programs.isEmpty());
    }
    @Test void derivedCteDclgenAndDynamic() throws Exception {
        Files.writeString(dir.resolve("DCL.cpy"),"EXEC SQL DECLARE S.T TABLE (ID INT) END-EXEC.");
        var r=scan("EXEC SQL INCLUDE DCL END-EXEC. EXEC SQL WITH X AS (SELECT * FROM BASE) SELECT * FROM X JOIN (SELECT * FROM SECOND) Y ON X.ID=Y.ID END-EXEC. EXEC SQL PREPARE S1 FROM :SQL-TEXT END-EXEC.");
        assertEquals(Set.of("S.T","BASE","X","SECOND"),r.tables); assertEquals("PARTIAL",r.scanStatus);
    }
}
