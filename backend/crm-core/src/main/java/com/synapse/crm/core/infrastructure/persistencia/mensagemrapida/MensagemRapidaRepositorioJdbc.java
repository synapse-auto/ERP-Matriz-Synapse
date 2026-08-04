package com.synapse.crm.core.infrastructure.persistencia.mensagemrapida;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.mensagemrapida.EscopoMensagensRapidas;
import com.synapse.crm.core.application.mensagemrapida.MensagemRapidaRepositorio;
import com.synapse.crm.core.application.mensagemrapida.PalavraChaveEmUsoException;
import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

@Repository
class MensagemRapidaRepositorioJdbc implements MensagemRapidaRepositorio {
    private static final String BASE =
            "SELECT m.id,m.atendente_id,u.nome atendente_nome,m.palavra_chave,m.conteudo,m.tipo_midia"
                    + " FROM mensagem_rapida m JOIN usuario u ON u.id=m.atendente_id";

    private final JdbcTemplate jdbc;

    MensagemRapidaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MensagemRapida> listar(EscopoMensagensRapidas escopo) {
        TransacaoObrigatoria.exigir("listar mensagens rapidas");
        return escopo.todas()
                ? jdbc.query(
                        BASE + " ORDER BY u.nome,m.palavra_chave",
                        MensagemRapidaRepositorioJdbc::mapear)
                : jdbc.query(
                        BASE + " WHERE m.atendente_id=? ORDER BY m.palavra_chave",
                        MensagemRapidaRepositorioJdbc::mapear,
                        escopo.usuarioId());
    }

    @Override
    public MensagemRapida criar(UUID atendente, String chave, String conteudo) {
        TransacaoObrigatoria.exigir("criar mensagem rapida");
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO mensagem_rapida(id,atendente_id,palavra_chave,conteudo) VALUES(?,?,?,?)",
                    id,
                    atendente,
                    chave,
                    conteudo);
        } catch (DuplicateKeyException erro) {
            throw new PalavraChaveEmUsoException();
        }
        return porId(id).orElseThrow();
    }

    @Override
    public Optional<MensagemRapida> atualizar(
            UUID id, EscopoMensagensRapidas escopo, String chave, String conteudo) {
        TransacaoObrigatoria.exigir("atualizar mensagem rapida");
        try {
            int alteradas = escopo.todas()
                    ? jdbc.update(
                            "UPDATE mensagem_rapida SET palavra_chave=?,conteudo=? WHERE id=?",
                            chave,
                            conteudo,
                            id)
                    : jdbc.update(
                            "UPDATE mensagem_rapida SET palavra_chave=?,conteudo=? WHERE id=? AND atendente_id=?",
                            chave,
                            conteudo,
                            id,
                            escopo.usuarioId());
            return alteradas == 0 ? Optional.empty() : porId(id);
        } catch (DuplicateKeyException erro) {
            throw new PalavraChaveEmUsoException();
        }
    }

    @Override
    public boolean remover(UUID id, EscopoMensagensRapidas escopo) {
        TransacaoObrigatoria.exigir("remover mensagem rapida");
        int removidas = escopo.todas()
                ? jdbc.update("DELETE FROM mensagem_rapida WHERE id=?", id)
                : jdbc.update(
                        "DELETE FROM mensagem_rapida WHERE id=? AND atendente_id=?",
                        id,
                        escopo.usuarioId());
        return removidas == 1;
    }

    private Optional<MensagemRapida> porId(UUID id) {
        return jdbc.query(
                        BASE + " WHERE m.id=?", MensagemRapidaRepositorioJdbc::mapear, id)
                .stream()
                .findFirst();
    }

    private static MensagemRapida mapear(ResultSet linha, int indice) throws SQLException {
        return new MensagemRapida(
                linha.getObject("id", UUID.class),
                linha.getObject("atendente_id", UUID.class),
                linha.getString("atendente_nome"),
                linha.getString("palavra_chave"),
                linha.getString("conteudo"),
                linha.getString("tipo_midia"));
    }
}
