-- EV-08 secao 5: o n8n consulta GET /internal/v1/automation-config e procura esta chave para
-- decidir se envia os tres botoes de satisfacao ao cliente. Sem a linha, ele le a lista e nao
-- acha nada. O CRM nao le este valor (RN-CRM-07: configura a automacao, nao a executa); o gate
-- do lado do CRM continua sendo URL + token + nome de header validos.
--
-- Nasce false: quem liga e o gestor, na tela de Automacao, quando o workflow do n8n estiver
-- pronto. Ligar por padrao mandaria pesquisa a cliente real no primeiro deploy.
INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES ('avaliacao_atendimento.habilitada', 'false', NULL, 'BOOLEAN', NULL, NULL,
        'Liga a pesquisa de satisfacao pos-atendimento executada pelo n8n (contrato EV-08).')
ON CONFLICT (chave) DO NOTHING;
