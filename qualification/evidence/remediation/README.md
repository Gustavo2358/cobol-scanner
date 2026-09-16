# Evidências da remediação do PR #1

Base revisada: `11f2ed588d9966c7b6594e57468360df886fbcbc`.
As evidências anteriores do RC continuam preservadas. Os comandos e os dados desta
pasta não mudam o gold nem os resultados anteriores.

- `red.log`: 50 casos novos, 37 falhas / 0 erros, antes de qualquer alteração de
  produção. Comando: `mvn -o -B -Dtest=CopyRemediationTest,WriterRemediationTest test`.
- `group-writes-red.log`: auditoria posterior das mesmas famílias, antes da correção
  de escritas em campos afetando o grupo; 57 casos, 4 falhas / 0 erros.
- `focused-green.log`: 73 casos incluindo as regressões, ValueTest, HardeningTest e corpus.
- `verify.log`: `mvn -o -B clean verify`, 108 testes / 0 falhas / 0 erros.
- `standalone.json`: somente JAR e fixture em diretório limpo, Java 21, golden byte a byte.
- `qualification.log` e `../../results/remediation/`: JAR real, 10 fontes, gold intacto.
- `benchmark.log` e `../../../benchmark/results/remediation/`: ambos os benchmarks
  oficiais, heap 512 MiB e resource guards inalterados. Os hashes de saída coincidem
  com os resultados anteriores do RC.

## COPY em comentário: investigação antes da correção

Os casos usuais fixed `*`, fixed `/`, free `*>`, inline fixed/free e literal com COPY
já passavam. Não foram tratados como explicação suficiente para o bug manual.
O trecho/comando exato observado pelo usuário não foi fornecido nesta tarefa;
não afirmamos que os casos abaixo sejam a identidade confirmada desse incidente.

`comments-baseline/` preserva os fontes e JSON obtidos com o JAR original, hash
`7145f1ce582bc1e2463c1e884e5e0c417e4e68e9df81a828b338596cfba20f77`.
Comando por caso: `java -jar target/cobol-dependency-scan.jar --source <caso>.cbl
--copy-dir <pasta-do-caso> --source-format <formato> --output <caso>.json`.

| Caso | Formato | Baseline |
|---|---|---|
| fixed-star / fixed-slash / fixed-inline | fixed | só REALCPY, OK |
| free-comment / free-inline | free | só REALCPY, OK |
| free-as-fixed | fixed | FAKECPY + REALCPY, PARTIAL |
| fixed-as-free | free | FAKECPY + REALCPY, PARTIAL |
| comment-entry | fixed | FAKECPY + REALCPY, PARTIAL |

Causas: recortar a sequence area antes de reconhecer `*>` podia apagar o marcador;
no modo free, o indicador fixed podia chegar ao lexer como código; AUTHOR/comment
entry não tinha estado de normalização e seu texto de Area B chegava à expansão.
Não houve filtro de nomes após reconhecer COPY: os comentários são removidos antes
da detecção. Testes verificam FAKECPY ausente (sem lookup/diagnóstico) e existente
(sem expansão), além de tokens normalizados sem FAKECPY.

Fixtures reduzidos permanentes:
`src/test/resources/fixtures/remediation/comment-entry.cbl` e
`src/test/resources/fixtures/remediation/early-floating-comment.cbl`;
a matriz fixed/free/literal/inline fica em CopyRemediationTest. A retomada de código
em Area A e aspas desbalanceadas dentro de comentário também estão cobertas.

## Replacements e writers

O fixture nested-copy reproduz literalmente ROOT → A → B, TARGET → 'SUB0001'.
Contextos ONE/TWO, regra local sem herdada, ciclos, profundidade e crescimento no
budget têm regressões. Para a restrição IBM de uma cláusula REPLACING por cadeia,
herdada + local gera PARTIAL e não expande o membro conflitante. COPY OF/IN registra
FOO, informa qualifier não resolvido e não consulta LIB1/LIB2 arbitrariamente.

WriterRemediationTest cobre READ direto/NEXT/PREVIOUS/LOCK, nível 88 associado ao
pai e somente conditions selecionadas, múltiplos SETs/receivers, VALUE ALL,
MOVE CORRESPONDING, ROUNDED, GIVING/REMAINDER, contadores e pointers, grupos,
REDEFINES e refmod. Nenhuma semântica de layout/fluxo foi introduzida; writers sem
modelo produzem Unknown nos valores relacionados ao sink e preservam os conhecidos.
A auditoria por família e seus limites estão em docs/coverage.md.
