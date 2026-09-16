package io.github.gustavo2358.cobolscan.scan;
import io.github.gustavo2358.cobolscan.fact.*;
import java.util.*;
public final class FactScanner {
    // Statement/clause boundaries prevent unrelated words from becoming MOVE receivers.
    public static final Set<String> BOUNDARIES=Set.of("ACCEPT","ADD","ALLOCATE","ALTER","CALL","CANCEL","CLOSE","COMPUTE","CONTINUE","DELETE","DISPLAY","DIVIDE","ELSE","END-IF","END-MOVE","END-STRING","END-CALL","END-EVALUATE","END-PERFORM","END-READ","END-WRITE","END-COMPUTE","EVALUATE","EXEC","EXIT","FREE","GOBACK","GO","IF","INITIALIZE","INSPECT","MERGE","MOVE","MULTIPLY","OPEN","PERFORM","READ","RELEASE","RETURN","REWRITE","SEARCH","SET","SORT","START","STOP","STRING","SUBTRACT","UNSTRING","WHEN","WRITE","ON","NOT","SIZE","ERROR","INVALID","AT","END","THEN","SECTION","DIVISION","USING","RETURNING","DELIMITED","WITH","POINTER","END-EXEC");
    public ValueFacts scan(List<Token> ts) {
        ValueFacts facts=new ValueFacts(); String declaration=null; boolean procedure=false;
        for(int i=0;i<ts.size();i++) {
            Token t=ts.get(i);
            if(t.is("EXEC")) { while(i<ts.size() && !ts.get(i).is("END-EXEC")) i++; continue; }
            if(t.is("PROCEDURE")) { procedure=true; declaration=null; }
            if(t.is(".")) declaration=null;
            if(!procedure && t.kind()==Token.Kind.NUMBER && i+1<ts.size() && ts.get(i+1).kind()==Token.Kind.WORD && (i==0 || ts.get(i-1).line()<t.line() || ts.get(i-1).is("."))) {
                int level; try { level=Integer.parseInt(t.value()); } catch(NumberFormatException e) { continue; }
                if(level>=1 && level<=49 || level==77) declaration=ts.get(i+1).upper();
                else if(level==88 || level==66) declaration=null;
            }
            if((t.is("VALUE") || t.is("VALUES")) && declaration!=null) {
                int v=i+1; if(v<ts.size() && (ts.get(v).is("IS") || ts.get(v).is("ARE"))) v++;
                facts.add(declaration,Operands.read(ts,v).value());
            }
            if(t.is("MOVE")) {
                var src=Operands.read(ts,i+1); int to=src.next();
                if(to<ts.size() && ts.get(to).is("TO")) {
                    for(int j=to+1;j<ts.size() && isReceiver(ts.get(j));) {
                        var dest=Operands.read(ts,j); if(dest.value() instanceof Value.Ref ref) facts.add(ref.name(),src.value()); j=dest.next();
                    }
                }
            }
            if(t.is("ACCEPT") || t.is("COMPUTE") || t.is("INITIALIZE") || t.is("SET")) {
                var dest=Operands.read(ts,i+1); if(dest.value() instanceof Value.Ref ref) facts.add(ref.name(),new Value.Unknown(t.upper()));
            }
            if(t.is("READ")) {
                int j=i+2; if(j<ts.size() && ts.get(j).is("INTO")) {
                    var dest=Operands.read(ts,j+1); if(dest.value() instanceof Value.Ref ref) facts.add(ref.name(),new Value.Unknown("READ INTO"));
                }
            }
        }
        return facts;
    }
    static boolean isReceiver(Token t) { return t.kind()==Token.Kind.WORD && !BOUNDARIES.contains(t.upper()); }
}
