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
As declarações fornecem somente nomes, relações grupo/campo, aliases e associação
nível 88 → item-pai/primeiro VALUE. Não calculamos offsets, padding/truncamento PIC
ou layout. SET condition-name TO TRUE produz o primeiro VALUE no pai, sem tornar
VALUEs de conditions não selecionadas candidatos. SET FALSE e VALUE não resolvido
contaminam o pai com Unknown. Escritas de grupo contaminam seus campos; escritas
parciais/de campos contaminam os grupos que os contêm e as visões sobrepostas por
REDEFINES. Um campo irmão independente não é um receiver da escrita.

Os writers usam ilhas limitadas por statements/cláusulas, sem modelar execução.
READ procura INTO dentro dessa ilha (incluindo NEXT/PREVIOUS RECORD e WITH NO LOCK).
Receivers múltiplos, ROUNDED, contadores/pointers de STRING/UNSTRING e INSPECT
TALLYING são produtores Unknown quando a transformação não é modelada. MOVE ALL e
CORRESPONDING também são Unknown; candidatos anteriores sobrevivem flow-insensitively.
O resolver continua sendo acionado somente pelos sinks.

Comentários são tratados antes da detecção de COPY: marcadores de linha inequívocos
antes do recorte de colunas, comment entries fixed da Identification Division no
Normalizer, e comentários inline no lexer. Strings permanecem tokens indivisíveis.
A tolerância a `*>` no começo da linha e a registros `000100*`/`000100/` também evita
que a seleção fixed/free transforme esses comentários em diretivas. Isso não é
inferência automática do formato do restante do fonte.

COPY REPLACING é um contexto de expansão, herdado por todos os COPYs descendentes.
O cache permanece de texto bruto, portanto inclusões ONE/TWO não compartilham texto
expandido. As regras não reescrevem os próprios operandos de diretivas COPY. Uma
regra local funciona quando não há REPLACING herdado. Duas cláusulas REPLACING na
mesma cadeia são diagnosticadas como conflito e o membro conflitante não é expandido:
PARTIAL/incomplete, sem inventar precedência. Esse é o limite conservador para a
restrição documentada pelo IBM Enterprise COBOL. Orçamentos de bytes/crescimento,
profundidade e ciclos continuam aplicáveis em cada expansão.

COPY MEMBER OF/IN LIBRARY registra MEMBER e produz PARTIAL/incomplete com diagnóstico
curto de qualifier não resolvido. Não existe contrato de library-name → diretório;
nenhum arquivo é escolhido pela ordem de copyDirs nesse caso.

Referências de semântica consultadas:
- [IBM — STRING](https://www.ibm.com/docs/en/cobol-aix/5.1.0?topic=statements-string-statement)
- [IBM — COPY, inclusive nesting](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf)
- [IBM — replacement rules](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=statement-comparison-replacement-rules)
- [IBM — SET condition-names](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=statement-format-4-set-condition-names)
- [IBM — comment entries](https://www.ibm.com/docs/en/cobol-zos/6.4?topic=division-optional-paragraphs)
- [IBM — continuation lines](https://www.ibm.com/docs/en/cobol-aix/5.1.0?topic=b-continuation-lines)

W7: concorrência somente entre fontes; até 2 × workers de resultados/futuros em voo.
Emissão segue caminhos ordenados, independentemente da ordem de término. O índice
contém somente caminhos; o lote não retém textos/resultados de todos os programas.
Cache LRU contém texto bruto, nunca expansão, limitado a heap/16; entradas pressupõem
arquivos imutáveis durante a execução. JSON é substituído atomicamente ao concluir.
O orçamento por fonte/expansão padrão é heap/(24 × workers), ajustável explicitamente.
Excedê-lo produz ERROR na raiz ou PARTIAL no include, com diagnóstico, nunca descarte
silencioso. Profundidade de includes e candidatos têm limites explícitos/configuráveis.
