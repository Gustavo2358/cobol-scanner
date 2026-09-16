package io.github.gustavo2358.cobolscan.scan;

import io.github.gustavo2358.cobolscan.fact.*;
import java.util.*;

public final class FactScanner {
  // Statement/clause boundaries prevent unrelated words from becoming MOVE receivers.
  public static final Set<String> BOUNDARIES =
      Set.of(
          "ACCEPT",
          "ADD",
          "ALLOCATE",
          "ALTER",
          "CALL",
          "CANCEL",
          "CLOSE",
          "COMPUTE",
          "CONTINUE",
          "DELETE",
          "DISPLAY",
          "DIVIDE",
          "ELSE",
          "END-IF",
          "END-MOVE",
          "END-STRING",
          "END-CALL",
          "END-EVALUATE",
          "END-PERFORM",
          "END-READ",
          "END-WRITE",
          "END-COMPUTE",
          "EVALUATE",
          "EXEC",
          "EXIT",
          "FREE",
          "GOBACK",
          "GO",
          "IF",
          "INITIALIZE",
          "INSPECT",
          "MERGE",
          "MOVE",
          "MULTIPLY",
          "OPEN",
          "PERFORM",
          "READ",
          "RELEASE",
          "RETURN",
          "REWRITE",
          "SEARCH",
          "SET",
          "SORT",
          "START",
          "STOP",
          "STRING",
          "SUBTRACT",
          "UNSTRING",
          "WHEN",
          "WRITE",
          "ON",
          "NOT",
          "SIZE",
          "ERROR",
          "INVALID",
          "AT",
          "END",
          "THEN",
          "SECTION",
          "DIVISION",
          "USING",
          "RETURNING",
          "DELIMITED",
          "WITH",
          "POINTER",
          "END-EXEC");

  private static final Set<String> WRITERS =
      Set.of(
          "MOVE",
          "STRING",
          "ACCEPT",
          "READ",
          "COMPUTE",
          "INITIALIZE",
          "SET",
          "INSPECT",
          "ADD",
          "SUBTRACT",
          "MULTIPLY",
          "DIVIDE",
          "UNSTRING");
  private static final Set<String> CLAUSE_ENDS =
      Set.of("ON", "NOT", "AT", "INVALID", "ELSE", "WHEN", "THEN", "END");
  private static final Set<String> STATEMENT_ENDS =
      Set.of(
          "CALL",
          "EXEC",
          "DISPLAY",
          "IF",
          "EVALUATE",
          "PERFORM",
          "GOBACK",
          "GO",
          "STOP",
          "EXIT",
          "WRITE",
          "REWRITE",
          "OPEN",
          "CLOSE",
          "RETURN",
          "SECTION",
          "DIVISION");
  private static final Set<String> MODIFIERS =
      Set.of(
          "ROUNDED",
          "MODE",
          "IS",
          "NEAREST-AWAY-FROM-ZERO",
          "NEAREST-EVEN",
          "NEAREST-TOWARD-ZERO",
          "AWAY-FROM-ZERO",
          "TOWARD-GREATER",
          "TOWARD-LESSER",
          "TRUNCATION",
          "PROHIBITED");

  private record Item(int level, String name) {}

  private record Condition(String parent, Value value) {}

  public ValueFacts scan(List<Token> ts) {
    ValueFacts facts = new ValueFacts();
    Map<String, List<Condition>> conditions = declarations(ts, facts);
    for (int i = 0; i < ts.size(); i++) {
      Token t = ts.get(i);
      if (t.is("EXEC")) {
        while (i < ts.size() && !ts.get(i).is("END-EXEC")) i++;
        continue;
      }
      if (t.kind() != Token.Kind.WORD || !WRITERS.contains(t.upper())) continue;
      int end = statementEnd(ts, i + 1);
      List<Token> clause = ts.subList(i + 1, end);
      switch (t.upper()) {
        case "MOVE" -> move(clause, facts);
        case "STRING" -> string(clause, facts);
        case "READ" -> unknownAfter(clause, "INTO", facts, "READ INTO");
        case "ACCEPT" -> unknown(Operands.read(clause, 0).value(), facts, "ACCEPT");
        case "COMPUTE" ->
            receivers(clause, 0, Set.of("=", "EQUAL"), v -> unknown(v, facts, "COMPUTE"));
        case "INITIALIZE" ->
            receivers(
                clause,
                0,
                Set.of("WITH", "REPLACING", "ALL", "TO", "DEFAULT", "VALUE", "VALUES"),
                v -> unknown(v, facts, "INITIALIZE"));
        case "SET" -> set(clause, conditions, facts);
        case "INSPECT" -> inspect(clause, facts);
        case "UNSTRING" -> unstring(clause, facts);
        default -> arithmetic(t.upper(), clause, facts);
      }
      i = end - 1;
    }
    return facts;
  }

  /**
   * Declaration metadata only: names, containment and level-88 owner/first VALUE; no storage
   * layout.
   */
  private Map<String, List<Condition>> declarations(List<Token> ts, ValueFacts facts) {
    Map<String, List<Condition>> conditions = new HashMap<>();
    List<Map.Entry<String, Value>> initials = new ArrayList<>();
    Deque<Item> ancestors = new ArrayDeque<>();
    String parent = null;
    for (int i = 0; i < ts.size(); i++) {
      if (ts.get(i).is("PROCEDURE")) break;
      if (ts.get(i).is("EXEC")) {
        while (i < ts.size() && !ts.get(i).is("END-EXEC")) i++;
        continue;
      }
      if (ts.get(i).is("SECTION") || ts.get(i).is("FD") || ts.get(i).is("SD")) {
        parent = null;
        ancestors.clear();
      }
      int level = level(ts, i);
      if (level < 0) continue;
      String name = ts.get(i + 1).upper();
      int end = i + 2;
      while (end < ts.size()
          && !ts.get(end).is(".")
          && !ts.get(end).is("PROCEDURE")
          && level(ts, end) < 0) end++;
      List<Token> clause = ts.subList(i + 2, end);
      int valueAt = index(clause, "VALUE", 0);
      if (valueAt < 0) valueAt = index(clause, "VALUES", 0);
      Value initial = new Value.Unknown("Unsupported/missing VALUE");
      if (valueAt >= 0) {
        int v = valueAt + 1;
        if (v < clause.size() && (clause.get(v).is("IS") || clause.get(v).is("ARE"))) v++;
        Value parsed = Operands.read(clause, v).value();
        if (parsed instanceof Value.Literal) initial = parsed;
      }
      if (level == 88) {
        if (parent != null)
          conditions
              .computeIfAbsent(name, k -> new ArrayList<>())
              .add(new Condition(parent, initial));
      } else if (level == 66) {
        parent = null;
      } else {
        parent = name.equals("FILLER") ? null : name;
        while (!ancestors.isEmpty() && ancestors.peek().level() >= (level == 77 ? 1 : level))
          ancestors.pop();
        if (parent != null) {
          if (!ancestors.isEmpty()) facts.contains(ancestors.peek().name(), name);
          ancestors.push(new Item(level == 77 ? 1 : level, name));
          if (valueAt >= 0) initials.add(Map.entry(name, initial));
          int alias = index(clause, "REDEFINES", 0);
          if (alias >= 0 && Operands.read(clause, alias + 1).value() instanceof Value.Ref ref)
            facts.alias(name, ref.name());
        }
      }
      i = end - 1;
    }
    for (var initial : initials) facts.write(initial.getKey(), initial.getValue());
    return conditions;
  }

  private int level(List<Token> ts, int i) {
    if (i + 1 >= ts.size()
        || ts.get(i).kind() != Token.Kind.NUMBER
        || ts.get(i + 1).kind() != Token.Kind.WORD) return -1;
    if (i > 0
        && ts.get(i - 1).line() >= ts.get(i).line()
        && !ts.get(i - 1).is(".")
        && !ts.get(i - 1).is("END-EXEC")) return -1;
    try {
      int n = Integer.parseInt(ts.get(i).value());
      return n >= 1 && n <= 49 || n == 66 || n == 77 || n == 88 ? n : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private int statementEnd(List<Token> ts, int start) {
    int i = start;
    for (; i < ts.size(); i++) {
      Token t = ts.get(i);
      if (t.is(".")) break;
      if (t.kind() == Token.Kind.WORD
          && (WRITERS.contains(t.upper())
              || CLAUSE_ENDS.contains(t.upper())
              || t.upper().startsWith("END-")
              || STATEMENT_ENDS.contains(t.upper()))) break;
    }
    return i;
  }

  private static int index(List<Token> ts, String word, int start) {
    for (int i = start; i < ts.size(); i++) if (ts.get(i).is(word)) return i;
    return -1;
  }

  private void unknown(Value target, ValueFacts facts, String reason) {
    Operands.assign(facts, target, new Value.Unknown(reason));
  }

  private void unknownAfter(List<Token> ts, String word, ValueFacts facts, String reason) {
    int at = index(ts, word, 0);
    if (at >= 0) unknown(Operands.read(ts, at + 1).value(), facts, reason);
  }

  private void receivers(
      List<Token> ts, int start, Set<String> stops, java.util.function.Consumer<Value> writer) {
    for (int i = start; i < ts.size(); ) {
      Token t = ts.get(i);
      if (stops.stream().anyMatch(t::is)) break;
      if (t.is(",") || t.kind() == Token.Kind.WORD && MODIFIERS.contains(t.upper())) {
        i++;
        continue;
      }
      if (!isReceiver(t)) break;
      var receiver = Operands.read(ts, i);
      writer.accept(receiver.value());
      i = receiver.next();
    }
  }

  private void move(List<Token> ts, ValueFacts facts) {
    var source = Operands.read(ts, 0);
    int to = index(ts, "TO", source.next());
    if (to < 0) return;
    Value value =
        to == source.next()
            ? source.value()
            : new Value.Unknown("Unsupported MOVE source/CORRESPONDING");
    receivers(ts, to + 1, Set.of(), v -> Operands.assign(facts, v, value));
  }

  private void set(List<Token> ts, Map<String, List<Condition>> conditions, ValueFacts facts) {
    int to = index(ts, "TO", 0);
    boolean truth = to >= 0 && to + 1 < ts.size() && ts.get(to + 1).is("TRUE");
    if (!ts.isEmpty() && ts.get(0).is("ADDRESS")) {
      unknownAfter(ts, "OF", facts, "SET ADDRESS");
      return;
    }
    receivers(
        ts,
        0,
        Set.of("TO", "UP", "DOWN"),
        target -> {
          if (target instanceof Value.Ref ref && conditions.containsKey(ref.name())) {
            for (Condition condition : conditions.get(ref.name()))
              facts.write(
                  condition.parent(),
                  truth ? condition.value() : new Value.Unknown("SET condition not TO TRUE"));
          } else unknown(target, facts, "SET");
        });
  }

  private void inspect(List<Token> ts, ValueFacts facts) {
    if (index(ts, "REPLACING", 0) >= 0 || index(ts, "CONVERTING", 0) >= 0)
      unknown(Operands.read(ts, 0).value(), facts, "INSPECT");
    int tally = index(ts, "TALLYING", 0);
    if (tally >= 0)
      for (int i = tally + 1; i < ts.size(); i++) {
        var candidate = Operands.read(ts, i);
        if (candidate.next() < ts.size() && ts.get(candidate.next()).is("FOR"))
          unknown(candidate.value(), facts, "INSPECT TALLYING");
      }
  }

  private void arithmetic(String verb, List<Token> ts, ValueFacts facts) {
    int giving = index(ts, "GIVING", 0);
    String receiverClause =
        switch (verb) {
          case "ADD" -> "TO";
          case "SUBTRACT" -> "FROM";
          case "MULTIPLY" -> "BY";
          default -> "INTO";
        };
    int start = giving >= 0 ? giving : index(ts, receiverClause, 0);
    if (start >= 0)
      receivers(ts, start + 1, Set.of("GIVING", "REMAINDER"), v -> unknown(v, facts, verb));
    unknownAfter(ts, "REMAINDER", facts, verb + " REMAINDER");
  }

  private void unstring(List<Token> ts, ValueFacts facts) {
    int into = index(ts, "INTO", 0);
    if (into < 0) return;
    for (int i = into + 1; i < ts.size(); ) {
      Token t = ts.get(i);
      if (t.is(",")
          || t.is("WITH")
          || t.is("IN")
          || t.is("COUNT")
          || t.is("DELIMITER")
          || t.is("TALLYING")
          || t.is("POINTER")) {
        i++;
        continue;
      }
      if (!isReceiver(t)) break;
      var receiver = Operands.read(ts, i);
      unknown(receiver.value(), facts, "UNSTRING");
      i = receiver.next();
    }
  }

  private void string(List<Token> ts, ValueFacts facts) {
    List<Value> parts = new ArrayList<>();
    int i = 0, groupStart = 0;
    int into = index(ts, "INTO", 0);
    if (into < 0) return;
    List<Token> senders = ts.subList(0, into);
    while (i < senders.size()) {
      if (senders.get(i).is(",")) {
        i++;
        continue;
      }
      if (senders.get(i).is("DELIMITED")) {
        i++;
        if (i < senders.size() && senders.get(i).is("BY")) i++;
        if (i < senders.size() && senders.get(i).is("SIZE")) i++;
        else {
          var delimiter = Operands.read(senders, i);
          i = delimiter.next();
          for (int j = groupStart; j < parts.size(); j++)
            parts.set(
                j,
                delimiter.value() instanceof Value.Literal l
                    ? new Value.Delimited(parts.get(j), l.text())
                    : new Value.Unknown("Dynamic STRING delimiter"));
        }
        groupStart = parts.size();
        continue;
      }
      var operand = Operands.read(senders, i);
      parts.add(operand.value());
      i = operand.next();
    }
    var dest = Operands.read(ts, into + 1);
    int pointer = index(ts, "POINTER", dest.next());
    Operands.assign(
        facts,
        dest.value(),
        pointer < 0 ? new Value.Concat(parts) : new Value.Unknown("STRING WITH POINTER"));
    if (pointer >= 0) unknown(Operands.read(ts, pointer + 1).value(), facts, "STRING POINTER");
  }

  static boolean isReceiver(Token t) {
    return t.kind() == Token.Kind.WORD && !BOUNDARIES.contains(t.upper());
  }
}
