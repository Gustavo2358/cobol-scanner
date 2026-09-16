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
| Group writes | sem layout; Unknown nos campos/grupos afetados e views sobrepostas |
| SET condition-name TO TRUE | primeiro VALUE do nível 88 produz valor no item-pai; não selecionados não alimentam o pai |
| SET condition-name TO FALSE / VALUE não resolvido | Unknown no item-pai |
| READ INTO | receiver localizado na ilha do statement, incluindo NEXT/PREVIOUS RECORD e WITH NO LOCK |
| COPY OF/IN library-name | membro registrado, qualifier não resolvido → PARTIAL; não busca arquivo arbitrário |
| COPY REPLACING aninhado | herda contexto; local sem herdado funciona; duas cláusulas na cadeia → PARTIAL |
| Comentários COPY/CALL e strings COPY | nenhuma dependência; fixed `*`/`/`, free/inline `*>`, comment entries fixed |
| PIC padding/truncamento, INITIALIZE, funções | sem simulação de layout; escritores detectados unknown |

STRING não simula bytes remanescentes do receptor. Os valores são candidatos de
construção textual. Não se afirma equivalência com execução COBOL.

W8: continuação de literal fixed inclui os espaços até a coluna 72. Comprimento
de refmod variável não causa crash. ADD/SUBTRACT/MULTIPLY/DIVIDE, INSPECT e UNSTRING
marcam produtores afetados como unknown. Limites de valor/candidatos são explícitos;
concatenação explosiva não produz resultado falsamente completo.

Qualificação: 108 testes incluindo gold CardDemo (10 fontes), zero miss no gold
elaborado pelo agente. Aprovação humana do gold pendente. Includes indisponíveis
mantêm sete fontes PARTIAL. Veja qualification/README.md e resultados brutos.

Pré-release: MOVE aceita vírgulas entre receivers; SQL distingue FROM de funções
(EXTRACT/SUBSTRING) e FOR UPDATE OF de dependências de tabela. COPY REPLACING
contabiliza crescimento no orçamento de expansão. ReleaseRegressionTest contém
os três casos RED→GREEN e a equivalência isolada de CALL/LINK/XCTL qualificados.

Auditoria focal dos writers existentes (sem novas famílias de statements):

| Família | Produção / limite conservador verificado |
|---|---|
| VALUE | literal inicial; ALL não modelado → Unknown; nível 88 não inicializa o pai sozinho |
| MOVE | literal/ref transitiva, vários receivers e vírgulas; ALL/CORRESPONDING → Unknown |
| STRING | concatenação e delimitador; palavra reservada dentro de literal permanece valor; pointer → Unknown no destino e no pointer |
| ACCEPT | Unknown no receiver externo |
| READ INTO | Unknown no receiver, tolerando cláusulas anteriores a INTO |
| COMPUTE | Unknown em todos os receivers, incluindo ROUNDED |
| INITIALIZE | Unknown nos receivers/grupos; não simula defaults/layout |
| SET | nível 88 associado ao pai; demais receivers múltiplos → Unknown |
| INSPECT | REPLACING/CONVERTING → Unknown no item; TALLYING → Unknown nos contadores |
| ADD/SUBTRACT/MULTIPLY/DIVIDE | Unknown nos receivers efetivos, GIVING/REMAINDER, listas e ROUNDED |
| UNSTRING | Unknown nos receivers INTO, COUNT/DELIMITER IN, POINTER e TALLYING |
| Escrita parcial/armazenamento compartilhado | refmod → Unknown na base; grupo/campo e REDEFINES propagam incompletude sem calcular offsets |

CopyRemediationTest e WriterRemediationTest acrescentam 57 casos, com evidências
[RED→GREEN](../qualification/evidence/remediation/README.md). A qualificação e os
benchmarks do mesmo JAR estão em `qualification/results/remediation` e
`benchmark/results/remediation`; o gold e os inputs congelados não mudaram.
A propriedade verificada é a incompletude dos writers reconhecidos que não podemos
resolver. Isso não afirma cobertura de qualquer sintaxe/dialeto COBOL desconhecido.
