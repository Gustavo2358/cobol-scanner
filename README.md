# COBOL Dependency Scanner — Plano B

Inventário de dependências COBOL em **um único JAR**, sem instalação do Plano A.
Identifica programas via CALL e CICS LINK/XCTL, external file names em SELECT ASSIGN,
tabelas SQL, COPYs transitivos e SQL INCLUDEs/DCLGENs. Java 17 ou superior; Maven 3.8+
apenas para construir. Jackson Core é a única biblioteca de runtime, já incluída no JAR.

## Construir e executar

```sh
mvn clean verify
java -jar target/cobol-dependency-scan.jar \
  --source src/test/resources/fixtures/complete.cbl \
  --output dependencies.json
```

Para distribuir, copie somente `target/cobol-dependency-scan.jar`.
O [exemplo completo](src/test/resources/fixtures/complete.cbl) tem
[JSON esperado](src/test/resources/fixtures/complete.json) verificado byte a byte.

```sh
java -Xmx512m -jar cobol-dependency-scan.jar \
  --source ./fontes \
  --copy-dir ./copybooks \
  --sql-include-dir ./sql-includes \
  --dclgen-dir ./dclgens \
  --source-format fixed \
  --charset UTF-8 \
  --threads 4 \
  --output dependencies.json \
  --metrics metrics.tsv
```

A pasta de saída deve existir. Diretórios de includes podem ser repetidos, na ordem
de precedência desejada. Membros são buscados diretamente nesses diretórios e depois
na pasta do arquivo que os inclui, sem percorrer subpastas implicitamente. Extensões
aceitas: nome exato, `.cpy`, `.cbl`, `.cob`, `.inc`, sem distinção de caixa.
`--dclgen-dir` também resolve SQL INCLUDE e classifica membros encontrados como DCLGEN;
`DECLARE ... TABLE` no conteúdo é a segunda forma de classificação. SQLCA/SQLDA são
built-ins e não DCLGENs. Membros indisponíveis permanecem listados e abrem PARTIAL.

Em diretórios de fontes, a descoberta é recursiva para `.cbl`, `.cob` e `.cobol`,
sem distinção de caixa. Um arquivo explícito pode ter qualquer extensão. O resultado
é por arquivo raiz; múltiplos programas/nested programs no mesmo arquivo são unidos
conservadoramente. Não há análise interprocedural nem separação completa de escopos.

## Contrato e estados

[Schema JSON v1](docs/dependencies.schema.json): `schemaVersion` e array `programs`
contendo `source`, `programs`, `externalFileNames`, `tables`, `copybooks`, `sqlIncludes`,
`dclgens`, `programResolutionIncomplete` e `scanStatus`. Arrays são ordenados/deduplicados;
a saída não contém timestamps nem detalhes internos. Literais de programas preservam
caixa, removendo somente espaços finais; identificadores COBOL não literais são uppercase.
SQL preserva identificadores entre aspas; nomes de tabela sem aspas viram uppercase.

| Estado / código | Significado |
|---|---|
| `OK` / exit 0 | nenhuma lacuna detectada dentro das capacidades declaradas |
| `PARTIAL` / exit 1 | candidatos conhecidos preservados; há resolução/expansão incompleta |
| `ERROR` / exit 1 | uma fonte não pôde ser lida/processada; outras continuam |
| exit 2 | argumentos, configuração, descoberta ou saída falharam |

Diagnósticos vão para stderr. `programResolutionIncomplete` se refere aos programas;
SQL dinâmico pode produzir PARTIAL sem alterar esse booleano. Lista vazia com incomplete
não significa ausência de dependências. `externalFileNames` não são datasets/JCL resolvidos.
JSON só substitui o arquivo de destino após concluir o lote. Falha de uma fonte ainda
produz resultado para ela. Caminhos raiz são relativos ao diretório informado; em modo
arquivo único, `source` é o basename. Resultados independem do número de workers.

## Análise de valores

VALUE e MOVE criam relações de produção. O resolver parte somente dos targets de CALL,
LINK e XCTL; conhece concatenação STRING, slices estáticos, aliases e referências básicas.
Um literal em DISPLAY, comentário ou SQL não vira programa sem relação com um sink.
Atribuições em caminhos inalcançáveis ou depois do CALL continuam elegíveis por projeto.

[Coverage](docs/coverage.md) é a matriz oficial. Limites deliberados incluem semântica
de layout/PIC padding/truncamento em atribuições e group moves, SET condition-name TO FALSE,
funções, refmod dinâmico, STRING WITH POINTER, bibliotecas proprietárias/GRBE/IMS,
SQL dinâmico e catálogo DB2. CTEs podem ser table candidates. Qualificações/subscritos
colapsam para nomes base; homônimos podem acrescentar candidatos. A ferramenta é um
scanner tolerante, não um validador completo de COBOL: unknowns detectáveis preservam
candidatos e incompletude, mas `OK` não é prova formal de cobertura de toda a linguagem.

Fixed format usa colunas 8–72, remove comentários `*`/`/`, inclui linhas `D` de forma
conservadora e suporta continuação `-`; literais continuados incluem espaços até coluna
72. Free format preserva linhas e comentários `*>`. `>>SOURCE ... FIXED/FREE` alterna
formato no arquivo. Tabs na área fixa não têm expansão de colunas específica de compilador;
comment entries fixed da Identification Division são removidas antes de procurar COPY.
Formas de continuação free com `&` e diretivas complexas não são suportadas.
COPY REPLACING cobre palavras/literais e pseudo-texto exato por tokens, sem LEADING/TRAILING
ou REPLACE global. O contexto é herdado por COPYs aninhados; duas cláusulas REPLACING
na mesma cadeia abrem PARTIAL. COPY OF/IN library-name também abre PARTIAL, sem
escolher biblioteca por ordem de diretórios. EXEC SQL/CICS é preservado até os coletores;
outras regiões são opacas.

## Recursos e performance

| Opção | Padrão / comportamento |
|---|---|
| `--source-format` | `fixed`; alternativa `free` |
| `--charset` | `UTF-8`; qualquer charset disponível na JVM, por exemplo `IBM1047` |
| `--threads` | min(processadores, 4); permitido 1–64 |
| `--max-source-bytes` | heap máximo / (24 × threads), por fonte + includes/expansão |
| `--max-include-depth` | 128, configurável 1–256; excesso vira PARTIAL |
| `--max-candidates` | 4096 por valor e na união de programas por fonte; excesso abre incomplete |
| `--max-value-chars` | 65536 por valor construído; excesso abre incomplete |
| `--metrics` | TSV opcional com LOC/bytes da raiz e tempos de preparação, scan, resolução e total |

Se uma fonte exceder o orçamento, retorna ERROR explícito; se um include exceder, os
outros fatos sobrevivem com PARTIAL. Aumente `-Xmx`, reduza workers ou ajuste o orçamento
apenas conhecendo a capacidade do host. Não há truncamento silencioso. A proteção é um
orçamento conservador, não garantia contra toda forma de exaustão do sistema operacional.
A fila mantém até 2 × workers fontes em voo; o cache LRU guarda apenas texto bruto,
com limite de heap/16. Arquivos devem permanecer imutáveis durante a execução.

[Benchmark e evidências](benchmark/README.md): 150 mil LOC em uma fonte e mil fontes
sintéticos, com heap de 512 MiB. Não é SLA para codebases arbitrários. Reprodução:

```sh
python3 -B benchmark/run.py
python3 -B qualification/run.py
```

[Qualificação CardDemo](qualification/README.md): 10 fontes, 125 candidatos esperados,
zero misses/extras no gold revisado pelo agente; **validação humana do gold pendente**.
Sete fontes permanecem PARTIAL por inputs ausentes. DCLGEN resolvido é coberto por fixtures
sintéticos. Os fontes reais foram preservados com licença e NOTICE Apache 2.0.

## Desenvolvimento

[Arquitetura](docs/architecture.md), [estado](docs/state.md) e [plano original](docs/plan.md).
`mvn clean verify` inclui unitários, golden, metamórficos, regressões e corpus local;
a CI executa em Java 17 e verifica o JAR em diretório limpo. Maven mantém cache em
`.cache/m2`, e temporários ficam em `.tmp`; ambos são ignorados pelo Git.

W0–W9 estão registrados em commits. `plan-b-mvp` preserva W5; o release candidate
`plan-b-backup-rc1` mantém o scanner operacional para revisão. Sem merge automático.
