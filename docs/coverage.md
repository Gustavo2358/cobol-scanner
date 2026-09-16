# Cobertura

| Construção | Estado |
|---|---|
| CLI, JSON schema 1, JAR | implementado W0 |
| Normalização/COPY | implementado W1; fixed/free, continuação fixed, COPY aninhado/REPLACING |
| CALL/CICS | implementado; literal e variável via resolver comum |
| VALUE/MOVE/resolver | implementado W3; transitivo, ciclos, memoização por raiz |
| ASSIGN/SQL INCLUDE | implementado W4; includes transitivos, SQLCA/SQLDA, DCLGEN por conteúdo/diretório |
| Tabelas SQL | implementado W5; FROM/JOIN/listas/INSERT/UPDATE/DELETE/MERGE, quoted names |
| Hardening de referências | condicionado ao corpus W6 |
| Batch limitado e benchmark | planejado W7 |

Entrada externa é unresolved por definição. Controle de fluxo é fora de escopo.

CTEs podem aparecer como candidatos. SQL dinâmico marca PARTIAL; catálogo não é resolvido.
