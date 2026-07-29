-- =========================================================
-- Campos customizados por filho (E06b).
--
-- O schema e generico de proposito (CLAUDE.md): nenhuma tabela e de
-- vidracaria. Mas sem um ponto de extensao, o primeiro campo que so um filho
-- precisa ("numero da obra", "convenio") vira ALTER TABLE lead ADD COLUMN — e
-- na terceira coluna especifica de cliente a tabela deixa de servir de base
-- para qualquer outro.
--
-- Tabela vazia custa zero; migration futura em producao custa janela e risco
-- (docs/09 §3.1). Por isso o schema entra agora, ainda que a UI de gestao
-- desses campos seja fase 2 — populado por SQL/migration ate la.
-- =========================================================

ALTER TABLE lead ADD COLUMN dados_customizados JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN lead.dados_customizados IS
    'Campos especificos do filho, fora do schema generico (docs/07 3 Nivel 1b). '
    'Chaves sempre validadas contra campo_customizado — nunca aceitas cruas do cliente.';

CREATE INDEX idx_lead_dados_customizados ON lead USING gin (dados_customizados);

-- ---------------------------------------------------------------------------
-- campo_customizado: o metadado que torna dados_customizados seguro de usar.
--
-- Sem esta tabela, "chave valida" dependeria de disciplina de quem escreve
-- cada consulta. Com ela, toda leitura ou escrita de dados_customizados passa
-- por uma allowlist do banco — o mesmo principio da allowlist estatica do
-- filtro modular (E03b), so que com uma parte que muda sem deploy.
-- ---------------------------------------------------------------------------
CREATE TABLE campo_customizado (
    chave       VARCHAR(60) PRIMARY KEY,
    rotulo      VARCHAR(120) NOT NULL,
    tipo        VARCHAR(20) NOT NULL CHECK (tipo IN ('TEXTO','NUMERO','DATA','BOOLEANO','LISTA')),
    opcoes      JSONB,
    obrigatorio BOOLEAN NOT NULL DEFAULT FALSE,
    filtravel   BOOLEAN NOT NULL DEFAULT FALSE,
    ordem       SMALLINT NOT NULL DEFAULT 0,

    -- A chave vira caminho JSONB dentro de uma consulta (jsonb_extract_path_text)
    -- e, quando filtravel, entra na allowlist DINAMICA do filtro modular. Um
    -- CHECK aqui e a segunda parede — a primeira e a validacao na aplicacao —
    -- para que nem um INSERT direto no banco (migration futura, script de
    -- suporte) consiga criar uma chave fora do formato seguro.
    CONSTRAINT chk_campo_customizado_chave CHECK (chave ~ '^[a-z][a-z0-9_]{2,59}$')
);

COMMENT ON TABLE campo_customizado IS
    'Metadado dos campos especificos do filho. Fase 1: populado por migration/SQL. '
    'Fase 2: tela de gestao (criar/editar/reordenar).';
COMMENT ON COLUMN campo_customizado.opcoes IS
    'Lista de valores permitidos, so para tipo=LISTA. Array JSON de string, ex.: ["Vidro","Aluminio"].';
COMMENT ON COLUMN campo_customizado.filtravel IS
    'Se true, a chave entra na allowlist dinamica do filtro modular (E03b + E06b).';
