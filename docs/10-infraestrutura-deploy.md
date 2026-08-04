# 10. Infraestrutura e Deploy

Decisão de hospedagem e checklist de pré-requisitos do ambiente. Este documento existe para que a pergunta não volte a cada etapa.

---

## 1. Hospedagem

**Contexto:** o EasyPanel gratuito (developer edition) limita a 2 serviços. O compose do projeto tem 5 por instância: Postgres, Redis, RabbitMQ, backend Spring Boot e frontend Next.js. Multiplicado por cliente, no modelo Base PAI.

### 1.1 Duas restrições eliminatórias

**Região São Paulo, não Europa.** Hetzner e Contabo são bem mais baratos, mas ficam na Europa — ~200ms até Brasília contra ~15ms de São Paulo. Isso aparece em dois lugares que importam: o round-trip dos webhooks do WhatsApp e a fluidez percebida do chat, que é requisito explícito. A economia não compensa.

**Backup com PITR no Postgres.** É o maior risco isolado do stack. Downtime de 8h é ruim; perder o banco é irreversível. Postgres em container de VPS sem point-in-time recovery é onde não se economiza.

### 1.1b Decisão de backup — homologação vs. produção

**Homologação (11/08): `pg_dump` de hora em hora para S3-compatível.** Sem PITR. A homologação não tem dado que doa perder, e WAL-G é complexidade na semana mais apertada.

> **⚠️ Dívida com data: PITR entra antes do go-live de produção.** `pg_dump` horário significa perder até uma hora de conversa num incidente — aceitável em homologação, não com o cliente operando. Tarefa própria: WAL-G arquivando WAL para o mesmo bucket, ou migração para Postgres gerenciado com PITR incluído.

**Registry: GitHub Container Registry (`ghcr.io`).** Gratuito para repositório privado da organização; o CI publica, o Dokploy puxa. O modo Docker Stack exige imagem pré-compilada em registry para aplicar `start-first` — sem isso, não há deploy sem downtime.

### 1.2 Recomendação

**VPS em São Paulo + Dokploy** como camada de deploy.

- Dokploy é mais leve (roda bem em 2GB) e Docker-first
- Coolify tem mais recursos e backups agendados embutidos, mas consome 2–4GB só para si
- Para dev solo, Dokploy pela simplicidade — menos superfície para dar problema às 9h da manhã

**Alternativa válida:** EasyPanel pago (Hobby ~US$ 13,90/mês ou Business ~US$ 31,90/mês) remove o limite de serviços. Se a migração de ferramenta custar tempo que não existe antes de 25/08, pagar é a escolha racional.

### 1.3 Watchdog em outro provedor — não negociável

O alerta de indisponibilidade só funciona se o monitor sobreviver à queda do monitorado. Se o VPS cair inteiro, um watchdog hospedado nele cai junto e o cliente descobre antes de você — exatamente o cenário que o requisito existe para evitar.

Um Uptime Kuma num VPS mínimo de **outro fornecedor**, ou um serviço gratuito de uptime, resolve.

### 1.4 Co-locação de filhos

Dá para rodar 2–3 clientes pequenos no mesmo VPS com bancos separados. Barato, mas enfraquece a garantia de "queda de um não afeta outro" que o modelo de instância isolada oferece.

**A Estrutural Vidros fica sozinha no host** — é o primeiro cliente, o mais complexo, e o que recebeu promessa explícita de estabilidade.

---

## 2. Checklist de pré-requisitos do ambiente

Verificar **antes** do primeiro deploy de homologação. Todos os três já causaram ou causariam falha de boot:

- [ ] **`pg_trgm` disponível.** Exige privilégio elevado. Funciona no container e no Testcontainers (superusuário), mas em Postgres gerenciado pode precisar ser habilitada fora da migration — e a V1 falharia. Está na allowlist da maioria dos provedores. *(`pgcrypto` foi removida na E01b — Postgres 13+ tem `gen_random_uuid()` nativo.)*

- [ ] **Usuário das migrations pode `CREATE ROLE`** e conceder `synapse_app` a si mesmo. **Se as migrations rodarem com usuário diferente do da aplicação, o `GRANT` vai para o usuário errado e a aplicação falha ao assumir a role** — e o RLS deixa de funcionar. Faça um teste de fumaça no primeiro deploy: logar como atendente e confirmar que ele não vê lead de colega.

- [ ] **Pool em modo transaction** (PgBouncer), se usado. O RLS depende de `SET LOCAL`; `SET` de sessão vazaria contexto entre usuários.

- [ ] **Partições de `mensagem` cobertas.** O boot falha se faltar a do mês corrente ou próximo. Confirme que o job mensal está ativo no ambiente.

- [ ] **Java 21 no runtime**, conforme `AGENTS.md`. Não 25.

- [ ] **Limites de mídia conferidos contra a documentação atual da Meta.** Configurados hoje: imagem 5 MB, áudio 16 MB, documento 100 MB — valores históricos usados como seed e teto de fallback. A Meta muda isso sem aviso, e o sintoma é upload rejeitado pelo provedor depois de o atendente já ter esperado o envio. Os valores são editáveis na tela de Configurações, sem tocar em código.

---

## 3. Ambiente de homologação

O documento interno prevê entregar à subgestora 10–15 dias antes da implantação. Com meta em 25/08, isso significa homologação disponível **por volta de 11/08**, ao fim da semana 2 — quando Atendimentos já funciona.

Não espere estar tudo pronto. O valor do acompanhamento dela é pegar desalinhamento cedo, e um ambiente incompleto que ela consegue usar vale mais que um completo que chega tarde.

Sources: [Easypanel Pricing](https://easypanel.io/pricing) · [Coolify vs Easypanel — Contabo](https://contabo.com/blog/coolify-vs-easypanel-best-paas/) · [Dokploy vs Coolify — INTROSERV](https://introserv.com/blog/dokploy-vs-coolify-complete-comparison-of-the-best-self-hosted-paas-platforms-for-vps-and-dedicated-servers-2026/)
