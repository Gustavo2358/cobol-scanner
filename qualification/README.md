# Qualificação W8

`mvn clean verify` inclui comparação de todas as categorias do corpus com `gold.json`.
`python3 -B qualification/run.py` usa o JAR real, confere SHA-256 dos inputs e preserva
JSON, métricas, stderr, RSS e resultados no diretório de saída. O corpus é uma cópia byte a byte
somente para testes, com licença/NOTICE originais. Nenhum arquivo do Plano A foi alterado.

CardDemo upstream: `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`.
Snapshot local estudado: proleap-poc `2c838d47858957a779f185e3df265ee8b2891d8d`.
A provenance importada é histórica: caminhos descritos nela referem-se ao Plano A;
`source-sha256.json` identifica os arquivos efetivamente consumidos aqui.

Gold elaborado por revisão dos fontes pelo agente, antes da comparação final,
**pendente de validação humana**. Os valores incluem sobreaproximações semânticas
permitidas: self-call via atribuições posteriores e código inalcançável. Não são uma
lista de chamadas efetivamente executáveis. Includes ausentes não foram inventados.
A tabela histórica de COPYs tinha omissões: COTRTUPC contém CSMSG02Y (linha 274) e
CSUSR01Y (277); ambos estão no gold físico revisado.

Resultado: 10 fontes, 12.186 LOC, 630.809 bytes; 0 ERROR, 3 OK, 7 PARTIAL.
125 pares fonte/categoria/candidato esperados, todos encontrados, sem extras:
22 programas, 6 external file names, 2 tabelas, 88 COPYs, 7 SQL INCLUDEs.
Recall/precision no gold: 100%; inflação 1,0. Não extrapolar essas métricas para
inputs ausentes ou para a semântica completa do runtime. Taxa de fontes com resolução
de programas incompleta: 70%. O corpus real não contém DCLGEN resolvido disponível;
a classificação/expansão é coberta por StructuralTest e SqlTest sintéticos.

Falha encontrada: refmod `BUFFER(1:WS-LENGTH)` provocava unboxing de null, descartando
CALLs independentes. `CorpusRegressionTest` reproduziu RED antes da correção; o baseline
bruto com sete ERROR permanece em results/baseline.*. O mesmo teste protege padding
de literais continued fixed e incompletude de aritmética/STRING POINTER não suportados.
Também foi adicionado limite explícito de tamanho de valor e cache do resolver para
impedir crescimento exponencial de STRING, mantendo incomplete em vez de OOM.

Cobertura representada: CALL literal e variável, CICS, SQL estático, COPYs aninhados,
ASSIGN e padrões combinados. Grandes fontes e 1000 programas são evidência sintética
em benchmark/. IMS/DLI e catálogos estão fora do produto. Não há comparação de velocidade
com Plano A nem aprovação humana simulada.

W9: o [binário final](../docs/release.json) repetiu a qualificação em **0,227 s**,
pico RSS **109.364 KiB**, mesmo gold e mesmos 125 candidatos. Saídas imutáveis:
[results/release-rc1](results/release-rc1/summary.json). Preflight e baseline anteriores
permanecem preservados. Novas execuções escrevem por padrão em `.tmp/qualification-results`;
`--output-dir` permite salvar outro conjunto dentro do repositório.

A suíte final contém 51 testes; os últimos protegem vírgulas em MOVE, contextos SQL
que não são tabelas e amplificação de COPY REPLACING. Nenhum gate de Plano A foi
executado: o produto é independente e os outros projetos permaneceram somente leitura.
