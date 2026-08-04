# 07. Estratégia Base PAI — Multi-instância e Reuso

**Modelo escolhido:** instância isolada por cliente (deploy + banco próprios por "filho").

---

## 1. O que "multi-tenant por instância" significa na prática

Cada filho é um deploy completo e independente:

```
┌─────────────────────────────────────────────────────────┐
│  BASE PAI (repositório core, versionado)                │
│  • crm-core, atendimento, campanhas, automacao-config…  │
│  • migrations Flyway                                    │
│  • design system + componentes Next.js                  │
└────────────────────────┬────────────────────────────────┘
                         │ (versão v1.4.2)
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Estrutural   │ │ Filho 2      │ │ Filho 3      │
│ Vidros       │ │              │ │              │
│ • DB próprio │ │ • DB próprio │ │ • DB próprio │
│ • config.yml │ │ • config.yml │ │ • config.yml │
│ • tema/logo  │ │ • tema/logo  │ │ • tema/logo  │
│ • v1.4.2     │ │ • v1.4.2     │ │ • v1.3.0     │
└──────────────┘ └──────────────┘ └──────────────┘
```

**Consequências diretas:**

- Zero risco de vazamento de dados entre clientes — o isolamento é físico, não lógico.
- Um filho pode ficar numa versão anterior sem bloquear os demais (útil quando um cliente está no meio de uma operação crítica).
- Uma queda afeta um cliente só.
- **Em troca:** N pipelines de deploy, N bancos para backup/monitorar, e a pergunta "como propago uma correção para todos?" precisa de resposta estruturada. É o resto deste documento.

---

## 2. Como o core chega nos filhos

### 2.1 Opções avaliadas

| Estratégia | Como funciona | Veredito |
|---|---|---|
| **Fork por cliente** | Copia o repo e customiza | ❌ Divergência garantida. Em 6 meses são 8 bases diferentes e nenhuma correção se propaga. |
| **Template repo + merge do upstream** | Cada filho é um repo criado a partir do template, com `git remote upstream` para puxar atualizações | ⚠️ Funciona, mas conflitos de merge crescem com a customização. |
| **Core como biblioteca versionada** | Core publicado como artefato (Maven/npm privado); cada filho é um repo fino que declara a dependência | ✅ **Recomendado.** Atualizar = bump de versão. Customização por configuração e extensão, não por edição. |

### 2.2 Estrutura recomendada

**Backend — monorepo Maven multi-módulo:**

```
synapse-crm-base/                 (repositório Base PAI)
├── pom.xml                       (parent, gerencia versões)
├── crm-shared-kernel/            → JAR publicado
├── crm-core/                     → JAR publicado
├── crm-atendimento/              → JAR publicado
├── crm-campanhas/                → JAR publicado
├── crm-automacao-config/         → JAR publicado
├── crm-equipe/                   → JAR publicado
├── crm-relatorios/               → JAR publicado
└── crm-app-template/             (aplicação executável de referência)
```

**Cada filho:**

```
estrutural-vidros-crm/            (repositório fino do filho)
├── pom.xml                       (depende de synapse-crm-base:1.4.2)
├── src/main/resources/
│   ├── application.yml           (config da instância)
│   ├── tema.json                 (cores, logo, tipografia)
│   ├── textos.json               (labels, nomes de cards)
│   └── db/migration/tenant/      (migrations específicas, se houver)
└── src/main/java/                (só extensões/adaptadores específicos)
```

**Regra de ouro:** se você precisou **editar** um arquivo do core para atender um filho, isso é um sinal de que faltou um ponto de extensão no core. Corrija no core (adicionando o ponto de extensão), não no filho.

**Frontend — mesma lógica com npm privado:**

```
@synapse/crm-ui           → design system + componentes (publicado)
@synapse/crm-features     → telas completas (Atendimentos, Kanban, etc.)
estrutural-vidros-web     → app Next.js fino, importa os pacotes e injeta tema
```

> **Nota de prazo:** publicar artefatos em registry privado (GitHub Packages) custa ~meio dia de configuração. Dado o prazo até 7/8, uma alternativa pragmática é **começar como monorepo único** com os módulos já fisicamente separados e a fronteira respeitada, e só extrair para artefatos publicados quando o segundo filho existir. A separação física dos módulos é o que importa agora; o mecanismo de distribuição pode esperar. Isso não é dívida técnica se a fronteira já estiver correta — é só empacotamento.

---

## 3. Camadas de customização (do mais barato ao mais caro)

Quando um filho pedir algo diferente, resolva no nível mais baixo possível desta lista:

**Nível 1 — Configuração de dados** (sem deploy, sem código)
Textos, cores, tempos de follow-up, etapas do funil, tags, mensagens automáticas, horários. Tudo já vive em tabelas (`configuracao_automacao`, `etapa_atendimento`, `tag`, `regra_follow_up`).

**Nível 1b — Campos customizados por filho** (sem deploy, sem migration)

O caso que quebra a Base PAI mais rápido: um filho precisa de um campo que os outros não têm. A vidraçaria quer "número da obra"; uma clínica quer "convênio"; uma imobiliária quer "código do imóvel".

**Resolver com coluna nova é nichar o core.** Na terceira coluna específica de cliente, a tabela `lead` não serve mais como base para ninguém.

Solução: JSONB no lead + tabela de metadados descrevendo os campos.

```sql
-- Valores: uma coluna, qualquer campo
ALTER TABLE lead ADD COLUMN dados_customizados JSONB NOT NULL DEFAULT '{}';
CREATE INDEX idx_lead_dados_customizados ON lead USING gin (dados_customizados);

-- Metadados: o que existe, como se chama, como valida, como renderiza
CREATE TABLE campo_customizado (
    chave        VARCHAR(60) PRIMARY KEY,   -- 'numero_obra'
    rotulo       VARCHAR(120) NOT NULL,     -- 'Número da Obra'
    tipo         VARCHAR(20) NOT NULL,      -- TEXTO|NUMERO|DATA|BOOLEANO|LISTA
    opcoes       JSONB,                     -- para LISTA
    obrigatorio  BOOLEAN NOT NULL DEFAULT FALSE,
    filtravel    BOOLEAN NOT NULL DEFAULT FALSE,
    ordem        SMALLINT NOT NULL DEFAULT 0
);
```

Três propriedades que fazem isso funcionar:

- **O core não sabe quais campos existem.** A UI renderiza a partir de `campo_customizado`; nenhum componente conhece "número da obra".
- **`filtravel` conversa com a allowlist do filtro modular.** Campo customizado marcado como filtrável entra na allowlist dinamicamente — sem abrir brecha, porque a chave é validada contra a tabela, não contra a entrada do cliente.
- **É configuração, não código.** Um filho novo popula `campo_customizado` e pronto.

> **Onde não usar:** campo que *todos* os filhos vão querer não é customizado — é campo do core. Se três clientes seguidos criam o mesmo campo customizado, promova para coluna. O JSONB é para a cauda longa, não para adiar decisão de modelagem.

**Nível 2 — Feature flags** (sem deploy)
Ligar/desligar módulos inteiros por filho: `campanhas.habilitado`, `chat_interno.habilitado`, `fidelizacao.habilitado`. O filho que não comprou campanhas simplesmente não vê a aba.

**Nível 3 — Arquivo de configuração da instância** (deploy, sem código)
`tema.json`, `textos.json`, credenciais de canal, URL da Automação.

**Nível 4 — Adaptador específico do filho** (código, isolado)
Integração com um ERP que só aquele cliente usa: implementa uma porta do core, mora no repo do filho, não toca no core.

**Nível 5 — Alteração no core** (código, afeta todos)
Só quando a necessidade é genuinamente genérica. Entra como ponto de extensão + versão nova, nunca como `if (cliente == "estrutural")`.

> Se você se pegar escrevendo `if (tenant == X)` no core, pare: é sempre um Nível 4 disfarçado.

---

## 4. Configuração da instância

```yaml
# application.yml de cada filho
synapse:
  tenant:
    codigo: estrutural-vidros
    nome-exibicao: "Estrutural Vidros"
    timezone: America/Sao_Paulo
  canal:
    whatsapp:
      provedor: ${WHATSAPP_PROVEDOR}
      numero-principal: ${WHATSAPP_NUMERO}      # trocável sem deploy (ver §5)
      token: ${WHATSAPP_TOKEN}
      webhook-secret: ${WHATSAPP_WEBHOOK_SECRET}
  automacao:
    url-base: ${AUTOMACAO_URL}
    token-permanente: ${AUTOMACAO_TOKEN}         # o "token de filho para filho"
  alertas:
    webhook-grupo: ${ALERTA_WEBHOOK}             # onde avisar em caso de queda
  features:
    campanhas: true
    chat-interno: true
    fidelizacao: true
```

Segredos vêm de variáveis de ambiente / secret manager — nunca commitados. Isso é o Twelve-Factor App aplicado: **a mesma imagem de container roda em qualquer filho, variando só a configuração injetada**.

---

## 5. Troca do número principal de atendimento

Requisito interno específico, com uma armadilha: trocar o número **não pode quebrar o histórico**.

**Modelagem:** o número não é uma coluna solta, é um registro versionado.

```sql
CREATE TABLE canal_credencial (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canal_id          UUID NOT NULL REFERENCES canal(id),
    numero            VARCHAR(30) NOT NULL,
    identificador_externo VARCHAR(120),   -- phone_number_id do provedor
    token_ref         VARCHAR(200),        -- referência ao secret manager, NUNCA o token em si
    ativo             BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_desde     TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente_ate       TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_canal_credencial_ativa
    ON canal_credencial (canal_id) WHERE ativo;
```

**Comportamento na troca:**

1. Cadastra-se a nova credencial como inativa e valida a conexão com o provedor.
2. A troca marca a antiga com `vigente_ate = now()`, `ativo = false`, e ativa a nova.
3. Atendimentos e mensagens **históricos continuam apontando para a credencial antiga** (por isso ela não é deletada) — o histórico permanece íntegro e auditável.
4. Evento de auditoria registrado; webhook do provedor reapontado para o novo número.

O índice único parcial garante, no nível do banco, que nunca haja duas credenciais ativas para o mesmo canal — uma proteção que não depende de o código estar correto.

---

## 6. Contrato estável com a Automação

O requisito é claro: mudar "no máximo a URL e o token" de filho para filho. Isso transforma a API interna em **contrato público versionado**:

- Namespace fixo: `/internal/v1/...` — idêntico em todos os filhos.
- Autenticação: token permanente por instância no header `X-Synapse-Token`, validado contra a config do tenant.
- **OpenAPI gerado automaticamente** (springdoc-openapi) e publicado como parte do release do core — a documentação vira artefato de build, não um documento que envelhece.
- Mudança incompatível ⇒ `/internal/v2`, com v1 mantido durante a transição. Nenhum filho pode ser forçado a atualizar a Automação junto com o CRM.
- **Testes de contrato** no pipeline do core: se um PR quebrar a forma de uma resposta de `/internal/v1`, o build falha. Isso é o que impede que a Automação de 8 clientes quebre por um refactor distraído.

---

## 7. Observabilidade e alerta por instância

Cada filho reporta para uma stack central de monitoramento, identificando-se pelo `tenant.codigo`:

- Logs estruturados JSON com `tenant`, `trace_id`, `usuario_id` em todo evento.
- Métricas com label `tenant` — permite um painel único da Synapse com a saúde de todos os filhos.
- **Watchdog externo** (fora do deploy do filho) fazendo *polling* em `/health/critical` a cada 30s. Duas falhas consecutivas ⇒ alerta no grupo do cliente + no canal interno da Synapse.

O alerta precisa distinguir "sistema fora" de "função crítica degradada" — avisar o cliente que o WhatsApp desconectou é útil; avisar que o relatório está lento gera ruído e treina o cliente a ignorar os alertas.

---

## 8. Roadmap interno (pós-entrega)

Os dois itens do roadmap interno ficam bem servidos por esta arquitetura:

**Mini front-end da Base PAI** — cada filho já expõe `/internal/v1` autenticado por token; um painel central que consulta N instâncias é uma agregação sobre APIs que já existem, não um sistema novo.

**Sistema de Novidades com integração GitHub** — como o core é versionado com releases semânticos, o changelog do GitHub *é* a fonte das novidades. Um endpoint `GET /api/v1/novidades` no filho lê a versão instalada e exibe as releases desde a última vista pelo usuário.

Nenhum dos dois exige mudança arquitetural — os dois caem naturalmente do modelo de core versionado + instância configurável. Isso é um bom sinal de que o modelo está certo.
