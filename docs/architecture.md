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

W6: qualificações e subscritos colapsam conservadoramente para o nome base.
REDEFINES de itens nomeados cria aliases; FILLER nunca é um alias global.
O resolver percorre closures de referências com pilha explícita para expressões.
Não memoiza resultados truncados por ciclos de transformação. Limites de candidatos
preservam os conhecidos e abrem incompletude; não há fixed-point de controle.

Evidência W6: padrões de qualificação/refmod em COACTUPC (corpus CardDemo),
REDEFINES em COCOM01Y/CVCRD01Y e STRING em CBSTM03A/COPAUS1C;
`HardeningTest` adiciona sinks sintéticos para provar o ganho (4 RED antes).
Group moves e SET de condition-names ficam adiados: não foi demonstrado ganho
necessário nos sinks do recorte qualificado. Não calculamos padding/truncamento PIC.

Referências de semântica consultadas:
- [IBM — STRING](https://www.ibm.com/docs/en/cobol-aix/5.1.0?topic=statements-string-statement)
- [IBM — continuation lines](https://www.ibm.com/docs/en/cobol-aix/5.1.0?topic=b-continuation-lines)

W7: concorrência somente entre fontes; até 2 × workers de resultados/futuros em voo.
Emissão segue caminhos ordenados, independentemente da ordem de término. O índice
contém somente caminhos; o lote não retém textos/resultados de todos os programas.
Cache LRU contém texto bruto, nunca expansão, limitado a heap/16; entradas pressupõem
arquivos imutáveis durante a execução. JSON é substituído atomicamente ao concluir.
O orçamento por fonte/expansão padrão é heap/(24 × workers), ajustável explicitamente.
Excedê-lo produz ERROR na raiz ou PARTIAL no include, com diagnóstico, nunca descarte
silencioso. Profundidade de includes e candidatos têm limites explícitos/configuráveis.
