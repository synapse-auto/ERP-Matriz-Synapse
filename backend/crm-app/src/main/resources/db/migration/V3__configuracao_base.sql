-- =========================================================
-- Canal, credencial de canal e etapas do funil.
-- canal_credencial precede atendimento (V5), que a referencia.
-- =========================================================

CREATE TABLE canal (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome  VARCHAR(50) NOT NULL UNIQUE,
    tipo  VARCHAR(30) NOT NULL,  -- 'WHATSAPP', extensivel
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Credencial versionada: trocar o numero principal nao pode quebrar o
-- historico, entao a credencial antiga e desativada, nunca deletada.
-- Ver docs/07-base-pai-multitenancy.md, secao 5.
CREATE TABLE canal_credencial (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canal_id              UUID NOT NULL REFERENCES canal(id),
    numero                VARCHAR(30) NOT NULL,
    identificador_externo VARCHAR(120),  -- ex.: phone_number_id do provedor
    token_ref             VARCHAR(200),  -- REFERENCIA ao secret manager, nunca o token
    ativo                 BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_desde         TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente_ate           TIMESTAMPTZ
);

CREATE TABLE etapa_atendimento (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome       VARCHAR(80) NOT NULL,
    ordem      SMALLINT NOT NULL,
    cor_visual VARCHAR(20)
);

-- ---------------------------------------------------------
-- Constraints de unicidade.
--
-- Escritas com sintaxe de indice, mas sao regra de negocio, nao performance.
-- Por isso ficam aqui, junto das tabelas que protegem, e nao na V10: separar
-- a constraint da tabela abre uma janela em que a tabela existe sem a garantia.
-- ---------------------------------------------------------

-- Nunca pode haver duas credenciais ativas no mesmo canal. Garantido pelo
-- banco para valer mesmo com dois processos concorrentes, onde uma validacao
-- na aplicacao nao protegeria.
CREATE UNIQUE INDEX idx_canal_credencial_ativa
    ON canal_credencial (canal_id) WHERE ativo;

-- A ordem das etapas do funil e posicao no Kanban: duas etapas na mesma
-- posicao deixariam a tela nao-determinista.
CREATE UNIQUE INDEX idx_etapa_ordem ON etapa_atendimento (ordem);
