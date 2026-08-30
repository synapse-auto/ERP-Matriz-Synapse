# E88 — Pendências de experiência em Atendimentos

## Objetivo

Entregar em uma única etapa as quatro pendências relacionadas à experiência de Atendimentos:

1. salvar/baixar imagem no chat;
2. seção de mídias e documentos na ficha lateral do lead;
3. tag que identifica card de atendimento atualmente em IA;
4. preenchimento automático e seguro de mensagens rápidas;
5. compactar aproximadamente 15% os tabs/filtros e os ícones de ação no topo da lista de Atendimentos.

Os prompts individuais E88–E91 anteriores são rascunhos substituídos por este documento. Execute **somente este prompt** para essa rodada, em uma única branch, e não divida em novas branches sem autorização.

## Base e segurança de trabalho

1. Atualize referências remotas sem alterar `main`.
2. Crie worktree a partir de `origin/main` e branch `codex/e88-pendencias-atendimentos`.
3. Leia integralmente `AGENTS.md`, os contratos de Atendimentos, mídia/storage, mensagens rápidas, painel do lead e regras RN-CRM-01/RN-CRM-06.
4. Não faça merge, rebase, reset, deploy ou chamada real a Meta/WhatsApp. Não edite migrations já aplicadas.

## 1. Mídias da ficha do lead e salvar imagem

- Adicione ao painel lateral do lead seção com mídias/documentos reais vinculados a atendimentos visíveis: imagem, áudio e documento, com nome, tipo, tamanho, data e origem quando disponíveis.
- A lista deve paginar/carregar incrementalmente, ter estados real de vazio/erro e nunca carregar binário completo em listagem.
- Imagens abrem visualização segura e ação **Salvar imagem**; documentos têm download; áudio preserva player existente.
- Reaproveite storage e registros de mensagens. Se URL assinada/cross-origin não tornar o download confiável, crie endpoint autorizado que valida lead + atendimento + mensagem antes de devolver conteúdo/`Content-Disposition`.
- Não implementar banco de arquivos global, exclusão, compartilhamento público, novo upload no painel, ou duplicação de storage.
- Atendente A não lista, visualiza nem baixa anexos do lead de B. Não usar id ou URL arbitrária do frontend como autorização.

## 2. Tag de atendimento em IA

- No card da lista de Atendimentos, exiba tag compacta “Atendido pela IA” somente quando o atendimento estiver realmente ativo sob IA.
- Primeiro audite o contrato. Se o resumo já tiver estado confiável, reutilize-o; se faltar, exponha no backend somente campo derivado mínimo, sem entidade inteira nem dados de IA sensíveis.
- Não exibir para atendimento humano, finalizado, sem atendimento ou chat interno. Não substituir etapa, canal, badge, responsável ou prévia.
- A atualização deve refletir devolução à IA, transferência ao humano e finalização pelo mecanismo atual de tempo real/invalidação, sem polling novo nem vazamento de lead.

## 3. Preenchimento automático de mensagens rápidas

- Audite a sintaxe de variáveis atual e centralize a resolução entre preview e envio, preservando as mensagens existentes.
- Ao escolher uma mensagem rápida no composer, preencha somente variáveis documentadas e autorizadas com dados reais do lead/atendimento. O usuário vê e pode editar o texto final; não enviar automaticamente nem mudar o template salvo.
- Nunca interpolar CPF, notas, resumo de IA, token, telefone completo, dados de outro lead ou conteúdo não autorizado.
- Variável sem valor fica claramente identificada e bloqueia envio com texto de catálogo até o usuário corrigir/remover.
- Preserve filtros de uso `HUMANO`/`CHATBOT`/`AMBOS`, regras de 24h/template, autoria e RN-CRM-06.
- Preview administrativo usa o mesmo resolvedor, em estado neutro, sem inventar dados de cliente.

## 4. Compactação visual no cabeçalho

- Reduza aproximadamente 15% os tabs/filtros `Todos`, `Ativos`, `Pendentes`, `Potenciais` e os ícones de ação no cabeçalho da lista de Atendimentos.
- Preserve alvo clicável de pelo menos 40x40 px, texto, badges, tooltip, nomes acessíveis, foco, hover, disabled e comportamento atual.
- Em 390 px não pode haver corte, sobreposição ou overflow horizontal. Não reduza fonte global, sidebar, componentes `Button`/`Tabs` compartilhados nem funcionalidade de filtros.
- Se a intenção de “abas 15% menores” for outro elemento além desses tabs da lista, pare e registre essa ambiguidade antes de alterar outro layout.

## Regras transversais

- Reutilize componentes e contratos existentes sempre que possível; não crie segunda implementação de mídia, preview ou resolução de variáveis.
- Textos novos em `textos.json` + schema Zod. Cores e tamanhos via tokens/classes locais, sem valores de cor fixos.
- Não introduza mocks, tenant_id, endpoint público, polling, migração alterada ou ampliação de permissão.
- Mantenha chat interno, reações, responder/encaminhar, avaliação automática e criação de atendimento externo fora deste escopo.

## Testes obrigatórios

### Backend com Java 21 e Testcontainers

- Listagem/download de anexos: paginação, arquivo inexistente, acesso autorizado e negativo A/B, sem URL privada vazada.
- Tag IA: origem correta do campo e transições IA → humano, humano → IA e finalização, preservando visibilidade.
- Mensagens rápidas: variável válida, proibida, ausente e isolamento de lead; nenhum dado sensível é interpolado.
- Rode `cd backend && ./mvnw clean verify` se qualquer backend for alterado.

### Frontend

- Mídias: vazio/carregando/erro, três tipos, salvar imagem, download de documento e paginação.
- Tag IA: aparece só no card elegível e nunca em chat interno/humano/finalizado.
- Mensagens rápidas: seleção preenche, edição funciona, pendência bloqueia envio, falha preserva texto e template não sofre mutação.
- Compactação: tabs/ícones mantêm rótulos acessíveis, contagens e alvos mínimos.

### Navegador e comandos

- Em ambiente autenticado com dados de demonstração, valide desktop, 1024 px e 390 px: ficha com anexos, salvar imagem, tag IA, mensagem rápida preenchida e cabeçalho compacto, sem overflow.
- Gere screenshots sem dados reais de cliente.
- Rode `cd frontend && npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` e `git diff --check`.

## Commit, push e CI

- Faça commits Conventional Commits coesos e envie para `origin/codex/e88-pendencias-atendimentos`.
- Abra PR contra `main` apenas com validações locais aprovadas; aguarde CI remota e informe URL/número da run e resultado por job.
- Não faça merge, deploy ou chamada real ao provedor sem autorização explícita posterior.

## Relatório final

Siga as sete seções de `AGENTS.md`. Inclua SHA, branch, confirmação de push, arquivos/migrations/endpoints, evidências de autorização negativa, screenshots, resultado da CI e qualquer limitação de storage/ambiente.
