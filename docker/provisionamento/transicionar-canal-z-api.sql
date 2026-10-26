-- Operacao EXPLICITA no banco existente. Nao altera FKs historicas, mensagens ou outboxes.
-- psql -X -v banco_esperado=... -v origem=UUID_UZAPI -v destino=UUID_ZAPI
-- -v operacao=PREPARAR|ATIVAR|SUSPENDER|LIBERAR_PROGRAMADA -v confirmar=APLICAR
-- LIBERAR_PROGRAMADA exige adicionalmente -v programada=UUID, uma por vez apos revisao.
-- Sem confirmar=APLICAR, simula e termina em ROLLBACK.
\set ON_ERROR_STOP on
\set VERBOSITY terse
\if :{?banco_esperado}
\else
 \set banco_esperado ''
\endif
\if :{?origem}
\else
 \set origem ''
\endif
\if :{?destino}
\else
 \set destino ''
\endif
\if :{?operacao}
\else
 \set operacao ''
\endif
\if :{?confirmar}
\else
 \set confirmar SIMULAR
\endif
\if :{?programada}
\else
 \set programada ''
\endif
BEGIN;
SET LOCAL lock_timeout='3s';
SET LOCAL statement_timeout='5s';
CREATE TEMP TABLE parametros_transicao ON COMMIT DROP AS SELECT
 :'banco_esperado'::text banco,:'origem'::text origem,:'destino'::text destino,
 :'operacao'::text operacao,:'confirmar'::text confirmacao,:'programada'::text programada;
DO $$
DECLARE p record; antiga canal_credencial%ROWTYPE; nova canal_credencial%ROWTYPE;
 t canal_transicao%ROWTYPE; corte timestamptz;
BEGIN
 SELECT * INTO p FROM parametros_transicao;
 IF p.banco='' OR p.banco<>current_database() THEN RAISE EXCEPTION 'Banco inesperado'; END IF;
 IF p.confirmacao NOT IN ('SIMULAR','APLICAR') THEN RAISE EXCEPTION 'Confirmacao invalida';END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls))
 THEN RAISE EXCEPTION 'Exige operador de manutencao com BYPASSRLS'; END IF;
 IF NOT pg_try_advisory_xact_lock(hashtextextended('synapse:transicao-canal',0))
 THEN RAISE EXCEPTION 'Ha transacao de envio em andamento; nao aguardar bloqueando o chat'; END IF;
 SELECT * INTO antiga FROM canal_credencial WHERE id=p.origem::uuid FOR UPDATE;
 IF NOT FOUND OR antiga.identificador_externo IS NULL THEN RAISE EXCEPTION 'Credencial origem invalida';END IF;
 SELECT * INTO nova FROM canal_credencial WHERE id=p.destino::uuid FOR UPDATE;
 IF NOT FOUND OR nova.id=antiga.id OR nova.canal_id=antiga.canal_id
    OR nova.token_ref IS DISTINCT FROM 'env://Z_API_TOKEN' OR nova.vigente_ate IS NOT NULL
    OR nova.vigente_desde>now() OR nova.identificador_externo !~ '^[A-Za-z0-9_-]{1,120}$'
 THEN RAISE EXCEPTION 'Destino Z-API invalido; nao reutilizar identidade Uzapi';END IF;
 SELECT * INTO t FROM canal_transicao FOR UPDATE;
 IF FOUND AND (t.origem_credencial_id<>antiga.id OR t.destino_credencial_id<>nova.id)
 THEN RAISE EXCEPTION 'Existe outra transicao; exige revisao, nao sobrepor';END IF;
 IF p.operacao='PREPARAR' THEN
   IF t.id IS NULL THEN
     IF NOT antiga.ativo OR NOT EXISTS(SELECT 1 FROM canal WHERE id=antiga.canal_id AND ativo)
     THEN RAISE EXCEPTION 'Origem nao e o canal ativo'; END IF;
     INSERT INTO canal_transicao(origem_credencial_id,destino_credencial_id,provedor_origem,provedor_destino,
       identificador_origem,identificador_destino,estado)
     VALUES(antiga.id,nova.id,'uzapi-autotic','z-api',antiga.identificador_externo,nova.identificador_externo,'PREPARADA')
     RETURNING * INTO t;
   END IF;
 ELSIF p.operacao='ATIVAR' THEN
   IF t.id IS NULL THEN RAISE EXCEPTION 'Execute PREPARAR primeiro'; END IF;
   IF t.estado='ATIVA' THEN RETURN; END IF;
   IF t.estado<>'PREPARADA' THEN RAISE EXCEPTION 'Recuperacao exige conciliacao; nao reativar automaticamente'; END IF;
   IF EXISTS(SELECT 1 FROM canal WHERE ativo AND id NOT IN(antiga.canal_id,nova.canal_id))
   THEN RAISE EXCEPTION 'Outro canal ativo; transicao ambigua';END IF;
   IF EXISTS(SELECT 1 FROM atendimento a WHERE finalizado_em IS NULL AND (
       (a.canal_credencial_id IS NOT NULL AND a.canal_credencial_id<>antiga.id) OR
       (a.canal_credencial_id IS NULL AND a.canal_id IS NOT NULL AND a.canal_id<>antiga.canal_id)))
   THEN RAISE EXCEPTION 'Atendimento aberto de outra identidade; revisar inventario';END IF;
   corte=clock_timestamp();
   INSERT INTO atendimento_rota_canal(atendimento_id,transicao_id,credencial_historica_id)
   SELECT a.id,t.id,a.canal_credencial_id FROM atendimento a WHERE a.finalizado_em IS NULL;
   INSERT INTO canal_transicao_programada(programada_id,transicao_id)
   SELECT id,t.id FROM mensagem_programada WHERE status='AGENDADA';
   -- Preserva inclusive os campos/estado da credencial Uzapi. So o CANAL deixa de ser principal.
   UPDATE canal SET ativo=false WHERE id=antiga.canal_id;
   UPDATE canal SET ativo=true WHERE id=nova.canal_id;
   UPDATE canal_credencial SET ativo=true WHERE id=nova.id;
   UPDATE canal_transicao SET estado='ATIVA',ativada_em=corte WHERE id=t.id;
   INSERT INTO audit_log(ator_tipo,acao,entidade_tipo,entidade_id,dados_antes,dados_depois)
   SELECT 'SISTEMA','TRANSICAO_ROTA_DE_ATENDIMENTO','ATENDIMENTO',r.atendimento_id,
     jsonb_build_object('credencialHistoricaId',r.credencial_historica_id),
     jsonb_build_object('transicaoId',t.id,'credencialDeSaidaId',nova.id,'executorSql',current_user)
   FROM atendimento_rota_canal r WHERE r.transicao_id=t.id;
 ELSIF p.operacao='SUSPENDER' THEN
   IF t.id IS NULL THEN RAISE EXCEPTION 'Transicao inexistente';END IF;
   UPDATE canal_transicao SET estado='RECUPERACAO' WHERE id=t.id;
 ELSIF p.operacao='LIBERAR_PROGRAMADA' THEN
   IF t.id IS NULL OR t.estado<>'ATIVA' OR p.programada='' THEN RAISE EXCEPTION 'Exige transicao ativa e UUID revisado';END IF;
   UPDATE canal_transicao_programada SET liberada_em=clock_timestamp()
   WHERE transicao_id=t.id AND programada_id=p.programada::uuid AND liberada_em IS NULL;
   IF NOT FOUND THEN RAISE EXCEPTION 'Programada inexistente, ja revisada ou de outra transicao';END IF;
 ELSE RAISE EXCEPTION 'Operacao invalida';END IF;
 INSERT INTO audit_log(ator_tipo,acao,entidade_tipo,entidade_id,dados_depois)
 VALUES('SISTEMA','TRANSICAO_CANAL_'||p.operacao,'CANAL_TRANSICAO',t.id,
   jsonb_build_object('executorSql',current_user,'programadaId',NULLIF(p.programada,'')));
END $$;
SELECT id AS transicao_id,estado,preparada_em,ativada_em,
 (SELECT count(*) FROM atendimento_rota_canal) AS rotas,
 (SELECT count(*) FROM canal_transicao_programada WHERE liberada_em IS NULL) AS programadas_retidas
FROM canal_transicao;
SELECT :'confirmar'='APLICAR' AS aplicar_transicao \gset
\if :aplicar_transicao
 COMMIT;
\else
 ROLLBACK;
 \echo 'SIMULACAO: nenhuma alteracao persistida.'
\endif
