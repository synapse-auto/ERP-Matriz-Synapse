# Prompt E07 — Contrato com a Automação e configuração

> Pré-requisito: E06 commitada (`3e8365c`). **Sessão limpa.**
> **Escopo alterado:** E07 e E08 foram fundidas. A outbox saiu daqui na E05, e o que sobrou (contrato `/internal/v1`) é essencialmente o endpoint de configuração — separá-los criaria duas etapas pela metade.

---

## 0. Correção pendente da E06 — janela de vazamento no Redis

O `RegistroDeAssinaturas` é consultado a cada entrega, e a revogação depende do evento de transferência chegar a **todas** as instâncias via Redis pub/sub.

**Redis pub/sub é at-most-once.** Não há garantia de entrega, não há retry, não há confirmação. Se a mensagem de revogação se perder — reconexão do cliente Redis, instância ocupada no momento do publish, partição de rede breve —, a instância que não recebeu **continua entregendo mensagens ao dono anterior do lead**, indefinidamente.

Isso é o vazamento de lead entre atendentes, por um caminho que nenhuma das camadas anteriores cobre.

**Correção: TTL nas entradas do registro.**

- Cada entrada de `RegistroDeAssinaturas` expira em N segundos (comece com 60, configurável)
- Na primeira entrega após expirar, revalida contra `AutorizarAssinaturaAtendimentoUseCase` e renova
- Revogação por evento continua — ela é o caminho rápido; o TTL é a rede

Assim a janela de vazamento passa de "indefinida" para "no máximo N segundos", e o custo é uma consulta por assinatura ativa a cada N segundos, não uma por mensagem.

Teste: com o publish de revogação simulado como perdido, o dono anterior para de receber após o TTL.

> Registre o número escolhido e o raciocínio. "60 segundos de janela" é uma decisão de risco que alguém precisa poder revisar depois.

---

## 1. Namespace `/internal/v1` — o contrato que a Automação de todos os filhos consome

O requisito interno é literal: mudar **no máximo a URL e o token** de filho para filho. Isso transforma este namespace em contrato público versionado.

- Autenticação por `X-Synapse-Token`, validado contra a configuração da instância
- **Contexto de serviço no RLS** — a Automação não é um usuário
- Namespace e formato idênticos em todos os filhos
- Mudança incompatível ⇒ `/internal/v2`, com v1 mantido. Nenhum filho pode ser forçado a atualizar a Automação junto com o CRM

Endpoints:

| Método | Rota | Descrição |
|---|---|---|
| GET | `/internal/v1/automation-config` | Todos os parâmetros tipados |
| GET | `/internal/v1/automation-config/{chave}` | Parâmetro específico |
| GET | `/internal/v1/regras/follow-up` | Regras ativas |
| GET | `/internal/v1/regras/fidelizacao` | Regras ativas |
| GET | `/internal/v1/atendentes/disponiveis` | Roteamento da IA |
| POST | `/internal/v1/eventos` | Automação reporta ação executada |

## 2. OpenAPI e testes de contrato

- OpenAPI gerado no build (springdoc), publicado como artefato do release — documentação vira saída de build, não documento que envelhece
- **Teste de contrato no CI:** se um PR mudar a forma de uma resposta de `/internal/v1`, o build falha

Esse teste é o que impede que um refactor distraído quebre a Automação de oito clientes ao mesmo tempo. Ele precisa comparar contra um snapshot versionado do contrato, não contra o código atual — senão valida a si mesmo.

## 3. Configuração de automação (ex-E08)

CRUD de `configuracao_automacao`, com:

- **Validação de faixa** antes de salvar, usando `valor_min`/`valor_max` e `tipo` da própria linha
- Cache (Redis) com invalidação por evento `automation.config.updated`
- `PUT /api/v1/automacao/config/{chave}` restrito a gestor/subgestor
- Auditoria: toda alteração vai para `audit_log` com valor antes e depois

Teste: alterar um parâmetro e confirmar que `/internal/v1/automation-config` reflete **sem redeploy**. É o requisito `RF-CRM-38b` e ele merece prova, não confiança.

## 4. Feature flags e configuração da instância

- `FeatureService` cacheado sobre `feature_flag`
- `GET /api/v1/config/features` — o frontend decide o menu a partir daqui
- `GET /api/v1/config/tema` e `/config/textos` — servem `tema.json` e `textos.json` da instância

Estes três são a fundação da Base PAI no frontend. A E10 depende deles existindo.

## 5. Regras da Meta que a Automação precisa saber

A E05 estabeleceu que texto livre fora da janela de 24h é rejeitado. A Automação precisa dessa informação **antes** de tentar enviar, senão descobre por erro.

- `/internal/v1/automation-config` deve expor se o canal ativo exige template fora da janela
- Um filho com provedor não oficial responde que não exige

Sem isso, a fidelização de "90 dias sem contato" vai falhar em produção na primeira execução — e falhar por 400 traduzido da Meta, que é diagnóstico ruim.

## 6. Testes

- Token inválido ou ausente em `/internal/v1` → 401
- Contexto de serviço: a Automação lê configuração sem usuário autenticado
- Alteração de parâmetro reflete sem redeploy
- Valor fora da faixa é rejeitado com mensagem clara
- Contrato: snapshot do OpenAPI de `/internal/v1` não mudou
- Flag desligada some do `/config/features`
- Vazamento do §0: revogação perdida para de vazar após o TTL

## Definição de pronto

- [ ] `/internal/v1` autenticado, documentado em OpenAPI
- [ ] Teste de contrato contra snapshot versionado no CI
- [ ] Configuração alterável sem redeploy, com auditoria
- [ ] Feature flags, tema e textos servidos ao frontend
- [ ] TTL do registro de assinaturas implementado
- [ ] CI verde

Commit: `feat: contrato internal/v1 e configuração da automação`.

Ao terminar, me diga se o snapshot do contrato ficou legível o suficiente para alguém revisar num diff — se o teste falhar e ninguém entender o porquê, ele vira ruído e alguém vai desligá-lo.
