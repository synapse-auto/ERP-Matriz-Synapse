package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.usuario.ClassificacaoDeTesteRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class ClassificacaoDeTesteRepositorioJdbc implements ClassificacaoDeTesteRepositorio {
    private final JdbcTemplate jdbc;
    private final JdbcTemplate chat;
    ClassificacaoDeTesteRepositorioJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dados,
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDados) {
        jdbc = new JdbcTemplate(dados);
        chat = new JdbcTemplate(chatDados);
    }
    public boolean administradorVigente(UUID ator) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario WHERE id=? AND ativo AND papel='ADMINISTRADOR' AND NOT usuario_teste)", Boolean.class, ator));
    }
    public boolean contaDeTeste(UUID usuario) {
        return Boolean.TRUE.equals(chat.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario WHERE id=? AND usuario_teste)", Boolean.class, usuario));
    }
    public List<Vinculo> vinculos(UUID usuario) {
        return jdbc.query("SELECT recurso, registro_id FROM app_vinculos_usuario_teste(?)",
                (r,i) -> new Vinculo(r.getString(1), r.getString(2)), usuario);
    }
    public void classificar(UUID usuario, boolean teste) {
        try {
            if (jdbc.update("UPDATE usuario SET usuario_teste=? WHERE id=?", teste, usuario) != 1)
                throw new IllegalStateException("Classificacao nao persistida na transacao do usuario.");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (e.getMostSpecificCause().getMessage().contains("USUARIO_TESTE_COM_VINCULOS"))
                throw new com.synapse.crm.equipe.application.usuario.UsuarioPossuiVinculosException(List.of());
            throw e;
        }
        if (teste) jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia=FALSE, atualizado_em=now() WHERE atendente_id=?", usuario);
    }
    public java.util.Set<UUID> usuariosAtivos() {
        return java.util.Set.copyOf(jdbc.queryForList("SELECT id FROM usuario WHERE ativo",UUID.class));
    }
    public void registrarBloqueio(UUID usuario, String operacao) {
        chat.update("INSERT INTO audit_log (ator_id,ator_tipo,acao,entidade_tipo,entidade_id,dados_depois) VALUES(?,'USUARIO','BLOQUEIO_CONTA_TESTE','USUARIO',?,jsonb_build_object('operacao',?::text))",
                usuario, usuario, operacao);
    }
}
