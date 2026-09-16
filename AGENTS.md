# Plano B

Leia `docs/architecture.md`, `docs/state.md` e `docs/coverage.md`.
Este repositório implementa `docs/plan.md`: inventário semântico de dependências.
Não introduza AST geral, IR, CFG, controle de fluxo, banco ou servidor.
Colete fatos e resolva somente os alvos de CALL/CICS sob demanda. Nunca colete
literais globais. CICS LINK/XCTL reutilizam obrigatoriamente o mesmo resolver.
Prefira candidatos semanticamente ligados ao sink a perder candidatos por controle.
Preserve candidatos conhecidos e sinalize incompletude. Não altere o JSON silenciosamente.
Toda mudança semântica exige teste; bugs reais exigem regressão mínima.
Atualize a matriz de cobertura. Execute `mvn clean verify` antes de cada checkpoint.
Faça commits por checkpoint na branch dedicada; não faça merge automático.
Não altere outros diretórios do workspace. Caches e temporários ficam aqui, ignorados.
