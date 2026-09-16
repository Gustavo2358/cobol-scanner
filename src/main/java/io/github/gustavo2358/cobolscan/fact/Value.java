package io.github.gustavo2358.cobolscan.fact;
public sealed interface Value {
    record Literal(String text) implements Value {}
    record Ref(String name) implements Value {}
    record Unknown(String reason) implements Value {}
}
