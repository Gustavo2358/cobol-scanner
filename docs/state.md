Current state: W0–W9 complete; focal review remediation validated for the same PR #1.
Artifact source commit: 7d4f5ec32712a2ec00787f04c726f9d90c3ceab2 (code, tests and raw evidence).
Reviewed baseline: 11f2ed588d9966c7b6594e57468360df886fbcbc.
The following metadata-only commit pins this build; no new roadmap or PR.

Validation: 108 tests via mvn clean verify, 57 new cases; RED logs preserved.
Standalone JAR: Java 21, exact golden JSON; SHA-256 and size in release.json.
Corpus: 10 sources, 125/125 expected candidates, 0 misses/extras; 3 OK / 7 PARTIAL / 0 ERROR.
Gold and frozen inputs unchanged; no stubs for unavailable includes.
Performance: same JAR, heap 512 MiB, 150k LOC and 1000 programs pass without OOM;
benchmark outputs unchanged and no gross runtime/RSS regression.

Corrected: comments before COPY detection, inherited nested replacements, conservative
COPY OF/IN, READ INTO variants, SET level-88 parent producers and audited writers.
Nested inherited + local REPLACING clauses are an explicit conservative PARTIAL;
group/overlapping writes use Unknown without layout, CFG, IR or general COBOL AST.
The exact manually observed comment snippet was not provided; independent minimal
RED witnesses and fixed/free/comment-entry coverage are recorded with that limitation.

Historical recovery tags remain unchanged: plan-b-mvp and plan-b-backup-rc1.
Known limits: human gold validation pending, unavailable includes, declared unsupported semantics.
Next: review the remediation in PR #1; CI status is attached to the exact pushed HEAD.
No merge or auto-merge authorized/performed. Other workspace projects remained read-only.
