package io.github.gustavo2358.cobolscan.resolve;
import io.github.gustavo2358.cobolscan.fact.*;
import java.util.*;
/** Explicit evaluation stack; reference-only cycles are traversed as finite alias closures. */
public final class ValueResolver {
    public record Resolution(SortedSet<String> values,boolean incomplete) {}
    private final ValueFacts facts;
    private final Map<Value,Resolution> memo=new HashMap<>();
    private final int maxCandidates;
    private int visitedCount;
    public ValueResolver(ValueFacts facts) { this(facts,4096); }
    public ValueResolver(ValueFacts facts,int maxCandidates) { this.facts=facts; this.maxCandidates=maxCandidates; }
    public int visitedCount() { return visitedCount; }
    private static final class Frame {
        final Value value; final List<Value> children; final List<Resolution> resolved=new ArrayList<>();
        boolean cycle; int next;
        Frame(Value value,List<Value> children) { this.value=value; this.children=children; }
    }
    public Resolution resolve(Value root) {
        if(memo.containsKey(root)) return memo.get(root);
        Deque<Frame> stack=new ArrayDeque<>(); Set<Value> active=new HashSet<>();
        stack.push(new Frame(root,children(root))); active.add(root);
        while(!stack.isEmpty()) {
            Frame f=stack.peek();
            if(f.next<f.children.size()) {
                Value child=f.children.get(f.next++);
                if(memo.containsKey(child)) f.resolved.add(memo.get(child));
                else if(active.contains(child)) { f.cycle=true; f.resolved.add(empty(true)); }
                else { stack.push(new Frame(child,children(child))); active.add(child); }
                continue;
            }
            Resolution result=evaluate(f); stack.pop(); active.remove(f.value);
            // A context-truncated result must never poison another sink's memo.
            if(!f.cycle) memo.put(f.value,result);
            if(stack.isEmpty()) return result;
            Frame parent=stack.peek(); parent.resolved.add(result); parent.cycle|=f.cycle;
        }
        throw new IllegalStateException("Empty resolver stack");
    }
    private List<Value> children(Value value) {
        if(value instanceof Value.Ref ref) {
            List<Value> leaves=new ArrayList<>(); Set<String> seen=new HashSet<>(); Deque<String> todo=new ArrayDeque<>(); todo.add(ref.name());
            while(!todo.isEmpty()) {
                String name=todo.removeLast(); if(!seen.add(name)) continue; visitedCount++;
                Collection<Value> producers=facts.producers(name);
                if(producers.isEmpty()) leaves.add(new Value.Unknown("No producer"));
                for(Value p:producers) { if(p instanceof Value.Ref r) todo.add(r.name()); else leaves.add(p); }
            }
            return leaves;
        }
        if(value instanceof Value.Concat c) return c.parts();
        if(value instanceof Value.Slice s) return List.of(s.source());
        if(value instanceof Value.Delimited d) return List.of(d.source());
        return List.of();
    }
    private Resolution evaluate(Frame f) {
        SortedSet<String> values=new TreeSet<>(); boolean incomplete=f.cycle;
        for(Resolution r:f.resolved) incomplete|=r.incomplete;
        if(f.value instanceof Value.Literal l) values.add(l.text());
        else if(f.value instanceof Value.Unknown) incomplete=true;
        else if(f.value instanceof Value.Concat) {
            values.add("");
            for(Resolution part:f.resolved) {
                SortedSet<String> next=new TreeSet<>();
                outer: for(String prefix:values) for(String suffix:part.values) {
                    if(next.size()>=maxCandidates) { incomplete=true; break outer; }
                    next.add(prefix+suffix);
                }
                values=next;
            }
        } else if(f.value instanceof Value.Slice s) {
            if(s.start()==null || s.length()==null || s.start()<1 || s.length()==0 || s.length() < -1) incomplete=true;
            else for(String v:f.resolved.get(0).values) {
                long end=s.length()==-1 ? v.length() : (long)s.start()-1+s.length();
                if(s.start()-1>=v.length() || end>v.length()) incomplete=true;
                else values.add(v.substring(s.start()-1,(int)end));
            }
        } else if(f.value instanceof Value.Delimited d) {
            for(String v:f.resolved.get(0).values) { int pos=d.delimiter().isEmpty() ? -1 : v.indexOf(d.delimiter()); values.add(pos<0?v:v.substring(0,pos)); }
        } else {
            for(Resolution r:f.resolved) for(String v:r.values) {
                if(values.size()<maxCandidates || values.contains(v)) values.add(v); else incomplete=true;
            }
        }
        return new Resolution(Collections.unmodifiableSortedSet(values),incomplete || values.isEmpty());
    }
    private static Resolution empty(boolean incomplete) { return new Resolution(Collections.emptySortedSet(),incomplete); }
}
