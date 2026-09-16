package io.github.gustavo2358.cobolscan.fact;

import java.util.*;

public final class ValueFacts {
  private final Map<String, Set<Value>> producers = new HashMap<>();

  public void add(String target, Value value) {
    producers.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(value);
  }

  public Collection<Value> producers(String name) {
    return producers.getOrDefault(name, Set.of());
  }
}
