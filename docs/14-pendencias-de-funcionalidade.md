# 14. Pendências de funcionalidade

Tudo que está no protótipo aprovado pelo cliente e **não** está no sistema. Ordenado por custo/benefício, não por ordem de descoberta.

Distinto do `docs/09`, que define o recorte da primeira entrega. Este documento existe para que nenhuma dessas pendências dependa de alguém lembrar.

Atualizado em 30/08/2026, após a E92, confrontando os itens com `origin/main` (`a47362c`).

## Itens que deixaram de ser pendências

As entregas abaixo estavam pendentes na versão anterior, mas foram incorporadas à `main`:

| Item | Evidência |
|---|---|
| Chat interno básico, entrada pela equipe, mídia e reações | PR #16 `be5b1b8`, PR #15 `99048f6`, merge E80 `4d03812` |
| Responder e encaminhar mensagens com citação | PR #19 `d9b249a` e migration V46 |
| Mídias/documentos na ficha, anexos múltiplos e catálogo de emoji | PR #17 `9dbe439`, PRs #25–#27 |
| CSAT após finalização pela Automação | PR #14 `b7a7ab8`, migrations V43–V44 |
| Templates da Meta | PR #13 `d5ba368`, PR #20 `91ea622`, PR #22 `bc89ba6`, PR #24 `2f7f2b4` |
| Código numérico do lead | PR #28 `a47362c`, migration V47 |

## E49 — Configurações ainda fora da entrega

A aba **Configurações** entrega nesta etapa apenas o perfil do usuário: nome editável pelo próprio
usuário, e-mail somente leitura, telefone/cargo de exibição, papel somente leitura e o link para o
fluxo existente de troca de senha. Os itens abaixo continuam pendentes e não são simulados na tela:

| Item | O que falta | Por que não entrou |
|---|---|---|
| **Alterar foto** | upload de avatar, limites, recorte, remoção e autorização | não existe contrato de foto de usuário; o MinIO atual é mídia de conversa |
| **Preferências gerais** | idioma, notificações, densidade e persistência por usuário | o catálogo de textos é por instância e não existe modelo de preferências |
| **Aparência** | sobrescrita de tema por usuário | o tema é da instância; tema individual exige uma decisão de precedência e um segundo nível de configuração |
| **Ajuda e suporte** | conteúdo, links e canal real de suporte | não há conteúdo nem integração de suporte definida |
| **Novidades & Em Breve / Administração** | módulo de publicação e administração da matriz | não fazem parte desta etapa nem têm contrato de produto |
| **Versão exibida na tela** | valor vindo do build | não foi criado um contrato de versão; não será usado texto fixo |

---

## Prioridade 2 — mudança pequena de schema

| Item | O que falta | Nota |
|---|---|---|
| **Mensagens rápidas compartilhadas ("Geral")** | `atendente_id` passa a aceitar nulo, com o significado de "da equipe" | hoje toda mensagem rápida é pessoal. Numa operação com vários atendentes, resposta pronta compartilhada é das coisas mais úteis que existem — e o protótipo mostra o grupo |
| **Modal de tag com a paleta do protótipo** | 7 tons e 22 ícones do modelo, no lugar dos 7/14 atuais | decisão de produto, não de fidelidade: confirme o conjunto com o cliente antes |
| **Disponibilidade para a IA independente da presença** | a coluna `disponibilidade_atendente_ia.disponivel_para_ia` existe, mas é escrita junto com a presença e **só** para `papel = 'ATENDENTE'`; falta o toggle próprio | descoberto em 19/08 pelo Dylan, ao ver `/internal/v1/atendentes/disponiveis` devolver vazio. Hoje **não existe** "atendente online mas fora do rodízio da IA": quem fica ONLINE entra no rodízio, sem escolha. Some com o item de Horários de trabalho — decidir junto |

## Prioridade 3 — módulo ou endpoint novo

| Item | Tamanho | Nota |
|---|---|---|
| **Regras de automação** — follow-up, fidelização, mensagem festiva, resumo por IA | grande | tabelas existem, zero caso de uso. Hoje ninguém consegue configurar nada disso. O produto se chama "CRM integrado com IA"; é a lacuna mais visível quando alguém for olhar essa parte |
| **Horários de trabalho** | 1,5 a 2 dias | módulo inteiro: `horario_trabalho` e `rotina_disponibilidade` só existem no schema. Consequência atual: **disponibilidade do atendente é manual** — ninguém entra em expediente sozinho |
| **Kanban na Agenda** | médio | precisa de endpoint de agrupamento por etapa com contagem; sem ele vira N+1 |
| **Importar/exportar CSV de leads** | médio | não existe motor CSV em nenhum dos dois lados |
| **Avaliação por atendimento na Automação** | ~~médio~~ **feito** | `POST /internal/v1/atendimentos/{id}/avaliacao`, com idempotência e validação de atendimento finalizado; PR #14 |
| **Troca de credencial do canal pela gestão** | médio | o cadastro de canal existe, mas não há API para consultar ou rotacionar a credencial com validação e histórico; os contratos antes descritos no `docs/04` eram apenas planejados |

## Prioridade 3 — itens do protótipo sem contrato fechado

Estes itens continuam fora da entrega porque ainda exigem decisão de produto, contrato ou arquitetura. Não devem ser simulados na interface.

| Item | Escopo que falta | Por que não entrou |
|---|---|---|
| **Aba de Informações da IA** | definir quais informações, tabelas, período e fonte de verdade serão exibidos | não há modelo nem contrato para os dados; uma tela baseada em snapshot seria enganosa |
| **Modelos de filhos por nicho** | ponto de extensão, catálogo de campos e provisionamento para variações por ramo | exige decisão arquitetural de Base PAI, não uma coluna ou `if` específico de cliente |
| **Aba de novidades da matriz** | módulo ADM para publicar, versionar e distribuir novidades | não existe serviço de conteúdo, público-alvo ou política de leitura |
| **Entrar/sair de atendimento em andamento** | permissão da Agenda, estado de participação e auditoria da entrada/saída | alterar presença não define sozinho quem pode assumir uma conversa em andamento |
| **Login como outro usuário por administrador** | fluxo de impersonação com consentimento, escopo, expiração e auditoria reforçada | é uma superfície de alto risco; não será criada sem decisão explícita de segurança |
| **Chat interno da equipe — fase 2** | grupos, retenção e notificações internas além da conversa direta, mídia e reações já entregues | a base foi entregue em E80/E86; só o restante continua pendente |

## Fora da primeira entrega por decisão (`docs/09`)

Dashboard nas abas Operacional, Comercial e IA & Automação; Relatórios; Campanhas; Banco de Arquivos. Não são pendências — são escopo de fase 2, com o cliente ciente. O chat interno deixou de ser apenas escopo futuro: sua base está entregue; os complementos de fase 2 permanecem na tabela acima.

---

## Duas decisões de produto em aberto

**Escala do CSAT.** A implementação atual usa a escala **1–5**, inclusive no contrato da Automação e na constraint de `avaliacao.nota`. O texto antigo do protótipo (“9,4 / 10”/0–10) ficou defasado; não alterar o banco sem uma nova decisão explícita de produto.

**Conjunto de cores e ícones de tag.** O protótipo tem 7 tons e 22 ícones; o construído tem 7 e 14. Trocar é decisão de produto.

---

## O que não é pendência de funcionalidade, mas bloqueia a entrega

Registrado aqui porque some de vista com facilidade:

- **Smoke RLS nunca executado** no ambiente real — atendente pode estar vendo lead de colega e ninguém saberia
- **Seed de demonstração nunca executado** — as telas estão vazias
- **Entrada e saída de mensagem:** o código e os contratos estão implementados, mas a confirmação do fluxo real no ambiente precisa ser mantida como evidência operacional separada; git não prova um teste contra WhatsApp real
- **App da Meta e WABA:** o estado operacional deve ser conferido no painel da Meta/Dokploy; este documento não deve afirmar que o app está publicado apenas por causa de um commit
- **Lead de teste no ambiente** — o webhook de teste criou "test user name" (`16315551181`). Limpar antes de o cliente usar
- **Backup nunca restaurado** de verdade
- **Watchdog externo da E22 ainda não provisionado/testado** — o endpoint e o runbook existem; falta configurar o Kuma fora do provedor do CRM e executar o teste destrutivo de `docs/15`
- **Subdomínios reais** — o `sslip.io` compartilha cota do Let's Encrypt com o mundo; na renovação, o certificado pode não sair, e aí a Meta para de entregar webhook
- **PITR** antes do go-live de produção (`docs/10` §1.1b)
