# COBOL Dependency Scanner — Plano B

## 0. Propósito deste documento

Este documento é a especificação executável para a construção de um analisador COBOL pragmático, standalone e orientado a inventário de dependências.

O agente responsável pela implementação deve ser capaz de ler este documento e executar o projeto integralmente como uma **long-running task**, checkpoint por checkpoint, sem precisar redescobrir a arquitetura ou transformar o produto em uma versão reduzida do analisador principal.

O objetivo não é construir um compilador, um analisador semântico geral ou uma infraestrutura de dataflow completa.

O objetivo é construir uma aplicação pequena, robusta e rápida que responda, para cada programa COBOL:

* quais programas ou subprogramas ele pode chamar;
* quais programas podem ser acionados por CICS `LINK` ou `XCTL`;
* quais external file names aparecem em `SELECT ... ASSIGN`;
* quais tabelas SQL são referenciadas;
* quais copybooks são utilizados;
* quais SQL INCLUDEs / DCLGENs são utilizados.

O resultado final deve ser um JSON simples.

---

# 1. Contexto arquitetural

Existe um analisador principal — o Plano A — cuja arquitetura busca alta fidelidade semântica.

Seu pipeline inclui conceitos como:

```text
COBOL
↓
preprocessamento
↓
parsing
↓
AST / produto semântico
↓
lowering
↓
IR
↓
CFG
↓
dataflow
↓
resolução de valores
↓
dependências
```

Essa arquitetura continua sendo adequada para o produto high-end.

O Plano B existe por outro motivo:

> fornecer rapidamente um inventário útil de dependências caso o Plano A ainda não esteja suficientemente maduro para os deadlines da entrega.

O Plano B não deve competir arquiteturalmente com o Plano A.

Ele deve responder uma pergunta deliberadamente mais simples.

Plano A:

> Qual valor pode chegar a determinado ponto de execução, considerando a semântica do programa?

Plano B:

> Quais valores presentes ou derivados estaticamente no programa possuem uma cadeia semântica de produção que pode alimentar um ponto de dependência?

A segunda pergunta permite eliminar quase toda a infraestrutura de análise de controle.

---

# 2. Filosofia central

A filosofia do projeto é:

> **Semantic overapproximation, not lexical overapproximation.**

Em português:

> **Superaproximação semântica, e não superaproximação lexical.**

Falsos positivos decorrentes de controle de fluxo são aceitáveis.

Falsos positivos completamente desconectados da dependência não são aceitáveis.

Exemplo aceitável:

```cobol
IF CONDICAO-IMPOSSIVEL
    MOVE 'PGM00001' TO WS-PGM
END-IF

CALL WS-PGM
```

O scanner pode retornar:

```text
PGM00001
```

mesmo que o ramo jamais execute.

Isso é deliberado.

Exemplo inaceitável:

```cobol
DISPLAY 'FIM'.

CALL WS-PGM.
```

`FIM` jamais pode aparecer como candidato a programa simplesmente porque é um literal textual.

Um literal somente pode chegar ao resultado se existir uma relação semântica entre ele e um dependency sink.

---

# 3. Princípio arquitetural

O sistema deve compreender apenas as partes do COBOL necessárias para descobrir dependências.

Todo o restante deve ser ignorado de forma tolerante.

Conceitualmente:

```text
             COBOL SOURCE
                  │
                  ▼
          Source Preparation
                  │
                  ▼
         Tolerant Island Scan
         ┌────────┼─────────┐
         │        │         │
         ▼        ▼         ▼
       Sinks   Value Facts  Direct
                         Dependencies
         │        │
         └────┬───┘
              ▼
     Demand-driven Resolver
              │
              ▼
       Dependency Result
              │
              ▼
             JSON
```

Este projeto NÃO possui pipeline de compilador.

---

# 4. Regra de ouro de escopo

Se uma nova feature exigir compreender **quando** determinado statement executa, ela provavelmente pertence ao Plano A.

O Plano B pode compreender:

```text
X recebe Y
X redefine Y
X é composto de A + B
CALL utiliza X
CICS LINK utiliza X
```

O Plano B não deve compreender:

```text
este IF executa?
este PERFORM chega neste parágrafo?
este GO TO torna esta definição alcançável?
qual definição domina este CALL?
qual caminho do CFG é executável?
```

Essas perguntas são explicitamente proibidas no projeto.

---

# 5. Não objetivos

O agente NÃO deve introduzir:

* AST geral de COBOL;
* produto semântico equivalente ao `proleap-poc`;
* IR;
* CFG;
* CDG;
* dominators;
* reaching definitions tradicional;
* constant propagation sobre CFG;
* fixed-point sobre blocos básicos;
* análise path-sensitive;
* análise flow-sensitive;
* interpretação de `IF`;
* interpretação de `EVALUATE`;
* interpretação de `PERFORM`;
* interpretação de `GO TO`;
* análise interprocedural;
* resolução de catálogo DB2;
* resolução de JCL;
* resolução de datasets;
* banco de dados;
* servidor HTTP;
* Spring;
* microsserviços;
* arquitetura de plugins;
* event bus;
* dependency injection framework;
* Neptune;
* Aurora;
* DynamoDB;
* múltiplos repositórios;
* múltiplos executáveis.

A aplicação deverá resultar em:

> **um repositório, um build Maven e um único JAR executável.**

---

# 6. Stack tecnológica

## Linguagem

Java 17 ou versão LTS já padronizada pelos repositórios existentes, desde que não aumente o requisito operacional.

Java 17 é suficiente para:

* records;
* sealed types;
* pattern matching básico;
* collections modernas;
* bom desempenho;
* compatibilidade empresarial ampla.

## Build

Maven.

## Runtime dependencies

Manter o conjunto mínimo possível.

Permitidas inicialmente:

* ANTLR Runtime, exclusivamente quando necessário ao preprocessamento;
* Jackson Core ou Jackson Databind para JSON;
* JUnit 5 somente em testes.

Não introduzir Guava, Spring, Lombok, Picocli ou bibliotecas semelhantes sem necessidade concreta.

O parsing de argumentos de linha de comando pode ser implementado diretamente.

## Empacotamento

Maven Shade Plugin ou mecanismo equivalente.

Resultado:

```text
target/cobol-dependency-scan.jar
```

Execução:

```bash
java -jar cobol-dependency-scan.jar ...
```

---

# 7. Estrutura recomendada do repositório

```text
cobol-dependency-scan/
├── AGENTS.md
├── README.md
├── pom.xml
├── docs/
│   ├── architecture.md
│   ├── state.md
│   └── coverage.md
├── src/
│   ├── main/
│   │   └── java/
│   │       └── .../
│   │           ├── Main.java
│   │           ├── source/
│   │           ├── scan/
│   │           ├── fact/
│   │           ├── resolve/
│   │           ├── extract/
│   │           └── output/
│   └── test/
│       ├── java/
│       └── resources/
│           └── fixtures/
└── benchmark/
```

Não criar uma interface para cada classe.

Interfaces devem existir apenas quando houver pelo menos dois comportamentos reais ou quando um boundary externo justificar isso.

---

# 8. Modelo conceitual

O sistema trabalha com cinco conceitos principais.

## 8.1 Source

Um arquivo COBOL raiz após normalização e expansão controlada de includes.

## 8.2 Dependency Sink

Um ponto do código que representa uma dependência.

Exemplos:

```cobol
CALL WS-PGM
```

```cobol
CALL 'PGM00001'
```

```cobol
EXEC CICS LINK PROGRAM(WS-PGM) END-EXEC
```

```cobol
EXEC CICS XCTL PROGRAM('PGM00002') END-EXEC
```

## 8.3 Value Fact

Uma relação simples de produção de valor.

Exemplos:

```text
"PGM00001" → WS-PGM
WS-A → WS-PGM
concat(WS-A, WS-B) → WS-PGM
```

## 8.4 Direct Dependency

Dependência que não exige resolução de valores.

Exemplos:

```text
COPY CPCLIENT
SELECT FILE-X ASSIGN TO DDINPUT
SQL FROM CUSTOMER
```

## 8.5 Resolver

Componente que parte de um sink baseado em variável e percorre apenas os fatos necessários para descobrir seus possíveis valores.

---

# 9. Modelo de Program Sink

Criar um modelo conceitualmente equivalente a:

```java
enum ProgramSinkKind {
    COBOL_CALL,
    CICS_LINK,
    CICS_XCTL
}
```

Alvo:

```java
sealed interface ProgramTarget {
    record Literal(String value) implements ProgramTarget {}
    record DataRef(String name) implements ProgramTarget {}
}
```

Sink:

```java
record ProgramSink(
    ProgramSinkKind kind,
    ProgramTarget target
) {}
```

Não armazenar informações que não sejam úteis à resolução.

Source location pode existir para testes e diagnóstico interno, mas não precisa aparecer no JSON final.

---

# 10. CICS é requisito de primeira classe

CICS NÃO é extensão futura.

O primeiro release funcional deve compreender:

```text
EXEC CICS LINK PROGRAM(...)
EXEC CICS XCTL PROGRAM(...)
```

Tanto:

```cobol
PROGRAM('ABC00001')
```

quanto:

```cobol
PROGRAM(WS-PGM)
```

devem funcionar.

O segundo caso deve usar exatamente o mesmo value resolver utilizado por:

```cobol
CALL WS-PGM
```

Arquiteturalmente:

```text
CALL WS-X
      │
      ▼
 ProgramSink(WS-X)

CICS LINK PROGRAM(WS-X)
                 │
                 ▼
          ProgramSink(WS-X)

CICS XCTL PROGRAM(WS-X)
                 │
                 ▼
          ProgramSink(WS-X)

                 │
                 ▼
             resolve()
```

Não implementar engines diferentes para COBOL CALL e CICS.

---

# 11. Lightweight Value Graph

O coração semântico da aplicação é um grafo muito pequeno de produção de valores.

Exemplo:

```cobol
01 WS-A PIC X(8).
01 WS-B PIC X(8) VALUE 'PGM00002'.

MOVE 'PGM00001' TO WS-A.
MOVE WS-B TO WS-A.

CALL WS-A.
```

Fatos:

```text
literal("PGM00001") → WS-A
literal("PGM00002") → WS-B
WS-B                 → WS-A
```

Sink:

```text
CALL → WS-A
```

Resolver:

```text
resolve(WS-A)
 ├── PGM00001
 └── resolve(WS-B)
       └── PGM00002
```

Resultado:

```text
PGM00001
PGM00002
```

---

# 12. Flow-insensitive por projeto

O grafo não registra em qual ponto de execução a atribuição ocorre para fins de reachability.

Considere:

```cobol
CALL WS-PGM.

GOBACK.

PARAGRAFO-INATINGIVEL.
    MOVE 'ABC00001' TO WS-PGM.
```

O Plano B pode retornar:

```text
ABC00001
```

Isso não é bug.

É uma consequência deliberada da análise flow-insensitive.

Por outro lado:

```cobol
DISPLAY 'ABC00001'
```

não cria nenhuma aresta e não pode influenciar o resultado.

---

# 13. Demand-driven por projeto

Não propagar valores para todas as variáveis antecipadamente.

Primeiro coletar fatos.

Depois partir somente das variáveis utilizadas pelos sinks.

Exemplo:

```text
WS-PGM <- WS-A
WS-A   <- WS-B
WS-B   <- "ABC00001"

WS-LOG <- "FIM"
WS-X   <- WS-Y
WS-Y   <- "IGNORAR"
```

Se o único sink for:

```text
CALL WS-PGM
```

o resolver visita somente:

```text
WS-PGM
WS-A
WS-B
```

`WS-LOG`, `WS-X` e `WS-Y` não precisam ser resolvidos.

---

# 14. Algoritmo base do resolver

Usar DFS ou algoritmo equivalente com memoização.

Pseudoalgoritmo:

```text
resolve(variable):

    if variable in memo:
        return memo[variable]

    if variable in currentStack:
        return unresolved-cycle

    add variable to currentStack

    result = empty

    for producer in producersOf(variable):

        if producer is literal:
            result += literal

        if producer is variable:
            result += resolve(producer)

        if producer is supported expression:
            result += evaluate(producer)

        if producer is unsupported:
            result.incomplete = true

    remove variable from currentStack

    memo[variable] = result

    return result
```

Ciclos de:

```cobol
MOVE A TO B
MOVE B TO A
```

não podem causar recursão infinita.

Não é necessário fixed-point sobre CFG.

---

# 15. Semântica inicial obrigatória de valores

O release mínimo deve compreender:

## VALUE

```cobol
01 WS-PGM PIC X(8) VALUE 'ABC00001'.
```

gera:

```text
"ABC00001" → WS-PGM
```

## MOVE literal

```cobol
MOVE 'ABC00001' TO WS-PGM
```

gera:

```text
"ABC00001" → WS-PGM
```

## MOVE variável

```cobol
MOVE WS-A TO WS-B
```

gera:

```text
WS-A → WS-B
```

## MOVE com múltiplos destinos

```cobol
MOVE WS-A TO WS-B WS-C
```

gera:

```text
WS-A → WS-B
WS-A → WS-C
```

Essas três operações devem suportar cadeias transitivas arbitrárias.

---

# 16. Semântica de valores de segundo nível

Depois que o núcleo estiver funcionando, adicionar apenas construções que tenham evidência real de uso no corpus.

Prioridade recomendada:

```text
STRING
reference modification
REDEFINES
qualified data names
subscripts
group moves
SET relevante
```

Não implementar todas simultaneamente.

---

# 17. STRING

Exemplo:

```cobol
MOVE 'ABC' TO WS-PREFIX.
MOVE '00001' TO WS-SUFFIX.

STRING
    WS-PREFIX
    WS-SUFFIX
    INTO WS-PGM
END-STRING.

CALL WS-PGM.
```

Representar:

```text
concat(WS-PREFIX, WS-SUFFIX) → WS-PGM
```

Resolver:

```text
ABC + 00001
```

Resultado:

```text
ABC00001
```

Não é necessário modelar todas as possibilidades de `STRING` no primeiro incremento.

Suportar primeiro:

* literais;
* data refs;
* concatenação direta;
* `INTO`.

Se `DELIMITED BY` introduzir comportamento ainda não implementado, o resultado deve ser conservador ou marcado incompleto.

---

# 18. Data references

A aplicação não deve construir uma Symbol Table COBOL completa.

Ela deve utilizar uma representação leve.

Suportar progressivamente:

```text
FIELD
FIELD OF GROUP
FIELD IN GROUP
FIELD(index)
FIELD(start:length)
```

Estratégia conservadora:

* nomes COBOL são normalizados para uppercase;
* `IN` e `OF` são equivalentes;
* subscripts podem inicialmente ser removidos para resolução do elemento base;
* qualificações podem gerar aliases conservadores.

Exemplo:

```text
PGM-NAME OF CONTROL-BLOCK
```

pode registrar aliases:

```text
PGM-NAME OF CONTROL-BLOCK
PGM-NAME
```

Isso pode aumentar falsos positivos em caso de nomes duplicados.

Esse comportamento é aceitável.

Perder valores devido a uma qualificação reconhecível não é desejável.

---

# 19. REDEFINES

Não modelar layout completo de memória.

Ao encontrar:

```cobol
01 WS-A PIC X(8).
01 WS-B REDEFINES WS-A PIC X(8).
```

criar relação de alias conservadora:

```text
WS-A ↔ WS-B
```

Assim, valores conhecidos para uma representação podem ser considerados candidatos para a outra.

Não calcular offsets complexos no primeiro release.

Se `REDEFINES` envolver grupos incompatíveis ou estruturas complexas, usar sobreaproximação conservadora.

---

# 20. Source preparation

O Source Preparation deve:

1. normalizar line endings;
2. tratar fixed/free format conforme configuração;
3. preservar literais;
4. preservar blocos `EXEC SQL`;
5. preservar blocos `EXEC CICS`;
6. reconhecer e expandir COPY;
7. permitir COPY aninhado;
8. suportar formas já estáveis de `COPY REPLACING`;
9. registrar todos os copybooks utilizados;
10. informar copybooks não encontrados;
11. evitar que falha em um include descarte todo o arquivo quando for possível continuar.

---

# 21. Reaproveitamento do preprocessamento existente

O comportamento comprovadamente funcional do preprocessamento atual pode ser reaproveitado.

Porém:

> o novo JAR NÃO deve depender da aplicação `proleap-poc` em runtime.

Preferências, em ordem:

1. extrair/copiar apenas o código mínimo necessário de preprocessamento para o novo repositório;
2. preservar testes relevantes;
3. preservar licenças/copyrights de gramáticas externas;
4. remover dependências de AST, SourceMap complexo ou Semantic Product;
5. manter o resultado final standalone.

Não criar um novo módulo compartilhado entre Plano A e Plano B durante a primeira entrega.

Isso criaria acoplamento organizacional desnecessário.

Duplicação pequena e consciente é aceitável neste projeto.

---

# 22. Preservação de EXEC SQL e EXEC CICS

O preprocessador não pode transformar:

```cobol
EXEC CICS ...
END-EXEC
```

ou:

```cobol
EXEC SQL ...
END-EXEC
```

em marcadores que removam o conteúdo antes dos coletores correspondentes.

O output preparado precisa continuar permitindo:

```text
CicsScanner
SqlScanner
```

operarem normalmente.

Essa propriedade deve possuir testes explícitos.

---

# 23. Island Scanner

Depois do preprocessamento, utilizar um scanner tolerante.

Ele não é um parser COBOL completo.

Ele reconhece somente “ilhas” relevantes.

Ilhas iniciais:

```text
CALL
MOVE
VALUE
COPY
SELECT ... ASSIGN
EXEC CICS LINK
EXEC CICS XCTL
EXEC SQL
EXEC SQL INCLUDE
data description com REDEFINES
```

Todo o restante é “water” e é ignorado.

---

# 24. Micro-lexer

Regex global sobre o arquivo inteiro não deve ser a base da implementação.

Implementar um tokenizer simples capaz de diferenciar pelo menos:

```text
WORD
STRING
NUMBER
punctuation
period
parentheses
EXEC regions
comments
```

O tokenizer deve compreender aspas simples e duplas e escapes COBOL relevantes.

Exemplo obrigatório:

```cobol
DISPLAY 'CALL ABC00001'.
```

não pode gerar um `CALL`.

Comentários também não podem produzir fatos.

---

# 25. CALL

Suportar no primeiro release:

```cobol
CALL 'ABC00001'
```

```cobol
CALL "ABC00001"
```

```cobol
CALL WS-PGM
```

Cláusulas posteriores como:

```text
USING
BY REFERENCE
BY CONTENT
BY VALUE
RETURNING
ON EXCEPTION
```

não são relevantes para identificar o program target.

O scanner deve obter apenas o primeiro operando de programa.

---

# 26. CICS

Suporte obrigatório:

```cobol
EXEC CICS
    LINK PROGRAM('ABC00001')
END-EXEC
```

```cobol
EXEC CICS
    LINK PROGRAM(WS-PGM)
END-EXEC
```

```cobol
EXEC CICS
    XCTL PROGRAM('ABC00001')
END-EXEC
```

```cobol
EXEC CICS
    XCTL PROGRAM(WS-PGM)
END-EXEC
```

Outras opções CICS não devem confundir o reconhecimento.

Exemplo:

```cobol
EXEC CICS LINK
    PROGRAM(WS-PGM)
    COMMAREA(WS-COMM)
    LENGTH(100)
    RESP(WS-RESP)
END-EXEC
```

o único program target é `WS-PGM`.

---

# 27. Arquivos / external file names

Reconhecer:

```cobol
SELECT CUSTOMER-FILE
    ASSIGN TO CUSTOMERDD.
```

Resultado:

```text
CUSTOMERDD
```

O produto não deve afirmar que encontrou o dataset ou JCL DD statement.

O conceito retornado é:

```text
externalFileNames
```

No ambiente batch do consumidor isso servirá como candidato ao DDName.

Não é necessário verificar se o arquivo é aberto ou utilizado.

Uma declaração não utilizada pode ser retornada.

Isso é um falso positivo aceitável.

---

# 28. COPY

Sempre registrar o membro de:

```cobol
COPY ABCXYZ.
```

mesmo se não puder expandi-lo.

Exemplo de resultado:

```json
"copybooks": [
  "ABCXYZ"
]
```

Se o copybook contiver outro COPY, o resultado final deve incluir dependências transitivas.

Exemplo:

```text
PROGRAM
 └─ COPY A
       └─ COPY B
```

resultado:

```text
A
B
```

Não apenas `A`.

---

# 29. SQL INCLUDE e DCLGEN

Reconhecer:

```cobol
EXEC SQL
    INCLUDE DCLCLIENTE
END-EXEC
```

Sempre registrar:

```text
sqlIncludes += DCLCLIENTE
```

Classificação como DCLGEN:

1. se o membro for encontrado em diretório configurado como DCLGEN, classificá-lo;
2. se o conteúdo contiver padrão compatível com `DECLARE ... TABLE`, classificá-lo;
3. built-ins conhecidos como `SQLCA` e `SQLDA` não devem ser classificados como DCLGEN;
4. para include não resolvido e não conhecido, uma política conservadora pode registrá-lo como `dclgenCandidate`.

O JSON pode expor separadamente:

```text
sqlIncludes
dclgens
```

---

# 30. Tabelas SQL

Dentro de:

```text
EXEC SQL
...
END-EXEC
```

extrair candidatos de tabela.

Suporte obrigatório inicial:

```text
FROM
JOIN
INSERT INTO
UPDATE
DELETE FROM
MERGE INTO
MERGE ... USING
```

Suportar:

```sql
FROM A, B
```

e não apenas a primeira tabela.

Suportar nomes qualificados:

```text
SCHEMA.TABLE
DATABASE.SCHEMA.TABLE
```

Preservar corretamente identificadores SQL entre aspas.

Unquoted identifiers podem ser normalizados para uppercase.

---

# 31. SQL semântica deliberadamente limitada

Não resolver:

* views até tabelas-base;
* synonyms;
* catálogo DB2;
* aliases externos;
* stored procedures externas;
* packages;
* access path;
* SQL optimizer.

CTEs e derivados podem inicialmente produzir algum ruído.

Exemplo:

```sql
WITH X AS (...)
SELECT * FROM X
```

Se `X` aparecer inicialmente como table candidate, isso é tolerável no Plano B.

Pode ser refinado posteriormente se representar ruído relevante no corpus.

---

# 32. Dynamic SQL

Exemplo:

```cobol
MOVE 'SELECT * FROM CLIENTE' TO WS-SQL.
EXEC SQL PREPARE S1 FROM :WS-SQL END-EXEC.
```

Uma extensão futura pode reutilizar o value resolver para recuperar SQL literal local.

Não bloquear o primeiro release por isso.

Quando SQL vier inteiramente de entrada externa:

```text
ACCEPT
READ
argumento externo
```

não há como enumerar estaticamente seus objetos.

Marcar a categoria como incompleta quando detectável.

---

# 33. Dependência externa e unresolved

Exemplo:

```cobol
ACCEPT WS-PGM.
CALL WS-PGM.
```

O resultado não deve inventar nomes.

Registrar internamente:

```text
programTargetUnresolved = true
```

O JSON deve possuir uma forma mínima de comunicar isso.

Exemplo:

```json
{
  "programs": [],
  "programResolutionIncomplete": true
}
```

Isso evita que lista vazia seja interpretada como:

> “este programa definitivamente não possui subprogramas”.

---

# 34. JSON de saída

Contrato mínimo recomendado por programa:

```json
{
  "source": "X0AB0001.cbl",
  "programs": [
    "ABC00001",
    "ABC00002"
  ],
  "externalFileNames": [
    "DDINPUT",
    "DDOUTPUT"
  ],
  "tables": [
    "CAD.CLIENTE",
    "CAD.CONTA"
  ],
  "copybooks": [
    "CPBASE",
    "CPCONTA"
  ],
  "sqlIncludes": [
    "DCLCLIENTE",
    "SQLCA"
  ],
  "dclgens": [
    "DCLCLIENTE"
  ],
  "programResolutionIncomplete": false,
  "scanStatus": "OK"
}
```

Não adicionar por padrão:

* linha de ocorrência;
* confidence score;
* evidence chains;
* AST node;
* CFG node;
* explanation;
* provenance detalhada.

Essas informações podem existir internamente durante testes, mas não fazem parte do contrato público inicial.

---

# 35. Determinismo

Dado o mesmo input e configuração, o output deve ser byte-for-byte determinístico, exceto por metadata temporal caso exista.

Preferencialmente nem incluir timestamp no arquivo principal.

Arrays devem ser:

* deduplicados;
* normalizados;
* ordenados deterministicamente.

Isso facilita:

* testes golden;
* diff;
* auditoria;
* execução repetível.

---

# 36. Batch

A ferramenta deve operar tanto sobre:

```text
um arquivo
```

quanto:

```text
uma árvore inteira de fontes
```

CLI conceitual:

```bash
java -jar cobol-dependency-scan.jar \
    --source ./src-cobol \
    --copy-dir ./copybooks \
    --sql-include-dir ./dclgens \
    --output dependencies.json
```

Opções desejáveis:

```text
--source
--copy-dir
--sql-include-dir
--output
--charset
--source-format
--threads
```

Sem arquivo de configuração obrigatório.

---

# 37. Output de lote

Não acumular todos os resultados em memória.

Usar streaming JSON.

Conceitualmente:

```json
{
  "schemaVersion": 1,
  "programs": [
    {...},
    {...},
    {...}
  ]
}
```

Jackson `JsonGenerator` pode escrever cada resultado conforme o programa termina.

---

# 38. Paralelismo

Primeiro tornar a execução correta single-thread.

Paralelismo entra somente no checkpoint de performance.

Quando introduzido:

* concorrência entre programas;
* não dentro de um único arquivo;
* fila limitada;
* número de workers configurável;
* default conservador.

Exemplo:

```text
min(processors, 4)
```

Não iniciar centenas de análises simultaneamente.

---

# 39. Cache de copybooks

Pode existir cache de:

```text
conteúdo bruto
conteúdo normalizado
```

Não cachear ingenuamente a expansão final apenas pelo nome do copybook.

Isto é incorreto:

```cobol
COPY ABC REPLACING X BY Y.
COPY ABC REPLACING X BY Z.
```

O mesmo membro gera duas expansões diferentes.

Cache contextual somente se a chave incluir os parâmetros de replacement.

---

# 40. Performance

O sistema não deve prometer números antes do benchmark.

Entretanto, como requisito mínimo para o Plano B, ele deve atingir ou superar os objetivos de entrega já existentes:

* analisar um fonte de aproximadamente 150k LOC sem OOM e muito abaixo do teto de minutos;
* processar aproximadamente 1000 programas dentro da janela operacional já prevista para o projeto;
* memória proporcional ao programa atualmente analisado, e não ao codebase inteiro.

O benchmark deve reportar:

```text
LOC
bytes
tempo de preprocessamento
tempo de scan
tempo de resolução
tempo total
peak/RSS quando mensurável
LOC/s
```

O objetivo aspiracional é que o Plano B seja substancialmente mais rápido que o Plano A, mas isso deve ser demonstrado e não presumido.

---

# 41. Invariantes fundamentais

Os testes devem proteger estas invariantes.

## Invariante 1

Adicionar literal irrelevante não altera dependências.

Antes:

```cobol
MOVE 'PGM00001' TO WS-PGM.
CALL WS-PGM.
```

Depois:

```cobol
DISPLAY 'FIM'.
MOVE 'PGM00001' TO WS-PGM.
CALL WS-PGM.
```

Resultado idêntico.

## Invariante 2

Control flow não remove candidatos.

```cobol
IF X = Y
    MOVE 'A' TO WS-PGM
ELSE
    MOVE 'B' TO WS-PGM
END-IF
```

pode gerar:

```text
A
B
```

## Invariante 3

Transitividade funciona.

```text
"A" → X → Y → Z → CALL
```

gera `A`.

## Invariante 4

Ciclo não trava.

```text
A → B
B → A
```

termina.

## Invariante 5

CALL e CICS compartilham resolução.

Se ambos usam `WS-PGM`, devem obter os mesmos possíveis valores.

## Invariante 6

Copy expansion não apaga SQL ou CICS.

## Invariante 7

Falha em uma fonte não interrompe todo o lote.

---

# 42. Fixture manifesto

Este teste deve existir desde o início do resolver:

```cobol
       WORKING-STORAGE SECTION.

       01 WS-PGM-A PIC X(8).
       01 WS-PGM-B PIC X(8)
          VALUE 'SUB00002'.

       PROCEDURE DIVISION.

           DISPLAY 'FIM'.

           MOVE 'SUB00001' TO WS-PGM-A.

           IF X = Y
               MOVE WS-PGM-B TO WS-PGM-A
           END-IF.

           CALL WS-PGM-A.

           EXEC CICS LINK
               PROGRAM(WS-PGM-A)
           END-EXEC.

           GOBACK.
```

Resultado obrigatório:

```json
{
  "programs": [
    "SUB00001",
    "SUB00002"
  ]
}
```

E explicitamente:

```java
assertFalse(programs.contains("FIM"));
```

Se esse teste quebrar, a filosofia do produto foi violada.

---

# 43. Estratégia de testes

A suíte deve possuir quatro categorias.

## Golden tests

Fonte completo → JSON esperado.

## Unit tests

Tokenização, reconhecimento de statements, resolver e normalização.

## Metamorphic tests

Transformações que não devem modificar resultado:

* whitespace;
* quebra de linha;
* uppercase/lowercase onde COBOL permite;
* inserção de comentário;
* inserção de DISPLAY irrelevante;
* expansão manual de COPY equivalente.

## Regression tests

Todo bug real encontrado no corpus deve resultar em fixture mínimo antes da correção.

---

# 44. Testes de cobertura semântica

Manter `docs/coverage.md` com matriz compacta.

Exemplo:

```text
Construct                         Status
------------------------------------------------
CALL literal                     supported
CALL variable                    supported
CICS LINK literal                supported
CICS LINK variable               supported
CICS XCTL literal                supported
CICS XCTL variable               supported
VALUE                            supported
MOVE literal                     supported
MOVE variable                    supported
MOVE multiple targets            supported
STRING                           partial
REDEFINES                        partial
Reference modification           planned
External input                   unresolved by definition
```

Esse arquivo é a fonte oficial de verdade sobre o que o Plano B resolve.

Não deixar capabilities implícitas.

---

# 45. Métrica principal

A métrica principal do Plano B é recall sobre o corpus conhecido.

Durante a qualificação:

> todo candidato esperado no gold corpus precisa ser encontrado.

Falsos positivos são medidos, mas inicialmente não são o principal blocker.

Entretanto, ruído lexical desconectado é bug.

Exemplos de bugs:

```text
DISPLAY 'FIM'
DISPLAY 'ERRO'
literal SQL que não alimenta sink
comentário contendo CALL
```

entrarem como programa.

---

# 46. Hierarquia de falsos positivos

Existem falsos positivos aceitáveis e inaceitáveis.

### Aceitável

Literal realmente atribuído ao target, mas em código inalcançável.

### Aceitável

Duas variáveis homônimas são confundidas por ausência de resolução completa de escopo.

### Aceitável

Alias conservador via REDEFINES produz possibilidade adicional.

### Aceitável

Uma tabela CTE aparece como table candidate em estágio inicial.

### Inaceitável

Literal sem relação com sink.

### Inaceitável

Texto de comentário interpretado como statement.

### Inaceitável

String contendo a palavra CALL interpretada como CALL.

---

# 47. Checkpoint W0 — Fundação e contrato

## Objetivo

Criar o repositório e congelar as decisões arquiteturais.

## Implementar

* Maven project;
* Java runtime definido;
* `Main`;
* JSON schema;
* estrutura de pacotes;
* `AGENTS.md`;
* `README.md`;
* `docs/architecture.md`;
* `docs/state.md`;
* `docs/coverage.md`;
* fixture runner;
* Maven Shade;
* JUnit;
* CI mínima.

## Comportamento

Executar:

```bash
java -jar ... --source fixture.cbl --output result.json
```

e produzir JSON válido, mesmo ainda vazio.

## Gate

```text
mvn verify
```

verde.

JAR executável.

Nenhuma dependência do Plano A em runtime.

---

# 48. Checkpoint W1 — Source Preparation

## Objetivo

Construir a base textual confiável.

## Implementar

* fixed/free normalization;
* comentários;
* continuações necessárias;
* preservação de strings;
* integração mínima do preprocessamento existente;
* COPY;
* nested COPY;
* COPY REPLACING suportado;
* coleta de nomes dos copybooks;
* preservação de `EXEC SQL`;
* preservação de `EXEC CICS`;
* includes não encontrados não derrubam todo o lote.

## Testes obrigatórios

```text
COPY simples
COPY nested
COPY REPLACING
copybook ausente
CICS dentro de copybook
SQL dentro de copybook
string contendo COPY
comment contendo COPY
```

## Gate

O texto expandido é adequado para todos os scanners seguintes.

Nenhum AST COBOL foi introduzido.

---

# 49. Checkpoint W2 — Program sinks diretos

## Objetivo

Entregar a primeira capacidade realmente útil.

## Implementar

```text
CALL literal
CALL variable
CICS LINK literal
CICS LINK variable
CICS XCTL literal
CICS XCTL variable
```

Neste checkpoint, alvos variáveis podem aparecer como unresolved.

## Exemplo

Entrada:

```cobol
CALL 'ABC00001'.

EXEC CICS LINK
    PROGRAM('XYZ00001')
END-EXEC.
```

Saída:

```text
ABC00001
XYZ00001
```

## Gate

`DISPLAY 'CALL ABC'` não cria dependência.

Comentários não criam dependência.

CICS funciona já neste checkpoint.

---

# 50. Checkpoint W3 — Lightweight Value Flow

## Objetivo

Resolver targets dinâmicos sem CFG.

## Implementar

```text
VALUE
MOVE literal → target
MOVE variable → target
MOVE source → multiple targets
transitive resolution
cycle detection
memoization
demand-driven lookup
```

## Teste manifesto

Obrigatório passar o teste `DISPLAY 'FIM'`.

## Testes

```text
1 hop
10 hops
100 hops
cycle
diamond graph
multiple definitions
unreachable assignment
assignment depois do CALL
```

Assignments depois do CALL ainda podem ser considerados.

Isso é comportamento esperado.

## Gate

Todos os sinks baseados em variável usam o resolver.

Nenhuma implementação de CFG aparece no código.

---

# 51. Checkpoint W4 — Files, SQL INCLUDE e DCLGEN

## Objetivo

Cobrir dependências estruturais simples.

## Implementar

```text
SELECT ... ASSIGN
externalFileNames
EXEC SQL INCLUDE
sqlIncludes
DCLGEN classification
```

## Testes

```text
ASSIGN multiline
ASSIGN dentro de copybook
SQLCA
SQLDA
DCLGEN resolvido
DCLGEN não resolvido
nested includes quando configurados
```

## Gate

JSON passa a conter:

```text
externalFileNames
copybooks
sqlIncludes
dclgens
```

---

# 52. Checkpoint W5 — SQL Table Scanner

## Objetivo

Entregar inventário de tabelas.

## Implementar

```text
FROM
JOIN
comma FROM list
INSERT INTO
UPDATE
DELETE FROM
MERGE INTO
MERGE USING
schema-qualified names
quoted identifiers
nested SQL regions
```

## Testes

```sql
SELECT FROM A
SELECT FROM A, B
A JOIN B
INSERT INTO A
UPDATE A
DELETE FROM A
MERGE INTO A USING B
```

Também testar SQL dentro de copybooks/DCLGENs.

## Gate

O sistema entrega todas as categorias originalmente necessárias para o Plano B.

Nesse ponto já existe um **MVP funcional de backup**.

---

# 53. CHECKPOINT DE ENTREGA — MVP BACKUP

Após W5, congelar uma versão executável.

Tag conceitual:

```text
plan-b-mvp
```

O agente deve validar:

```text
programs            ✅
CICS programs       ✅
external file names ✅
tables              ✅
copybooks           ✅
DCLGEN/sql include  ✅
single JAR          ✅
batch               pelo menos funcional
```

Mesmo que checkpoints posteriores continuem, este estado deve permanecer recuperável.

---

# 54. Checkpoint W6 — Semantic Hardening

## Objetivo

Aumentar recall de targets dinâmicos sem entrar em control flow.

Implementar incrementalmente e nessa ordem, caso o corpus justifique:

### W6A — qualified data names

```text
X OF A
X IN A
```

### W6B — subscripts

```text
TABLE-X(I)
```

Inicialmente colapsando para a base quando apropriado.

### W6C — REDEFINES

Alias conservador.

### W6D — reference modification

Exemplo:

```cobol
MOVE WS-BLOCK(5:8) TO WS-PGM
```

Quando origem puder ser resolvida, extrair substring possível.

### W6E — STRING

Concatenação.

### W6F — group moves

Somente se necessário para fixtures reais.

### W6G — SET / outras atribuições

Somente quando houver impacto comprovado em dependency target.

## Gate

Cada subcheckpoint deve adicionar fixture real ou sintética que demonstrava miss antes da implementação.

Não implementar construções “porque podem ser úteis algum dia”.

---

# 55. Checkpoint W7 — Batch e performance

## Objetivo

Transformar o MVP em ferramenta utilizável sobre codebase grande.

## Implementar

* recursive directory walking;
* filtering por extensões;
* processamento independente por fonte;
* streaming JSON;
* parallel workers;
* bounded queue;
* copybook cache seguro;
* métricas;
* benchmark runner;
* proteção contra arquivos gigantes;
* isolamento de falhas por fonte.

## Proibido

Limites artificiais silenciosos como:

```text
máximo 10k statements
máximo 100k tokens
máximo 32 MiB
```

que simplesmente descartem conteúdo válido.

Proteções devem impedir OOM sem produzir resultados silenciosamente incompletos.

## Gate

Executar corpus sintético grande sem OOM.

---

# 56. Checkpoint W8 — Corpus Qualification

## Objetivo

Validar o scanner contra programas reais representativos.

Separar corpus em categorias:

```text
CALL literal
CALL variável
CICS
SQL pesado
COPY pesado
DCLGEN
ASSIGN
grandes fontes
múltiplos padrões combinados
```

Criar gold set humano.

Para cada fonte:

```text
expected programs
expected files
expected tables
expected copybooks
expected dclgens
```

Medir:

```text
recall
precision
candidate inflation
unresolved rate
runtime
memory
```

Prioridade:

```text
1. falso negativo conhecido
2. falha/crash
3. ruído absurdo
4. performance
```

---

# 57. Gate de recall

Para o gold corpus mantido pelo projeto:

> Nenhuma dependência esperada pertencente às construções declaradas como suportadas pode ficar ausente.

Se ocorrer miss:

1. reduzir para fixture mínimo;
2. adicionar regression test;
3. identificar se é bug ou capability não suportada;
4. corrigir somente se estiver dentro da filosofia do Plano B.

Não adicionar CFG para corrigir um único miss.

---

# 58. Checkpoint W9 — Release Backup

## Objetivo

Produzir uma versão operacional pronta para entrega.

## Entregáveis

```text
cobol-dependency-scan.jar
README operacional
JSON schema documentado
coverage matrix
benchmark report
known limitations
sample invocation
sample output
```

## Release gate

```text
mvn clean verify
```

verde.

Todos os golden tests verdes.

Corpus qualification executado.

Nenhum P0/P1 conhecido.

JAR executado do zero em diretório limpo.

---

# 59. Extensões pós-release

Somente após o backup estar utilizável.

Possíveis incrementos:

```text
GRBE / MONITOR
IMS
EXEC SQL CALL
dynamic SQL local
CICS LOAD
CICS START
outros monitores proprietários
```

Cada extensão deve entrar como novo sink ou novo producer de valor, não como ampliação geral do compilador.

---

# 60. GRBE

Por relevância ao ambiente alvo, GRBE provavelmente será a primeira extensão pós-MVP.

A arquitetura deve permitir:

```text
GrbeProgramSink
```

reutilizando:

```text
ValueResolver
```

Não implementar um framework de plugins antecipadamente.

Uma classe/coletor específico é suficiente.

Exemplo conceitual:

```text
GRBE scanner
     │
     ▼
ProgramTarget(WS-PGM)
     │
     ▼
ValueResolver
```

A regra proprietária determina qual campo representa o program target.

A resolução de valor continua genérica.

---

# 61. Critério para aceitar nova semântica

Toda nova capability deve responder SIM às três perguntas:

1. Essa construção pode produzir diretamente uma dependência ou alimentar um dependency sink?
2. Ela pode ser modelada sem raciocinar sobre reachability de control flow?
3. Existe fixture ou corpus real demonstrando valor?

Se qualquer resposta for NÃO, não implementar.

---

# 62. Anti-scope-creep guardrail

Se durante a implementação alguém propuser:

```text
“vamos construir um CFG só para…”
```

a resposta padrão deve ser:

> não neste projeto.

Se alguém propuser:

```text
“precisamos saber qual MOVE realmente chega no CALL…”
```

isso pertence ao Plano A.

Se alguém propuser:

```text
“podemos criar um IR simplificado…”
```

não.

Se alguém propuser:

```text
“vamos criar uma AST mínima…”
```

somente se o objeto for literalmente uma estrutura local equivalente aos fatos já definidos; não criar uma representação geral da linguagem.

---

# 63. Tratamento de unsupported

Construção desconhecida não deve derrubar o programa.

O scanner deve:

```text
continuar
+
preservar dependências já encontradas
+
marcar incompletude quando ela puder afetar resolução
```

Exemplo:

```cobol
COMPUTE WS-PGM = alguma-coisa
CALL WS-PGM
```

Se `COMPUTE` não for suportado para program targets:

```text
programResolutionIncomplete = true
```

Não retornar literal aleatório para compensar.

---

# 64. Princípio de falha

É melhor retornar:

```json
{
  "programs": ["ABC00001"],
  "programResolutionIncomplete": true
}
```

que:

```json
{
  "programs": [
    "ABC00001",
    "FIM",
    "ERRO",
    "CLIENTE",
    "SIM",
    "NAO"
  ]
}
```

O scanner deve aumentar recall através de **relações semânticas**, não através de ruído lexical.

---

# 65. Estratégia de desenvolvimento vertical

Cada checkpoint deve terminar com uma aplicação executável.

Não fazer:

```text
primeiro todas as abstrações
depois todos os models
depois todos os parsers
depois integração
```

Fazer:

```text
input real
↓
feature mínima
↓
resultado JSON
↓
testes
↓
próximo slice
```

O primeiro `CALL` deve aparecer no JSON cedo.

O primeiro CICS também.

---

# 66. Estado do projeto

`docs/state.md` deve permanecer curto.

Formato sugerido:

```text
Current checkpoint: W3

Completed:
- W0
- W1
- W2

Current:
- MOVE/value graph

Known blockers:
- none

Next:
- finish transitive resolver
- fixture cycles
- run mvn verify

Last green commit:
<sha>
```

Não transformar `state.md` em diário de desenvolvimento.

---

# 67. Documentação

Manter apenas documentação que sobreviva à implementação:

```text
README.md
AGENTS.md
docs/architecture.md
docs/state.md
docs/coverage.md
```

Não criar dezenas de tasklists permanentes.

Informações absorvidas pelo código ou pelos documentos oficiais devem ser removidas de scratch files.

---

# 68. AGENTS.md

Deve orientar agentes futuros a:

* ler `architecture.md`;
* ler `state.md`;
* respeitar non-goals;
* não criar CFG;
* preferir false positive semanticamente justificável a false negative;
* nunca coletar literais globalmente;
* manter CICS como feature obrigatória;
* executar testes antes de commit;
* atualizar `coverage.md` ao adicionar semântica;
* adicionar regression test para bug real;
* não alterar JSON silenciosamente.

---

# 69. Fluxo Git da long-running task

Criar branch dedicada:

```text
feat/plan-b-dependency-scanner
```

Abrir Draft PR cedo.

O agente deve continuar trabalhando no mesmo Draft PR durante a long-running task.

Após cada checkpoint:

```text
mvn clean verify
```

Se verde:

```text
commit
push
update state.md
```

Commits sugeridos:

```text
W0 scaffold standalone dependency scanner
W1 implement source preparation and copy expansion
W2 extract COBOL and CICS program sinks
W3 add flow-insensitive value resolver
W4 extract external file names and SQL includes
W5 extract static SQL table dependencies
W6 harden target value semantics
W7 add batch execution and performance controls
W8 qualify against representative corpus
W9 prepare backup release
```

Não fazer merge automaticamente.

A revisão humana decide o merge.

---

# 70. Regra de checkpoint

O agente NÃO deve começar checkpoint seguinte se:

```text
mvn verify
```

estiver quebrado.

Exceção somente se o próprio checkpoint seguinte for explicitamente a correção de uma infraestrutura quebrada, o que deve ser registrado em `state.md`.

---

# 71. Long-running behavior

O agente deve continuar autonomamente pelos checkpoints.

Não parar depois de:

```text
scaffold
arquitetura
primeiro teste
primeiro commit
```

O objetivo da long-running task é chegar ao release backup funcional.

Quando houver ambiguidade pequena, escolher a solução:

```text
mais simples
mais conservadora
mais facilmente testável
menos acoplada
```

e registrar decisão curta.

Não bloquear a execução aguardando decisão humana sobre detalhe não crítico.

---

# 72. Critérios para escalada humana

Parar para revisão apenas se surgir decisão com impacto estrutural significativo, como:

* necessidade aparente de CFG;
* necessidade aparente de parser COBOL completo;
* incompatibilidade de licença;
* preprocessador existente impossível de isolar;
* mudança incompatível no JSON;
* comportamento real do corpus contradizendo uma premissa fundamental da arquitetura.

Bugs e decisões locais normais devem ser resolvidos pelo agente.

---

# 73. Definição de Done por checkpoint

Um checkpoint só é concluído quando:

```text
código implementado
+
unit tests
+
golden/regression tests pertinentes
+
mvn verify verde
+
JAR executável quando aplicável
+
coverage.md atualizado
+
state.md atualizado
+
commit criado
+
push realizado
```

Não considerar checkpoint concluído apenas porque o código compila.

---

# 74. Definition of Done do projeto

O projeto estará pronto como Plano B quando:

1. um diretório de fontes COBOL puder ser informado;
2. copybooks puderem ser resolvidos;
3. cada fonte gerar um resultado;
4. CALL literal funcionar;
5. CALL variável funcionar para `VALUE`/`MOVE` transitivos;
6. CICS LINK funcionar;
7. CICS XCTL funcionar;
8. `DISPLAY 'FIM'` jamais contaminar targets;
9. external file names forem extraídos;
10. SQL tables forem extraídas;
11. COPYs forem listados;
12. SQL INCLUDE/DCLGENs forem listados;
13. falhas parciais não encerrarem o lote;
14. unresolved targets forem sinalizados;
15. o processamento do corpus alvo não apresentar OOM;
16. o resultado for determinístico;
17. tudo for distribuído em um único JAR.

---

# 75. Arquitetura final desejada

A implementação concluída deve continuar conceitualmente próxima disto:

```text
                      ┌─────────────────────┐
                      │     COBOL file      │
                      └──────────┬──────────┘
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │ Source Preparation  │
                      │ normalize + COPY    │
                      └──────────┬──────────┘
                                 │
                                 ▼
                ┌────────────────────────────────┐
                │        Tolerant Scanner        │
                └──────┬──────────┬──────────────┘
                       │          │
             ┌─────────┘          └─────────┐
             ▼                              ▼
      ┌─────────────┐                ┌─────────────┐
      │Program Sinks│                │ Value Facts │
      │CALL/CICS    │                │MOVE/VALUE   │
      └──────┬──────┘                └──────┬──────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
                    ┌───────────────┐
                    │Value Resolver │
                    │demand-driven  │
                    │flow-insens.   │
                    └───────┬───────┘
                            │
       ┌────────────────────┼─────────────────────┐
       │                    │                     │
       ▼                    ▼                     ▼
   programs         external files        tables/includes
       │                    │                     │
       └────────────────────┼─────────────────────┘
                            ▼
                    ┌───────────────┐
                    │     JSON      │
                    └───────────────┘
```

Se, ao final da implementação, a arquitetura estiver substancialmente mais complicada que esse desenho, o agente deve reavaliar se houve scope creep.

---

# 76. Resumo executivo para o agente

Construa um scanner COBOL standalone em Java cujo objetivo é inventariar dependências.

Não construa um compilador.

Não construa CFG.

Não construa IR.

Use preprocessamento confiável para COPY.

Reconheça somente construções importantes.

`CALL` e CICS `LINK/XCTL` são dependency sinks.

`VALUE`, `MOVE` e posteriormente algumas transformações produzem fatos de valor.

Use um grafo flow-insensitive.

Resolva valores sob demanda.

Aceite valores vindos de código inalcançável.

Nunca aceite literais sem relação semântica com o sink.

Extraia external file names de `ASSIGN`.

Extraia COPYs.

Extraia SQL INCLUDE/DCLGEN.

Extraia tabelas de SQL estático.

Gere JSON determinístico.

Distribua tudo como um único JAR.

Mantenha o projeto pequeno.

A filosofia do produto pode ser resumida em uma única frase:

> **Encontrar todas as dependências estaticamente deriváveis que conseguirmos, aceitando superaproximação de fluxo, mas nunca substituindo semântica por uma bolsa global de literais.**
