package io.github.gustavo2358.cobolscan;
import com.fasterxml.jackson.core.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CorpusTest {
    @Test void reviewedCorpusHasNoMissingOrDisconnectedCandidate() throws Exception {
        Options o=new Options(); Path root=Path.of("qualification/corpus/carddemo");
        o.copyDirs.add(root.resolve("cpy"));o.copyDirs.add(root.resolve("cpy-bms"));o.sqlDirs.add(root.resolve("cpy"));
        Scanner scanner=new Scanner(o);int count=0;
        try(JsonParser p=new JsonFactory().createParser(Path.of("qualification/gold.json").toFile())) {
            while(p.nextToken()!=null) if(p.currentToken()==JsonToken.FIELD_NAME && p.currentName().equals("source")) {
                p.nextToken();String name=p.getText();var actual=scanner.scan(root.resolve("cbl").resolve(name),name);count++;
                Map<String,SortedSet<String>> fields=Map.of("programs",actual.programs,"externalFileNames",actual.externalFileNames,"tables",actual.tables,"copybooks",actual.copybooks,"sqlIncludes",actual.sqlIncludes,"dclgens",actual.dclgens);
                while(p.nextToken()!=JsonToken.END_OBJECT) {
                    if(p.currentToken()!=JsonToken.FIELD_NAME) continue;String field=p.currentName();p.nextToken();
                    if(fields.containsKey(field)) { SortedSet<String> expected=new TreeSet<>();while(p.nextToken()!=JsonToken.END_ARRAY) expected.add(p.getText());assertEquals(expected,fields.get(field),name+" / "+field); }
                    else p.skipChildren();
                }
                assertNotEquals("ERROR",actual.scanStatus,name+": "+actual.diagnostics);
            }
        }
        assertEquals(10,count);
    }
}
