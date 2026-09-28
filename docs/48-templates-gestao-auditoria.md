# 48. Auditoria da gestão de templates WhatsApp (criar, editar, excluir)

Relato de origem (28/09/2026): o cliente disse que "não consegue clicar no botão que confirma a
exclusão". A auditoria percorreu criação, edição e exclusão nas duas superfícies — a página
"Templates WhatsApp" (`pagina-templates-whatsapp.tsx`) e o modal de templates do atendimento
(`modal-de-templates.tsx`) — do clique até o provedor.

## 1. Como foi verificado

Navegador real (Chromium via Playwright e o navegador embutido do app), backend real e o
**adaptador de produção** `meta-cloud`, apontado para um stub local da Graph API. Nenhuma chamada foi
à Meta e nenhum template real foi tocado.

| Peça | Onde |
|---|---|
| Stub da Graph API (registra método, caminho, query e corpo; injeta falha/atraso) | `frontend/e2e/fixtures/graph-meta-stub.mjs` |
| E2E | `frontend/e2e/templates-gestao.spec.ts` (19 casos) |
| Contrato REST com provedor simulado | `backend/crm-app/src/test/java/.../canal/TemplatesWhatsAppMetaIT.java` |

### Rodar localmente

```bash
node frontend/e2e/fixtures/graph-meta-stub.mjs            # porta 8089
```

Banco **descartável** (não use o banco dev com seed: ver §5, bug do runner):

```bash
docker exec synapse-postgres psql -U synapse -d postgres -c "CREATE DATABASE synapse_crm_e2e_tpl OWNER synapse;"
export SYNAPSE_DB_URL=jdbc:postgresql://localhost:5433/synapse_crm_e2e_tpl
java -jar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar                               # sem perfil: para na V72
java -jar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar --synapse.migrations.run-once  # V73
```

Backend (perfil `dev`, segredo JWT aleatório só para a execução local):

```bash
SPRING_PROFILES_ACTIVE=dev SYNAPSE_JWT_SEGREDO=<32+ caracteres aleatórios> \
WHATSAPP_PROVEDOR=meta-cloud WHATSAPP_URL_BASE=http://127.0.0.1:8089/v21.0 \
WHATSAPP_NUMERO=phone-e2e WHATSAPP_TOKEN=token-stub-e2e WHATSAPP_CONTA_NEGOCIO=waba-e2e \
WHATSAPP_WEBHOOK_VERIFY_TOKEN=verify-e2e WHATSAPP_WEBHOOK_SECRET=segredo-e2e \
java -jar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar
```

Frontend e E2E:

```bash
cd frontend && NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
npx playwright test e2e/templates-gestao.spec.ts --workers=1
```

Sem o stub no ar, o spec se marca como `skipped` em vez de falhar.

## 2. Matriz

Legenda: **comprovado** = reproduzido com evidência; **hipótese** = compatível com o código, sem
reprodução; **não reproduzido** = tentado e não aconteceu.

### Exclusão

| Etapa | Página | Modal do atendimento |
|---|---|---|
| Ação visível só com `templates.excluir` | ✅ E2E: atendente não vê lixeira; `DELETE` direto → 403 sem chamada à Meta | ✅ idem (componente só renderiza com a capacidade) |
| Confirmação abre e o botão recebe o clique | ✅ **bloqueio não reproduzido**: `elementFromPoint` = o botão, `pointer-events: auto`, sem ancestral `inert` | ✅ **bloqueio não reproduzido**: idem, com a confirmação aninhada sobre o modal de envio |
| Requisição | ✅ exatamente 1 `DELETE /api/v1/whatsapp/templates/{id}?nome=` | ✅ idem |
| Chega ao provedor com o identificador certo | ✅ `DELETE /{waba}/message_templates?hsm_id=tpl-e2e-2&name=retorno_orcamento_e2e` | ✅ idem |
| Sucesso atualiza a lista | ✅ item some após revalidação | ✅ item some; modal de envio continua aberto (5/5 repetições) |
| **Falha 403/422/503 aparece ao usuário** | ❌→✅ **comprovado**: diálogo ficava aberto, botão reabilitava e nada era exibido | ❌→✅ **comprovado**, idem |
| Fechar e reabrir enquanto pendente | ✅ bloqueado (E2E: Cancelar desabilitado, Esc sem efeito, 1 `DELETE`) | ✅ mesmo componente (teste de componente) |
| **Clique repetido enquanto pendente** | ❌→✅ **comprovado**: 3 de 5 execuções enviaram 2–3 `DELETE` para o mesmo template | ❌→✅ mesmo componente; E2E dedicado |
| Cancelar | ✅ nenhum `DELETE` | ✅ nenhum `DELETE`, modal de envio continua aberto |

### Edição

| Etapa | Resultado |
|---|---|
| Ação só com `templates.editar`; `PUT` direto de atendente → 403 sem chamada à Meta | ✅ |
| Corpo válido → `GET /{id}?fields=status,components` e `POST /{id}` com `components[BODY]` | ✅ E2E |
| Lista/prévia mudam só depois do sucesso | ✅ E2E (lista) e teste de componente existente (prévia) |
| Falha mostra o motivo | ⚠️→✅ **comprovado**: mostrava sempre o texto genérico, sem o motivo da Meta |

### Criação

| Etapa | Resultado |
|---|---|
| Pedido válido → `POST /{waba}/message_templates`; item aparece como **Pendente** com aviso | ✅ E2E |
| Variáveis fora de ordem bloqueiam o envio, sem chamada | ✅ E2E |
| Recusa do provedor mantém o formulário e não inventa item | ✅ E2E |
| Falha mostra texto do catálogo | ⚠️→✅ **comprovado**: mostrava o `detail` cru do backend (texto da Meta) fora do catálogo |

### Regressão

Envio de template aprovado pelo modal continua chegando ao provedor como `type: template` (E2E).

## 3. Correções

1. **Feedback de falha.** `mensagemDeErroDeTemplate` (`frontend/src/lib/atendimento/erro-de-template.ts`)
   traduz o `ErroDeApi` para o catálogo: 403 → sem permissão; 404 → não existe mais; 422 → "A Meta
   recusou a operação: {motivo}"; 400 → "Pedido inválido: {motivo}"; 5xx → provedor indisponível,
   **sem** o diagnóstico cru; falha de rede → genérico. O motivo perde marcação e é limitado a 200
   caracteres. A confirmação de exclusão ganhou `role="alert"`; o diálogo continua aberto para nova
   tentativa. As mutations são zeradas ao reabrir o diálogo, para não mostrar erro de outra tentativa.
2. **Duplo `DELETE`.** `disabled={excluindo}` só vale depois do próximo render. A confirmação agora
   tem trava síncrona (`useRef`), liberada quando a tentativa termina.
3. **Operação em voo não fecha o diálogo.** Confirmação de exclusão, edição e criação ignoram
   Cancelar, X, Esc e clique fora enquanto a requisição está pendente. Sem isso, fechar e reabrir para
   outro template permitia um segundo `DELETE`/`POST` concorrente, e o erro do primeiro sumia com o
   diálogo (achado da revisão de código; o `reset()` da mutation, que limpa erro antigo ao reabrir,
   agora só roda sem requisição em voo).
4. **Catálogo.** `templatesWhatsApp.erros` no `textos.json`, com `.default` no schema do frontend:
   frontend novo com backend anterior (imagens publicadas separadamente) não derruba o catálogo.

Backend e adaptador não mudaram: o contrato REST (204, 403, 422 e 503 em RFC 7807) e os
identificadores enviados à Meta estavam corretos; os testes novos em `TemplatesWhatsAppMetaIT` os
fixam.

## 4. O que continua incerto no ambiente do cliente

- **Causa do relato — hipótese.** No código de antes, qualquer falha da exclusão (Meta recusando,
  token sem permissão de gestão de templates, 503, perda de permissão) deixava o botão reabilitado
  sem nenhuma mensagem, o que é indistinguível de "o botão não funciona". Sem acesso aos logs da
  instância, não dá para afirmar qual resposta o usuário recebeu. Com a correção, a tela passa a
  dizer qual foi.
- **Fechamento do modal de envio após excluir — não reproduzido.** Aconteceu uma vez na primeira
  tentativa manual no navegador embutido; nas 5 repetições dirigidas e nas demais execuções o modal
  permaneceu aberto. Não há correção para isso.
- **A confirmação aninhada no modal de envio não escurece o modal de baixo** (há um só backdrop). É
  visual, não impede o clique; não foi alterado.
- **Provedor da instância.** A gestão só aparece quando o canal ativo declara `gerenciaTemplates`
  (`meta-cloud` com `WHATSAPP_CONTA_NEGOCIO` preenchido). `uzapi-autotic` não gerencia templates e a
  página não é exibida.

## 5. Achado fora do escopo — runner da V73 em banco dev

`FlywayMigrationRunnerConfiguration` usa só `classpath:db/migration`. Em banco criado com o perfil
`dev`, a seed repetível `R__seed_dev.sql` já está no histórico, a validação do runner falha
("Falha ao validar histórico/checksum") e o boot continua parado antes da V73. Por isso bancos dev
semeados ficam presos na V72. O procedimento da §1 contorna criando o banco sem o perfil `dev` até a
V73. Produção não é afetada (não usa a seed).

## 6. Instabilidade observada no `clean verify`

`WebhookAvaliacaoIT.httpBloqueado_naoReteveTransacaoNemImpedeMensagemNormal_eTimeoutRepeteMesmoId`
falhou uma vez (`expected: 0 but was: 1`) no `clean verify` completo, com a máquina sob carga, e
passou 40/40 em duas execuções isoladas. O teste não tem relação com templates; fica registrado como
intermitente dependente de tempo.
