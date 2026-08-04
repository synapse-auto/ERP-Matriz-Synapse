# Prompt E14a — Deploy de homologação

> Pré-requisito: E13 commitada e **enviada ao `origin`**.
> Leia `AGENTS.md` e `docs/10-infraestrutura-deploy.md` antes de começar.
> Esta etapa envolve infraestrutura real. Onde precisar de credencial ou decisão de custo, **pare e pergunte** — não invente.

---

**Etapa E14a — Subir o CRM num ambiente que a subgestora possa usar.**

Alvo: **11/08**. É o primeiro contato do cliente com o produto, e o começo da validação contínua prevista no documento interno.

## 0. Antes de qualquer coisa: o checklist de ambiente

O `docs/10` §2 lista cinco pré-requisitos que **já causaram ou causariam falha de boot**. Verifique cada um no ambiente de destino antes de subir:

- **`pg_trgm` disponível.** Exige privilégio elevado; em Postgres gerenciado pode precisar ser habilitada fora da migration, e a V1 falharia
- **Usuário das migrations pode `CREATE ROLE`** e conceder `synapse_app` a si mesmo. **Se o usuário das migrations for diferente do da aplicação, o `GRANT` vai para o errado e o RLS para de funcionar em silêncio** — este é o mais perigoso da lista
- **Pool em modo transaction**, se usar PgBouncer. O RLS depende de `SET LOCAL`
- **Partições de `mensagem` cobertas.** O boot falha por desenho se faltar a do mês corrente ou próximo
- **Java 21 no runtime.** Não 25

Depois de subir, faça o **teste de fumaça do RLS**: logue como atendente e confirme que ele não vê lead de colega. Se vir, o `GRANT` foi para o usuário errado — e nenhum teste automatizado pegaria isso, porque no CI o usuário é o mesmo.

## 1. Provisionamento

**Plataforma: Dokploy**, num VPS em São Paulo (ver `docs/10` §1 para o raciocínio).

Serviços da instância:

- Postgres 15+ (banco próprio deste filho, não compartilhado)
- Redis
- RabbitMQ
- Backend Spring Boot
- Frontend Next.js
- MinIO ou S3-compatível para mídia

**Três configurações que não são padrão e importam:**

**Rolling update com `start-first`.** No Swarm o padrão é `stop-first` — derruba o container antigo antes de subir o novo, o que é downtime. Isso viola a regra de precedência das 08:00–18:30:

```yaml
deploy:
  update_config:
    order: start-first
    failure_action: rollback
```

**Restart apontando para `/health/liveness`, nunca para `/health/critical`.** O `liveness` não olha o banco de propósito (decidido na E00): uma oscilação do Postgres não pode reiniciar a aplicação. Se o healthcheck do orquestrador apontar para o `critical`, o container entra em loop de restart exatamente durante um incidente de banco — quando você mais precisa dele de pé para diagnosticar.

**Limites de recurso por serviço** (`deploy.resources.limits`). É o Bulkhead no nível de infra, mesma lógica dos dois DataSources. Sem isso, um container consumindo toda a RAM derruba os vizinhos e o modelo Silo perde o isolamento que deveria dar.

## 2. Configuração da instância

Tudo por variável de ambiente e arquivo, nada hardcoded:

- Bloco `synapse:` do `application.yml` — código do tenant, timezone, URLs
- Credenciais da Meta (número, token, webhook secret) — **secret manager ou variável de ambiente**, nunca commitadas; o banco guarda só `token_ref`
- `tema.json` e `textos.json` da Estrutural
- Token permanente da Automação
- Segredo do JWT — gerado para este ambiente, não reaproveitado do dev

**Feature flags conforme `docs/09`:** `campanhas`, `relatorios` e `banco_arquivos` em `false`.

## 3. HTTPS e webhook

O webhook da Meta **exige certificado válido** — autoassinado não funciona, e sem webhook o CRM não recebe mensagem.

- Domínio apontado, Let's Encrypt via Traefik
- Webhook da Meta reapontado para a URL de homologação
- Valide a assinatura chegando de verdade: mande uma mensagem real e confirme que aparece na tela

## 4. Provisionamento inicial dos dados

O seed de desenvolvimento **não roda em produção** (por perfil Spring). Este ambiente precisa de:

- Usuário administrador com senha forte, entregue de forma segura
- Etapas do funil conforme a operação real da Estrutural — confirme com a subgestora, não invente
- Tags iniciais
- Credencial do canal WhatsApp
- Parâmetros de `configuracao_automacao` com faixas válidas
- **Limites de mídia conferidos contra a documentação atual da Meta** (`docs/10` §2)

**Escreva isso como script ou checklist versionado.** Você vai repetir para cada filho, e a terceira vez é onde se esquece um item.

## 5. Backup — antes de existir dado, não depois

- Backup do Postgres agendado para S3-compatível
- **De hora em hora durante o horário comercial**, não de 4 em 4 horas. Duas horas de conversa perdida é dano real ao cliente
- Retenção de pelo menos 7 dias

**Teste a restauração agora**, com o banco ainda vazio de dados reais: restaure num banco novo e confirme que a aplicação sobe. Com 20 migrations e a role `synapse_app`, tem mais etapas que um `pg_restore` simples — e backup nunca restaurado é esperança, não backup.

## 6. Observabilidade mínima

- Logs acessíveis pela interface do Dokploy
- Log estruturado com `tenant`, `trace_id` e `usuario_id`

A parte de alerta é a **E09b**, que roda logo depois desta — o watchdog só faz sentido quando existe algo hospedado para vigiar.

## Definição de pronto

- [ ] Os cinco itens do checklist de ambiente verificados
- [ ] **Teste de fumaça do RLS passando** — atendente não vê lead de colega no ambiente real
- [ ] Aplicação no ar com HTTPS válido
- [ ] Mensagem real trocada com o WhatsApp, ponta a ponta
- [ ] Deploy sem downtime confirmado (suba uma versão nova e observe)
- [ ] Restart apontando para `liveness`
- [ ] Backup agendado **e restauração testada**
- [ ] Provisionamento inicial documentado como script ou checklist versionado
- [ ] Flags conforme `docs/09`
- [ ] Commit e push das configurações versionáveis

Ao terminar, me diga: quanto de RAM sobrou, quanto tempo levou o deploy, e o que você precisou fazer manualmente que não estava previsto aqui — isso vira o roteiro do segundo filho.
