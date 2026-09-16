# Arquitetura

Java 17, Maven, um JAR. Jackson Core é a única dependência de runtime.
Source preparation → micro-lexer → ilhas/fatos/sinks → resolver sob demanda → JSON.
O grafo é de produção de valores, flow-insensitive; não representa execução.
CALL e CICS LINK/XCTL compartilham resolução. Literais sem aresta para sink não entram.

A leitura do preprocessador do Plano A (proleap-poc, SHA
`2c838d47858957a779f185e3df265ee8b2891d8d`) mostrou acoplamento com ANTLR,
SourceMap e diagnostics. Reimplementamos somente os comportamentos necessários,
com testes de equivalência de COPY, sem copiar código ou gramáticas externas.
Não há runtime, build ou contrato compartilhado com o Plano A.

JSON schemaVersion 1 é determinístico, com arrays ordenados e sem timestamps.
`OK` significa ausência de lacuna detectada nas capacidades declaradas, não prova
semântica de completude COBOL. `PARTIAL` preserva candidatos conhecidos; `ERROR`
identifica fonte que não pôde ser lida/processada. Detalhes vão para stderr.
