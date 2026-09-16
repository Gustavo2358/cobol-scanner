# Benchmark reproduzível

`python3 -B benchmark/run.py` executa o JAR construído, com `-Xmx512m`.
Gera fontes somente em `benchmark/work/` (ignorado); preserva métricas por fonte,
JSON bruto, stderr, `/usr/bin/time -v`, hashes e resumo em `benchmark/results/`.

W7, Linux x86_64 / Temurin 25.0.4:

| Carga sintética | LOC | Threads | Tempo de processo | Pico RSS |
|---|---:|---:|---:|---:|
| 1 fonte, 100k atribuições + 50k linhas adicionais | 150.000 | 1 | 0,526 s | 244.100 KiB |
| 1.000 fontes de 200 linhas + COPY contextual | 200.000 | 4 | 0,509 s | 197.712 KiB |

Sem OOM; candidatos exatos e status OK verificados pelo runner. Estes resultados
são deste hardware e dessas cargas sintéticas, não promessa para mil programas
arbitrários nem comparação medida com Plano A. Tempos de fases são somas por worker,
portanto podem exceder o tempo de parede quando há paralelismo. LOC/bytes são físicos
nos fontes raiz; expansão conta no custo, mas não aumenta esses denominadores.
