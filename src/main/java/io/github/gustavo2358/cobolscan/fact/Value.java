package io.github.gustavo2358.cobolscan.fact;
public sealed interface Value {
    record Literal(String text) implements Value {}
    record Ref(String name) implements Value {}
    record Concat(java.util.List<Value> parts) implements Value { public Concat { parts = java.util.List.copyOf(parts); } }
    record Slice(Value source, Integer start, Integer length) implements Value {}
    record Delimited(Value source, String delimiter) implements Value {}
    record Unknown(String reason) implements Value {}
}
