package io.github.gustavo2358.cobolscan.resolve;
import io.github.gustavo2358.cobolscan.fact.*;
import java.util.*;
/** Demand-driven traversal with per-root memoization, no JVM recursion. */
public final class ValueResolver {
    public record Resolution(SortedSet<String> values,boolean incomplete) {}
    private final ValueFacts facts;
    private final Map<Value,Resolution> memo=new HashMap<>();
    private int visitedCount;
    public ValueResolver(ValueFacts facts) { this.facts=facts; }
    public int visitedCount() { return visitedCount; }
    public Resolution resolve(Value root) {
        if(memo.containsKey(root)) return memo.get(root);
        SortedSet<String> values=new TreeSet<>(); Set<String> visited=new HashSet<>(); Deque<Value> pending=new ArrayDeque<>(); pending.add(root); boolean incomplete=false;
        while(!pending.isEmpty()) {
            Value v=pending.removeLast();
            if(v instanceof Value.Literal l) values.add(l.text());
            else if(v instanceof Value.Unknown) incomplete=true;
            else if(v instanceof Value.Ref ref && visited.add(ref.name())) {
                visitedCount++; Collection<Value> producers=facts.producers(ref.name());
                if(producers.isEmpty()) incomplete=true; else pending.addAll(producers);
            }
        }
        Resolution result=new Resolution(Collections.unmodifiableSortedSet(values),incomplete || values.isEmpty()); memo.put(root,result); return result;
    }
}
