package io.github.gustavo2358.cobolscan.scan;
import io.github.gustavo2358.cobolscan.fact.Value;
import java.util.*;
public final class Operands {
    public record Parsed(Value value, int next) {}
    public static Parsed read(List<Token> ts, int i) {
        if (i>=ts.size()) return new Parsed(new Value.Unknown("Missing operand"),i);
        Token t=ts.get(i);
        if (t.kind()==Token.Kind.STRING) return new Parsed(new Value.Literal(t.value()),i+1);
        if (t.kind()==Token.Kind.WORD) return new Parsed(new Value.Ref(t.upper()),i+1);
        return new Parsed(new Value.Unknown("Unsupported operand: "+t.text()),i+1);
    }
}
