# Cobertura

| Construção | Estado |
|---|---|
| CLI, JSON schema 1, JAR | implementado W0 |
| Normalização/COPY | implementado W1; fixed/free, continuação fixed, COPY aninhado/REPLACING |
| CALL/CICS | implementado; literal e variável via resolver comum |
| VALUE/MOVE/resolver | implementado W3; transitivo, ciclos, memoização por raiz |
| ASSIGN/SQL INCLUDE | implementado W4; includes transitivos, SQLCA/SQLDA, DCLGEN por conteúdo/diretório |
| Tabelas SQL | implementado W5; FROM/JOIN/listas/INSERT/UPDATE/DELETE/MERGE, quoted names |
| Hardening de referências | implementado parcialmente W6 (detalhes abaixo) |
| Batch limitado e benchmark | implementado W7; workers/fila/cache limitados, métricas, proteção explícita |

Entrada externa é unresolved por definição. Controle de fluxo é fora de escopo.

CTEs podem aparecer como candidatos. SQL dinâmico marca PARTIAL; catálogo não é resolvido.

| Construção W6 | Estado |
|---|---|
| OF/IN, subscritos | alias conservador por nome base; homônimos podem inflar candidatos |
| REDEFINES | alias bidirecional de itens nomeados; sem layout/offset de grupos |
| Refmod de leitura | offset/comprimento constantes; dinâmicos unresolved |
| Refmod de escrita | preserva candidatos anteriores + incomplete |
| STRING | concatenação cartesiana; SIZE/literal/SPACE como delimitador |
| STRING WITH POINTER | unresolved |
| Group moves / SET condition-names | não implementado; SET marca producer unknown |
| PIC padding/truncamento, INITIALIZE, funções | sem simulação de layout; escritores detectados unknown |

STRING não simula bytes remanescentes do receptor. Os valores são candidatos de
construção textual. Não se afirma equivalência com execução COBOL.

W8: continuação de literal fixed inclui os espaços até a coluna 72. Comprimento
de refmod variável não causa crash. ADD/SUBTRACT/MULTIPLY/DIVIDE, INSPECT e UNSTRING
marcam produtores afetados como unknown. Limites de valor/candidatos são explícitos;
concatenação explosiva não produz resultado falsamente completo.

Qualificação: 51 testes incluindo gold CardDemo (10 fontes), zero miss no gold
elaborado pelo agente. Aprovação humana do gold pendente. Includes indisponíveis
mantêm sete fontes PARTIAL. Veja qualification/README.md e resultados brutos.

Pré-release: MOVE aceita vírgulas entre receivers; SQL distingue FROM de funções
(EXTRACT/SUBSTRING) e FOR UPDATE OF de dependências de tabela. COPY REPLACING
contabiliza crescimento no orçamento de expansão. ReleaseRegressionTest contém
os três casos RED→GREEN e a equivalência isolada de CALL/LINK/XCTL qualificados.
