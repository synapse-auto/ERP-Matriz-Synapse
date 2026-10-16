-- Contas existentes permanecem comuns. Nenhuma classificacao por identidade/aproximacao.
ALTER TABLE usuario ADD COLUMN usuario_teste BOOLEAN NOT NULL DEFAULT FALSE;

-- Consulta nao correlacionada nos read models: um InitPlan por SQL, nao uma leitura por usuario.
CREATE FUNCTION app_administrador_vigente() RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM usuario
        WHERE id = NULLIF(current_setting('app.usuario_id', TRUE), '')::uuid
          AND ativo AND papel = 'ADMINISTRADOR');
$$;

-- Read models de equipe compartilham o mesmo corte, antes de busca/contagem/paginacao.
-- A autenticacao continua lendo usuario; a view nao altera sessoes, Specification ou RLS de leads.
CREATE VIEW usuario_visivel WITH (security_barrier = true) AS
SELECT id,nome,email,senha_hash,papel,status_presenca,ativo,criado_em,
       telefone,cargo,foto_referencia,senha_alterada_em,usuario_teste
FROM usuario WHERE NOT usuario_teste OR (SELECT app_administrador_vigente());

-- Inventario das relacoes operacionais. Sessao, presenca e configuracao nao sao autoria comercial.
-- A mesma lista alimenta a verificacao anterior e a protecao de novos vinculos.
CREATE VIEW usuario_teste_relacao_operacional AS
SELECT DISTINCT n.nspname AS esquema, c.relname AS tabela, a.attname AS coluna
FROM pg_constraint fk
JOIN pg_class c ON c.oid = fk.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY(fk.conkey)
WHERE fk.contype = 'f' AND fk.confrelid = 'usuario'::regclass AND n.nspname = 'public'
  AND NOT c.relispartition
  AND c.relname NOT IN ('refresh_token','presenca_historico','disponibilidade_atendente_ia',
      'rotina_disponibilidade_atendente','usuario_setor','permissao_perfil','permissao_usuario',
      'permissao_usuario_excecao','audit_log')
UNION SELECT 'public', 'mensagem', 'remetente_id';

-- Retorna somente uma amostra tecnica por relacao. Nao retorna conteudo nem identidade.
-- SECURITY DEFINER restrita ao ADM vigente e ao contexto SERVICO apenas dentro da verificacao;
-- restaura o contexto inclusive em falha. Necessario para detectar vinculos ocultos pela RLS.
CREATE FUNCTION app_vinculos_usuario_teste(p_usuario UUID)
RETURNS TABLE(recurso TEXT, registro_id TEXT) LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public AS $$
DECLARE r RECORD; encontrado TEXT; papel_anterior TEXT; identificador TEXT;
BEGIN
    IF NOT public.app_administrador_vigente() THEN
        RAISE EXCEPTION 'CLASSIFICACAO_TESTE_SOMENTE_ADM' USING ERRCODE = '42501';
    END IF;
    papel_anterior := current_setting('app.papel', TRUE);
    PERFORM set_config('app.papel', 'SERVICO', TRUE);
    FOR r IN SELECT esquema,tabela,coluna FROM public.usuario_teste_relacao_operacional
        ORDER BY CASE tabela WHEN 'lead' THEN 0 WHEN 'atendimento' THEN 1 ELSE 2 END,tabela,coluna LOOP
        identificador := CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema=r.esquema AND table_name=r.tabela AND column_name='id') THEN 'id' ELSE r.coluna END;
        EXECUTE format('SELECT %I::text FROM %I.%I WHERE %I = $1 LIMIT 1',
            identificador, r.esquema, r.tabela, r.coluna) INTO encontrado USING p_usuario;
        IF encontrado IS NOT NULL THEN
            recurso := r.tabela || '.' || r.coluna; registro_id := encontrado; RETURN NEXT;
        END IF;
    END LOOP;
    -- Referencias historicas e comandos em JSON nao possuem FK para usuario.
    SELECT e.id::text INTO encontrado FROM public.evento_timeline e
      WHERE jsonb_path_exists(e.dados, '$.** ? (@ == $id)', jsonb_build_object('id', p_usuario::text)) LIMIT 1;
    IF encontrado IS NOT NULL THEN recurso := 'evento_timeline.dados'; registro_id := encontrado; RETURN NEXT; END IF;
    SELECT e.id::text INTO encontrado FROM public.outbox_evento e
      WHERE jsonb_path_exists(e.payload, '$.** ? (@ == $id)', jsonb_build_object('id', p_usuario::text)) LIMIT 1;
    IF encontrado IS NOT NULL THEN recurso := 'outbox_evento.payload'; registro_id := encontrado; RETURN NEXT; END IF;
    SELECT e.id::text INTO encontrado FROM public.permissao_historico e
      WHERE e.autor_id=p_usuario OR e.origem_usuario_id=p_usuario LIMIT 1;
    IF encontrado IS NOT NULL THEN recurso := 'permissao_historico.autoria'; registro_id := encontrado; RETURN NEXT; END IF;
    SELECT e.id::text INTO encontrado FROM public.audit_log e
      WHERE e.ator_id=p_usuario AND e.acao NOT IN ('TROCAR_SENHA','BLOQUEIO_CONTA_TESTE') LIMIT 1;
    IF encontrado IS NOT NULL THEN recurso := 'audit_log.autoria'; registro_id := encontrado; RETURN NEXT; END IF;
    PERFORM set_config('app.papel', COALESCE(papel_anterior, ''), TRUE);
EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.papel', COALESCE(papel_anterior, ''), TRUE); RAISE;
END $$;

REVOKE ALL ON FUNCTION app_vinculos_usuario_teste(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_vinculos_usuario_teste(UUID) TO synapse_app;

CREATE FUNCTION app_validar_classificacao_teste() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND NOT NEW.usuario_teste)
       OR (TG_OP = 'UPDATE' AND NEW.usuario_teste = OLD.usuario_teste) THEN RETURN NEW; END IF;
    IF NOT app_administrador_vigente() OR NEW.id = app_usuario_id() THEN
        RAISE EXCEPTION 'CLASSIFICACAO_TESTE_SOMENTE_ADM' USING ERRCODE = '42501';
    END IF;
    IF NEW.usuario_teste AND EXISTS (SELECT 1 FROM app_vinculos_usuario_teste(NEW.id)) THEN
        RAISE EXCEPTION 'USUARIO_TESTE_COM_VINCULOS' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validar_classificacao_teste BEFORE INSERT OR UPDATE OF usuario_teste ON usuario
FOR EACH ROW EXECUTE FUNCTION app_validar_classificacao_teste();

-- FOR SHARE serializa com a classificacao concorrente; vale tambem para jobs/filas/SERVICO.
CREATE FUNCTION app_impedir_vinculo_conta_teste() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE campo TEXT; alvo UUID; teste BOOLEAN;
BEGIN
    FOREACH campo IN ARRAY TG_ARGV LOOP
        alvo := NULLIF(to_jsonb(NEW)->>campo, '')::uuid;
        IF alvo IS NOT NULL THEN
            SELECT usuario_teste INTO teste FROM usuario WHERE id = alvo FOR SHARE;
            IF teste THEN RAISE EXCEPTION 'CONTA_TESTE_SOMENTE_LEITURA' USING ERRCODE = '23514'; END IF;
        END IF;
    END LOOP;
    RETURN NEW;
END $$;

-- Validacao de um identificador, sem copiar conteudo/JSON da mensagem ou da ficha.
-- Os criadores SECURITY DEFINER de conversa usam synapse_chat_rls, que so pode ler usuario.
-- O lock exige UPDATE: esta funcao estreita obtem a trava como seu dono, sem conceder escrita
-- na tabela a role do chat. Nao retorna identidade e nao altera o usuario.
CREATE FUNCTION app_exigir_usuario_operacional(p_usuario UUID) RETURNS VOID LANGUAGE plpgsql
SECURITY DEFINER SET search_path = pg_catalog, public AS $$
DECLARE teste BOOLEAN;
BEGIN
    SELECT usuario_teste INTO teste FROM public.usuario WHERE id=p_usuario FOR SHARE;
    IF teste THEN RAISE EXCEPTION 'CONTA_TESTE_SOMENTE_LEITURA' USING ERRCODE='23514'; END IF;
END $$;
REVOKE ALL ON FUNCTION app_exigir_usuario_operacional(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_exigir_usuario_operacional(UUID) TO synapse_app, synapse_chat_rls;

-- Referencias historicas sem FK; fora do caminho de mensagens.
CREATE TRIGGER impedir_origem_teste_permissao BEFORE INSERT OR UPDATE ON permissao_historico
FOR EACH ROW EXECUTE FUNCTION app_impedir_vinculo_conta_teste('origem_usuario_id','autor_id');

DO $$ DECLARE r RECORD; campo RECORD; corpo TEXT; colunas TEXT;
BEGIN
    FOR r IN SELECT esquema, tabela
        FROM usuario_teste_relacao_operacional GROUP BY esquema, tabela LOOP
        corpo := 'BEGIN '; colunas := '';
        FOR campo IN SELECT coluna FROM usuario_teste_relacao_operacional
            WHERE esquema=r.esquema AND tabela=r.tabela ORDER BY coluna LOOP
            IF r.tabela='mensagem' AND campo.coluna='remetente_id' THEN
                -- O UUID do cliente nao e usuario. Recebimento da IA/lead nao consulta equipe.
                corpo := corpo || 'IF NEW.remetente_tipo=''ATENDENTE'' AND NEW.remetente_id IS NOT NULL THEN PERFORM app_exigir_usuario_operacional(NEW.remetente_id); END IF; ';
            ELSE
                corpo := corpo || format('IF NEW.%I IS NOT NULL AND (TG_OP=''INSERT'' OR NEW.%I IS DISTINCT FROM OLD.%I) THEN PERFORM app_exigir_usuario_operacional(NEW.%I); END IF; ',
                    campo.coluna,campo.coluna,campo.coluna,campo.coluna);
            END IF;
            colunas := colunas || CASE WHEN colunas='' THEN '' ELSE ',' END || quote_ident(campo.coluna);
        END LOOP;
        IF r.tabela='mensagem' THEN colunas := colunas || ',remetente_tipo'; END IF;
        corpo := corpo || 'RETURN NEW; END;';
        EXECUTE format('CREATE FUNCTION %I() RETURNS TRIGGER LANGUAGE plpgsql AS %L',
            'app_usuario_teste_' || r.tabela,corpo);
        EXECUTE format('CREATE TRIGGER impedir_vinculo_conta_teste BEFORE INSERT OR UPDATE OF %s ON %I.%I FOR EACH ROW EXECUTE FUNCTION %I()',
            colunas,r.esquema,r.tabela,'app_usuario_teste_' || r.tabela);
    END LOOP;
END $$;

-- Classificacao muda visibilidade e invalida a revisao usada pelos caches de permissao.
CREATE FUNCTION app_revisar_classificacao_teste() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.usuario_teste IS DISTINCT FROM OLD.usuario_teste THEN
        UPDATE permissao_politica SET revisao = revisao + 1 WHERE id = 1;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER revisar_classificacao_teste AFTER UPDATE OF usuario_teste ON usuario
FOR EACH ROW EXECUTE FUNCTION app_revisar_classificacao_teste();
