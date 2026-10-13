-- =========================================================
-- V104: atendente vinculado e chaves de distribuicao (D3b e D3c da Disbrel, docs/71). Tudo DESLIGADO.
--
-- Esta migration nao muda nenhuma consulta existente. As tres chaves nascem `false` e a tabela de vinculo so e
-- escrita quando `ia.distribuicao.atendente_vinculado` estiver `true`; com ela `false` o rodizio e a transferencia
-- se comportam exatamente como antes. Ligar uma chave e operacao pontual por instancia (PUT
-- /api/v1/automacao/config/{chave} ou o script de provisionamento), nunca por migration.
--
-- Depende da V103 (setores): ordem de merge #295 (V102) -> #296 (V103) -> esta.
-- =========================================================

-- Vinculo explicito lead -> ultimo atendente humano que recebeu/assumiu. NAO e acesso: nenhuma politica RLS nem
-- a VisibilidadeLeadSpecification le esta tabela (RN-CRM-01/02/06 intactas). So o rodizio da IA a consulta, e
-- nenhuma rota a devolve ao navegador. Sem RLS, como `disponibilidade_atendente_ia`: nao guarda dado comercial.
CREATE TABLE lead_atendente_vinculado (
    lead_id       UUID PRIMARY KEY REFERENCES lead(id) ON DELETE CASCADE,
    atendente_id  UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    vinculado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    origem        VARCHAR(40) NOT NULL
);

COMMENT ON TABLE lead_atendente_vinculado IS
    'Ultimo atendente humano que recebeu/assumiu o lead. So orienta o rodizio da IA; nao concede acesso ao lead.';
COMMENT ON COLUMN lead_atendente_vinculado.origem IS
    'O que gravou o vinculo: TRANSFERENCIA (inclui assumir e rodizio) ou ENVIO_MANUAL (RN-CRM-06).';

-- Desativar/excluir o usuario remove o vinculo (ON DELETE CASCADE) ou o deixa sem efeito: o rodizio so honra o
-- vinculado ativo, disponivel e fora de setor que nao recebe da triagem.
CREATE INDEX idx_lead_atendente_vinculado_atendente ON lead_atendente_vinculado (atendente_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON lead_atendente_vinculado TO synapse_app;

-- BOOLEAN fecha os dois estados e reaproveita a validacao do CRUD de configuracao, como ia.distribuicao.sequencial (V71).
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('ia.distribuicao.atendente_vinculado', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Distribuicao da IA: true entrega o lead de volta ao atendente vinculado (se ativo, online e disponivel) antes do rodizio; false mantem so o rodizio.'),
    ('ia.distribuicao.por_botoes', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Modo de distribuicao escolhido para a Automacao: true = o cliente escolhe por botoes; false = rodizio automatico. O CRM so guarda e expoe; quem age e a Automacao.'),
    ('ia.distribuicao.perguntar_preferencia', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'A Automacao pergunta ao cliente se ele tem atendente de preferencia. O CRM so guarda e expoe; quem age e a Automacao.')
ON CONFLICT (chave) DO NOTHING;
