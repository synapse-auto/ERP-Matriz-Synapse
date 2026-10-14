# 73 — Auditoria de convite e atendimento colaborativo

## Escopo e estado

Base: `origin/main 9a2fc89`, worktree gerenciada isolada, branch
`codex/convite-colaborativo-auditoria`. Não inclui mudanças alheias da worktree principal.
Sem VPS, deploy, atendimentos reais ou reprocessamento de convites de produção.

Requisito: A permanece responsável do lead/ciclo, B aceita e colabora, C não alcança.
Convite pendente só libera a leitura prevista; aceite, abertura e mensagem consentida
não podem assumir a responsabilidade. Entrada direta e Agenda preservam RN-CRM-06.

## Inventário inicial — ainda não é causa comprovada do relato

- docs/51 já descreve origem `CONVITE`/`PEDIDO_APROVADO`, bloqueio de envio pendente,
  tratamento de HTTP 200 vazio e reconciliação canônica, autoria e expiração.
- Código atual de `EnviarMensagemUseCase` contém a exceção por origem consentida.
  Não substituir essa proteção nem inferir transferência pela mudança de aba.
- SQL de Ativos ainda usa responsável; Pendentes próprios inclui convite pendente.
  Precisamos reproduzir o comportamento de participação aceita na inbox e contadores.
- A Specification de lead tem somente próprio/IA/finalizado. O documento registra
  ficha/tags indisponíveis ao participante; confirmar pelo endpoint e navegador.
- O painel operacional recém-merged também tem recortes próprios: auditar como se
  comporta quando a capacidade está concedida, sem ampliar presença de terceiros.
- Diálogo já oferece seleção e confirmação; medir geometria atual com nomes longos.
- Cache de pedidos é global por atendimento: auditar isolamento de sessão, invalidação
  durante requests pendentes e respostas antigas. Nenhuma causa concluída por inspeção.

## Plano de execução e evidência

1. Reproduzir main sem alterações, em banco sintético com gateway fake explícito.
   Registrar donos do lead/ciclo, pedido, origem, HTTP A/B/C e envelopes técnicos.
2. Capturar duas sessões headed: convite, histórico pendente, aceite, envio bilateral,
   refresh/reconexão, abas/contagem, ficha e diálogo desktop/menor.
3. Corrigir apenas defeitos comprovados; preservar autorização, RLS, outbox, idempotência,
   coalescência e exceção restrita de propriedade. Nenhuma concessão global.
4. Testar pontos de entrada e negativos: B pendente, C, recusado/expirado, repetição,
   saída, transferência/finalização concorrentes, atraso e ordem de snapshots.
5. Frontend completo/typecheck/lint/build, backend clean verify Java 21, diff check,
   commit/push, PR e CI do último SHA, relatório nos sete itens.

## Limitações e decisões

Skills requeridas clean-code, architecture-patterns, api-design-principles e
supabaseboaspraticas indisponíveis no catálogo. Playwright CLI lida para validação real
headed; não usar uma reprodução HTML nem dados mockados na aplicação.
Relato real não tem IDs/horários; reprodução local não comprova a versão implantada.
Se necessário, preparar diagnóstico somente leitura e sanitizado para o operador.

## Causas locais comprovadas e correções

| Causa | Evidência inicial | Correção |
| --- | --- | --- |
| Ficha/tags excluíam B aceito pela Specification | HTTP real `/leads/{id}` = 404; teste falhou | EXISTS de participação ativa em ciclo aberto na Specification, sob a mesma RLS |
| Ativos excluía participante que não é dono | Inbox de B = `itens: []`; teste falhou | Alinhar escolha e contagem de Ativos; Pendentes compartilha o recorte quando a última mensagem é do lead |
| Cache reintroduzia PENDENTE depois de APROVADO | Teste de promessa fora de ordem falhou | Geração de request, chave usuário/papel e descarte de resposta obsoleta |
| Cache do layout raiz podia sobreviver à troca de conta client-side | Negativo com B→C continuou mostrando a ficha privada de B | QueryProvider separado por usuário/papel; C refaz a leitura e recebe o 404, sem renderizar dados de B |
| Diálogo cortava em viewport baixa | 390×320: top −54,5 / bottom 374,5 | Altura limitada, rolagem e grade encolhível; depois top 16 / bottom 304, sem overflow horizontal |
| Refresh podia perder abertura pelo deep link | E2E após envio bilateral: nenhuma conversa selecionada | Marcar abertura processada só na microtask não cancelada |

O painel operacional reaplicava próprio/sem dono, descartando convites/participações permitidos
pela RLS. O teste HTTP com capacidade concedida cobre convite, aceite e saída, sem liberar outros
leads do responsável. O agregado conserva **o dono real**: a linha de A no recorte de B contém
somente conversas compartilhadas autorizadas, não toda a carteira de A.

O snapshot `/atendimentos/{id}/estado` devolve `podeEnviar=false` para convite pendente e `true`
após aceite se o ciclo continuar aberto. Capacidades e autorização do comando continuam necessárias.
Transferir/finalizar já recusavam B pendente por locks sob RLS; o cabeçalho deixa de oferecer essas
ações antes do aceite. Chamada direta de transferência pendente no banco sintético confirmou
HTTP 404 e ambos os responsáveis intactos.

## Contratos e decisões

- Sem endpoint, tabela, função SQL ou migration nova. `ParticipacaoVisivelLeadEntity` é projeção
  JPA imutável de IDs, sem escrita/JpaRepository, dependência do módulo atendimento ou elevação SQL.
- `VisibilidadeLead.permite(Lead)` representa a **carteira base**. Lead puro não contém participação;
  não usar esse helper sozinho como autorização contextual. Specification e RLS compõem a exceção.
- Convite pendente mantém só a leitura do histórico prevista, não foi estendido à ficha.
  Participante aceito/ativo recebe ficha/tags que a RLS já permitia. Saída retira essa exceção;
  acesso independente de gestor/próprio/IA/finalizado segue a regra existente.
- `/api/v1/atendimentos/painel-operacional/fila` acrescenta `convitePendente` booleano pessoal,
  derivado de pedidos válidos. Compatível com clientes antigos; ausência é falso no frontend.
  OpenAPI é gerado do record `PainelOperacional.Conversa`.
- Convidado não substitui a etapa, não muda o dono e desaparece após o aceite.
- Uma busca de participação por chave, fora do render; invalidação por evento, sem polling novo.
  Logout/troca de usuário limpa o cache. Perda da condição de responsável desativa consultas de pedidos.
- QueryProvider recria o cache e seus observadores somente na troca de usuário/papel, não em refresh
  de token, aceite ou evento do mesmo usuário. O alcance inclui os caches de outras telas no mesmo
  layout: decisão de segurança necessária para impedir reutilização de informação entre contas.
  Não muda permissões do servidor, não reenvia mutations nem altera o armazenamento de rascunhos.
- Após aceite, revalidar ficha/tags somente do lead correspondente: um 404 anterior ao aceite não
  pode ficar persistente na interface. Não invalidar todas as fichas nem conceder acesso otimista.
- Confirmação de convite usa trava síncrona contra dois cliques antes do próximo render.
- Finalização explícita **libera o responsável do lead** para o próximo ciclo, como já definido;
  mantém o dono comercial em `atendimento.atendente_id`. O teste da corrida não confunde essa
  finalização autorizada com transferência pelo convite.
- Entrada direta e Agenda não foram modificadas. A exceção de envio continua restrita à origem
  `CONVITE`/`PEDIDO_APROVADO`; não depende do nome/papel de um participante para mudar propriedade.

## Testes e ambiente reproduzível

`ConviteColaborativoIT`: HTTP/STOMP reais, PostgreSQL/Redis Testcontainers, canal fake de testes.
Cobre convite/aceite/envio bilateral/autoria, ficha/tags/saída, lista A/B/C, pendente
(texto/template/mídia/agendamento), recusado/expirado, duplicidade/aceite repetido, self-add de C,
transferência/finalização concorrentes e estado canônico. `PainelContagemEquivalenciaIT` compara
lista/contagem sob RLS em A/B/OPERADOR/GESTOR. `PainelOperacionalIT` cobre delegado sem visão global,
marca pessoal e retirada do alcance ao sair.

Baseline: backend 12 testes, 2 falhas (ficha e Ativos); cache 5 testes, 1 falha (resposta antiga).
Primeira rodada corrigida: backend 24/24, frontend dirigido 109/109. A bateria final acrescenta
concorrência e estado canônico. Resultados finais de `clean verify`/frontend/CI acompanham o SHA
na PR e no relatório: uma rodada parcial/local não significa CI verde.

`ConviteColaborativoLocal` é main **somente em test-classes**, opt-in `CONVITE_VISUAL_LOCAL=true`:
Postgres `convite_auditoria` e Redis descartáveis, `CanalFake` explícito e schedulers desligados.
Não entra na imagem de produção nem envia a clientes. A fixture SQL recusa outro nome de banco.
Rodar na pasta backend com classpath de testes e Java 21. Frontend localhost:3011, API localhost:8091,
WS localhost:8091/ws. Não usar URLs de produção.

```text
PLAYWRIGHT_BASE_URL=http://localhost:3011
PSQL=docker exec -i <container-descartavel> psql -U test -d convite_auditoria -At -v ON_ERROR_STOP=1
PLAYWRIGHT_CAPTURAS=<workspace>/output/playwright
```

E2E: `frontend/e2e/convite-colaborativo.spec.ts --headed --workers=1`. Espera condições, sem sleeps.
Viewports 1366×820, 390×820 e 390×320; nomes longos, seleção/confirmar, ausência de `/transferir`,
duas sessões, envio bilateral, responsável e reload. Capturas da aplicação real, dados sintéticos,
Inter carregado, sem reprodução HTML. Evidências versionadas em
`evidencias/convite-colaborativo-20261014/`, produzidas primeiro em `output/playwright/`.

## Limitações e operação

- Não foi homologada a ocorrência real/versão implantada nem o WhatsApp/provedor externo.
  Relato sem IDs/horários não permite atribuir a ocorrência específica a uma dessas causas.
- Nenhum polling/provedor/lista inteira/trabalho pesado novo no envio. Alcance compartilhado do
  painel é materializado na consulta existente; contagem por status e deduplicação preservadas.
- Planos/CPU da HML não foram medidos. Não foi proposto índice/migration sem evidência.
- Windows não implementa os bits POSIX usados por `aplicar-marca.test.ts`; executar a suíte completa
  também em Linux/Node 22, sem remover esse teste de segurança.
- Ação necessária no Dokploy: **nenhuma**. Sem variável nova, deploy ou alteração de n8n.

Status: implementação em validação; homologação da ocorrência real continua pendente.
