# Prompt E06b — Campos customizados (schema apenas)

> Etapa curta: ~1 hora. Pode rodar antes ou depois da E06.
> **Só schema e leitura.** A UI de gestão desses campos é fase 2.

---

**Etapa E06b — Estrutura de campos customizados por filho.**

## Por quê agora

O schema hoje é genérico e serve a qualquer cliente — nenhuma tabela é de vidraçaria. Mas não há onde colocar um campo que só um filho precisa ("número da obra" para a Estrutural, "convênio" para uma clínica).

Sem esse ponto de extensão, o primeiro pedido desse tipo vira `ALTER TABLE lead ADD COLUMN numero_obra` — e na terceira coluna específica de cliente a `lead` não serve mais como base para ninguém.

Vale fazer agora pela mesma regra do `docs/09` §3.1: **tabela vazia custa zero; migration futura em produção custa janela e risco.**

## O que construir

### 1. Migration

```sql
ALTER TABLE lead ADD COLUMN dados_customizados JSONB NOT NULL DEFAULT '{}';
CREATE INDEX idx_lead_dados_customizados ON lead USING gin (dados_customizados);

CREATE TABLE campo_customizado (
    chave        VARCHAR(60) PRIMARY KEY,
    rotulo       VARCHAR(120) NOT NULL,
    tipo         VARCHAR(20) NOT NULL CHECK (tipo IN ('TEXTO','NUMERO','DATA','BOOLEANO','LISTA')),
    opcoes       JSONB,
    obrigatorio  BOOLEAN NOT NULL DEFAULT FALSE,
    filtravel    BOOLEAN NOT NULL DEFAULT FALSE,
    ordem        SMALLINT NOT NULL DEFAULT 0
);
```

`chave` deve validar contra identificador seguro (`^[a-z][a-z0-9_]{2,59}$`) — ela vai virar caminho JSONB numa consulta, então não pode ser texto arbitrário. Valide na aplicação **e** com `CHECK` no banco.

### 2. Leitura e escrita no lead

- `GET /api/v1/campos-customizados` — lista os campos definidos, para a UI renderizar
- `dados_customizados` incluído no `GET /leads/{id}` e aceito no `PUT`
- Validação contra `campo_customizado`: chave não cadastrada é rejeitada, tipo é conferido, `obrigatorio` é exigido

**Não inclua `dados_customizados` na projeção de listagem** — mesma razão de `notas` e `resumo_ia`.

### 3. Integração com o filtro modular

Campo com `filtravel = true` entra na allowlist **dinamicamente**.

O ponto delicado: a allowlist da E03b é estática e por isso segura. Aqui ela passa a ter parte dinâmica, e é onde a injeção voltaria a ser possível.

- A chave é validada contra `campo_customizado` (consulta ao banco), **nunca** contra o que o cliente mandou
- O caminho JSONB é montado com a chave já validada, e o valor sempre como parâmetro
- Operadores permitidos variam por `tipo` — `MAIOR_QUE` não faz sentido em `BOOLEANO`

Teste de injeção obrigatório: chave forjada que não existe em `campo_customizado` é rejeitada; chave com sintaxe maliciosa nem passa do `CHECK`.

### 4. Testes

- Campo cadastrado aparece na listagem de campos
- Lead salva e lê `dados_customizados`
- Chave não cadastrada é rejeitada no `PUT`
- Tipo incompatível é rejeitado
- Campo `filtravel` funciona no filtro modular; campo não filtrável é rejeitado
- Injeção via chave de campo customizado não executa SQL arbitrário
- Listagem não traz `dados_customizados`

## Fora do escopo

Tela de gestão dos campos (criar/editar/reordenar pela UI) é fase 2. Por ora, popula-se por migration ou SQL.

## Definição de pronto

- [ ] Migration aplicada, com `CHECK` na chave
- [ ] Leitura e escrita validadas contra os metadados
- [ ] Filtro dinâmico funcionando, com teste de injeção
- [ ] Listagem sem o JSONB
- [ ] CI verde

Commit: `feat: campos customizados por instância`.
