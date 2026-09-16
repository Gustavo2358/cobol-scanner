package io.github.gustavo2358.cobolscan.scan;
import io.github.gustavo2358.cobolscan.fact.Value;
import java.util.*;
public final class Operands {
    public record Parsed(Value value,int next) {}
    public static Parsed read(List<Token> ts,int i) {
        if(i>=ts.size()) return new Parsed(new Value.Unknown("Missing operand"),i);
        Token t=ts.get(i++);
        if(t.kind()==Token.Kind.STRING || t.kind()==Token.Kind.NUMBER) return new Parsed(new Value.Literal(t.value()),i);
        if(t.is("SPACE") || t.is("SPACES")) return new Parsed(new Value.Literal(" "),i);
        if(t.is("ZERO") || t.is("ZEROS") || t.is("ZEROES")) return new Parsed(new Value.Literal("0"),i);
        if(t.is("FUNCTION")) {
            Parsed argument=read(ts,i); return new Parsed(new Value.Unknown("FUNCTION"),argument.next());
        }
        if(t.kind()!=Token.Kind.WORD || FactScanner.BOUNDARIES.contains(t.upper())) return new Parsed(new Value.Unknown("Unsupported operand: "+t.text()),i);
        Value value=new Value.Ref(t.upper());
        while(i<ts.size()) {
            if((ts.get(i).is("OF") || ts.get(i).is("IN")) && i+1<ts.size() && ts.get(i+1).kind()==Token.Kind.WORD) { i+=2; continue; }
            if(!ts.get(i).is("(")) break;
            int start=++i,depth=1,colon=-1;
            while(i<ts.size() && depth>0) {
                if(ts.get(i).is("(")) depth++;
                if(ts.get(i).is(")")) depth--;
                if(depth==1 && ts.get(i).is(":")) colon=i;
                if(depth>0) i++;
            }
            if(depth!=0) return new Parsed(new Value.Unknown("Unclosed reference"),i);
            if(colon>=0) {
                Integer offset=colon==start+1 ? integer(ts.get(start)) : null;
                Integer length=colon+1==i ? Integer.valueOf(-1) : colon+2==i ? integer(ts.get(colon+1)) : null;
                value=new Value.Slice(value,offset,length);
            }
            i++;
        }
        return new Parsed(value,i);
    }
    private static Integer integer(Token t) {
        if(t.kind()!=Token.Kind.NUMBER) return null;
        try { return Integer.valueOf(t.value()); } catch(NumberFormatException e) { return null; }
    }
    public static void assign(io.github.gustavo2358.cobolscan.fact.ValueFacts facts,Value destination,Value producer) {
        if(destination instanceof Value.Ref ref) facts.add(ref.name(),producer);
        else if(destination instanceof Value.Slice slice) assign(facts,slice.source(),new Value.Unknown("Partial reference write"));
    }
}
