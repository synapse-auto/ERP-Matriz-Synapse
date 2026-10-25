package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

@SpringBootTest
class ParticipacaoAtendimentoIT extends PostgresIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transacao;

    @AfterEach
    void sair() { ApoioRls.sair(); }

    @Test
    void dois_pedidos_do_mesmo_solicitante_retorna_o_mesmo_id_e_uma_linha() {
        UUID dono = usuario("dono");
        UUID solicitante = usuario("solicitante");
        UUID lead = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES(?,?,?,'EM_ATENDIMENTO'::status_basico_lead)", lead, "pedido " + lead, dono);
        jdbc.update("INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES(?,?,?,'EM_ATENDIMENTO'::status_atendimento)", atendimento, lead, dono);

        ApoioRls.entrarComo(solicitante, PapelUsuario.ATENDENTE);
        UUID primeiro = transacao.execute(s -> jdbc.queryForObject("SELECT app_registrar_pedido_entrada(?,?)", UUID.class, atendimento, solicitante));
        UUID segundo = transacao.execute(s -> jdbc.queryForObject("SELECT app_registrar_pedido_entrada(?,?)", UUID.class, atendimento, solicitante));

        assertThat(segundo).isEqualTo(primeiro);
        Long quantidade = transacao.execute(s -> jdbc.queryForObject("SELECT count(*) FROM pedido_entrada_atendimento WHERE atendimento_id=? AND solicitante_id=?", Long.class, atendimento, solicitante));
        assertThat(quantidade).isEqualTo(1L);
    }

    @Test
    void busca_restrita_nao_tem_colunas_de_historico_ficha_ou_etapa() {
        UUID dono = usuario("busca-dono");
        UUID lead = UUID.randomUUID();
        String nome = "contato restrito " + lead;
        jdbc.update("INSERT INTO lead(id,nome,empresa,atendente_responsavel_id,status_basico) VALUES(?,?,?,?,'EM_ATENDIMENTO'::status_basico_lead)", lead, nome, "empresa restrita", dono);
        jdbc.update("INSERT INTO atendimento(lead_id,atendente_id,status) VALUES(?,?,'EM_ATENDIMENTO'::status_atendimento)", lead, dono);
        UUID solicitante = usuario("busca-solicitante");
        ApoioRls.entrarComo(solicitante, PapelUsuario.ATENDENTE);
        Map<String, Object> linha = transacao.execute(s -> jdbc.queryForMap("SELECT id,nome,empresa,responsavel_id,responsavel_nome FROM app_buscar_lead_para_entrada(?,?)", nome, solicitante));
        assertThat(linha.keySet()).containsExactlyInAnyOrder("id", "nome", "empresa", "responsavel_id", "responsavel_nome");
        assertThat(linha).doesNotContainKeys("mensagens", "historico", "etapa", "ficha", "notas", "resumo_ia");
    }

    private UUID usuario(String sufixo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,nome,email,senha_hash,papel) VALUES(?,?,?,'x','ATENDENTE'::papel_usuario)", id, sufixo, sufixo + id + "@rls.test");
        return id;
    }
}
