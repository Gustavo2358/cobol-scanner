package io.github.gustavo2358.cobolscan.fact;

import java.util.*;

public final class ValueFacts {
  private final Map<String, Set<Value>> producers = new HashMap<>();

  private final Map<String, Set<String>> children = new HashMap<>();
  private final Map<String, Set<String>> parents = new HashMap<>();
  private final Map<String, Set<String>> aliases = new HashMap<>();

  public void contains(String group, String child) {
    if (!group.equals(child)) {
      children.computeIfAbsent(group, k -> new LinkedHashSet<>()).add(child);
      parents.computeIfAbsent(child, k -> new LinkedHashSet<>()).add(group);
    }
  }

  public void alias(String a, String b) {
    add(a, new Value.Ref(b));
    add(b, new Value.Ref(a));
    aliases.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
    aliases.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
  }

  /**
   * Group writes cannot be projected without layout: invalidate descendants, never invent values.
   */
  public void write(String target, Value value) {
    add(target, value);
    if (children.isEmpty()) return;
    Value unknown = new Value.Unknown("Group/overlapping write");
    Set<String> affected = new HashSet<>();
    Deque<String> pending = new ArrayDeque<>();
    pending.add(target);
    while (!pending.isEmpty()) {
      String name = pending.removeFirst();
      if (!affected.add(name)) continue;
      pending.addAll(aliases.getOrDefault(name, Set.of()));
      for (String child : children.getOrDefault(name, Set.of())) {
        add(child, unknown);
        pending.add(child);
      }
    }
    // A child write changes its enclosing groups, but does not write unrelated siblings.
    Set<String> seen = new HashSet<>(affected);
    for (String name : affected) pending.addAll(parents.getOrDefault(name, Set.of()));
    while (!pending.isEmpty()) {
      String name = pending.removeFirst();
      if (!seen.add(name)) continue;
      add(name, unknown);
      pending.addAll(parents.getOrDefault(name, Set.of()));
      for (String alias : aliases.getOrDefault(name, Set.of())) {
        pending.add(alias);
        // Without offsets, any field of the overlapping view might have changed.
        Deque<String> overlap = new ArrayDeque<>();
        overlap.add(alias);
        Set<String> visited = new HashSet<>();
        while (!overlap.isEmpty()) {
          String item = overlap.removeFirst();
          if (!visited.add(item)) continue;
          add(item, unknown);
          overlap.addAll(children.getOrDefault(item, Set.of()));
          overlap.addAll(aliases.getOrDefault(item, Set.of()));
        }
      }
    }
  }

  public void add(String target, Value value) {
    producers.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(value);
  }

  public Collection<Value> producers(String name) {
    return producers.getOrDefault(name, Set.of());
  }
}
