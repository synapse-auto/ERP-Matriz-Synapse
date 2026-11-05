package com.synapse.crm.app.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** SQL direto, com role não proprietária e sem BYPASSRLS: não depende do controller proteger. */
@SpringBootTest
class ColetaDarwinCacheRlsIT extends PostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacao;
    private UUID lead;
    private UUID usuario;
    private UUID outro;

    @BeforeEach void preparar() {
        ApoioRls.sair(); lead=UUID.randomUUID();
        usuario=jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        jdbc.update("INSERT INTO lead(id,nome,status_basico) VALUES (?,'Coleta sintética','IA')",lead);
        outro=UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,status_basico) VALUES (?,'Outra coleta sintética','IA')",outro);
        comoServico(()->jdbc.update("INSERT INTO lead_coleta_cadastral(lead_id,identidade_hash,token,estado,proxima_consulta,repeticao_manual) VALUES (?,repeat('0',64),?,'AGUARDANDO',now(),now())",lead,UUID.randomUUID()));
    }
    @AfterEach void limpar() { ApoioRls.sair(); jdbc.update("DELETE FROM lead WHERE id IN (?,?)",lead,outro); }

    @Test void papeisHumanosNaoLeemNemAlteramCacheMesmoPorSql() {
        for(var papel:java.util.List.of(PapelUsuario.ATENDENTE,PapelUsuario.OPERADOR,PapelUsuario.GESTOR,PapelUsuario.ADMINISTRADOR)) {
            ApoioRls.entrarComo(usuario,papel);
            transacao.executeWithoutResult(s->{
                assertThat(jdbc.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user",Boolean.class)).isFalse();
                assertThat(jdbc.queryForObject("SELECT current_user=pg_get_userbyid(relowner) FROM pg_class WHERE oid='lead_coleta_cadastral'::regclass",Boolean.class)).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM lead_coleta_cadastral WHERE lead_id=?",Integer.class,lead)).isZero();
                assertThat(jdbc.update("UPDATE lead_coleta_cadastral SET estado='FORJADO' WHERE lead_id=?",lead)).isZero();
            });
        }
        ApoioRls.sair();
        assertThat(transacao.execute(s->jdbc.queryForObject("SELECT count(*) FROM lead_coleta_cadastral WHERE lead_id=?",Integer.class,lead))).isZero();
        assertThat(comoServico(()->jdbc.queryForObject("SELECT estado FROM lead_coleta_cadastral WHERE lead_id=?",String.class,lead))).isEqualTo("AGUARDANDO");
    }
    @Test void humanoNaoInsereReservaForjadaMasServicoPodeAtualizar() {
        ApoioRls.entrarComo(usuario,PapelUsuario.GESTOR);
        assertThatThrownBy(()->transacao.executeWithoutResult(s->jdbc.update("INSERT INTO lead_coleta_cadastral(lead_id,identidade_hash,token,estado,proxima_consulta,repeticao_manual) VALUES (?,repeat('0',64),?,'FORJADO',now(),now())",outro,UUID.randomUUID())))
                .isInstanceOfSatisfying(DataAccessException.class, erro ->
                        assertThat(((java.sql.SQLException) erro.getRootCause()).getSQLState()).isEqualTo("42501"));
        assertThat(comoServico(()->jdbc.update("UPDATE lead_coleta_cadastral SET estado='ATUALIZADO' WHERE lead_id=?",lead))).isEqualTo(1);
    }
    private <T> T comoServico(Supplier<T> acao) {
        return ContextoDeServico.buscarComo("teste-rls-coleta-darwin",()->transacao.execute(s->acao.get()));
    }
}
