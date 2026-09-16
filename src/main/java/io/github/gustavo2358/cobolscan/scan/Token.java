package io.github.gustavo2358.cobolscan.scan;
import java.util.Locale;
public record Token(Kind kind, String text, String value, int line) {
    public enum Kind { WORD, STRING, NUMBER, SYMBOL, INVALID }
    public boolean is(String s) { return (kind == Kind.WORD || kind == Kind.SYMBOL) && text.equalsIgnoreCase(s); }
    public String upper() { return value.toUpperCase(Locale.ROOT); }
}
