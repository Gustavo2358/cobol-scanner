package io.github.gustavo2358.cobolscan.extract;
import io.github.gustavo2358.cobolscan.scan.Token;
import io.github.gustavo2358.cobolscan.output.ScanResult;
import java.util.*;
public final class SqlScanner {
    private static final Set<String> STOP=Set.of("WHERE","GROUP","ORDER","HAVING","UNION","EXCEPT","INTERSECT","FETCH","FOR","SET","VALUES","WHEN","ON","RETURNING","INTO","SELECT","TABLE","FINAL","OLD","NEW","LATERAL");
    public void scan(List<Token> tokens,ScanResult result) {
        for(int i=0;i+1<tokens.size();i++) if(tokens.get(i).is("EXEC")) {
            int end=i+2; while(end<tokens.size() && !tokens.get(end).is("END-EXEC")) end++;
            if(tokens.get(i+1).is("SQL")) region(tokens.subList(i+2,end),result); i=end;
        }
    }
    private record Name(String value,int next) {}
    private Name name(List<Token> ts,int i) {
        if(i>=ts.size() || !identifier(ts.get(i))) return null;
        StringBuilder value=new StringBuilder(part(ts.get(i++)));
        while(i+1<ts.size() && ts.get(i).is(".") && identifier(ts.get(i+1))) { value.append('.').append(part(ts.get(i+1))); i+=2; }
        return new Name(value.toString(),i);
    }
    private boolean identifier(Token t) { return t.kind()==Token.Kind.WORD && !STOP.contains(t.upper()) || t.kind()==Token.Kind.STRING && t.text().startsWith("\""); }
    private String part(Token t) { return t.kind()==Token.Kind.STRING ? t.text() : t.upper(); }
    private void region(List<Token> ts,ScanResult result) {
        if(ts.isEmpty() || ts.get(0).is("INCLUDE")) return;
        if(ts.get(0).is("PREPARE") || ts.get(0).is("EXECUTE")) { result.partial("Dynamic SQL objects unresolved"); return; }
        boolean merge=ts.get(0).is("MERGE"), from=false; Deque<Boolean> scopes=new ArrayDeque<>();
        for(int i=0;i<ts.size();i++) {
            Token t=ts.get(i);
            if(t.is("(")) { scopes.push(from); from=false; continue; }
            if(t.is(")")) { from=scopes.isEmpty()?false:scopes.pop(); continue; }
            if(STOP.contains(t.upper()) && t.kind()==Token.Kind.WORD) from=false;
            int target=-1;
            if(t.is("FROM")) { from=true; target=i+1; }
            else if(t.is("JOIN")) target=i+1;
            else if(t.is(",") && from) target=i+1;
            else if(t.is("UPDATE") && (i==0 || !ts.get(i+1<ts.size()?i+1:i).is("SET"))) target=i+1;
            else if(t.is("INTO") && i>0 && (ts.get(i-1).is("INSERT") || ts.get(i-1).is("MERGE"))) target=i+1;
            else if(t.is("USING") && merge) target=i+1;
            else if(t.is("DECLARE")) {
                Name n=name(ts,i+1); if(n!=null && n.next<ts.size() && ts.get(n.next).is("TABLE")) result.tables.add(n.value);
            }
            if(target>=0) { Name n=name(ts,target); if(n!=null) { result.tables.add(n.value); i=n.next-1; } }
        }
    }
}
