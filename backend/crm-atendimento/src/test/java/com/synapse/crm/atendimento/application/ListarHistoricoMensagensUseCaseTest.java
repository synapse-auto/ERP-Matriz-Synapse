package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.application.reacao.ReacaoDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class ListarHistoricoMensagensUseCaseTest {

    @Test
    void busca_mensagem_por_id_reutiliza_autorizacao_do_atendimento_e_reacoes() {
        UUID atendimentoId = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        HistoricoDeMensagensRepositorio mensagens = mock(HistoricoDeMensagensRepositorio.class);
        ReacaoDeMensagemRepositorio reacoes = mock(ReacaoDeMensagemRepositorio.class);
        UsuarioContext usuarios = mock(UsuarioContext.class);
        MensagemDoHistorico origem = historico(mensagemId, atendimentoId);

        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(
                Atendimento.abrirComIa(atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now())));
        when(mensagens.porId(atendimentoId, mensagemId)).thenReturn(Optional.of(origem));
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(usuarioId, PapelUsuario.ATENDENTE, false));
        when(reacoes.resumir(List.of(new ReacaoDeMensagemRepositorio.Chave(mensagemId, origem.mensagem().enviadoEm())), usuarioId))
                .thenReturn(java.util.Map.of());

        MensagemDoHistorico resultado = new ListarHistoricoMensagensUseCase(atendimentos, mensagens, reacoes, usuarios)
                .executarPorId(atendimentoId, mensagemId);

        assertThat(resultado.mensagem().id()).isEqualTo(mensagemId);
        verify(mensagens).porId(atendimentoId, mensagemId);
    }

    @Test
    void mensagem_de_outra_conversa_nao_e_exposta() {
        UUID atendimentoId = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        HistoricoDeMensagensRepositorio mensagens = mock(HistoricoDeMensagensRepositorio.class);
        ReacaoDeMensagemRepositorio reacoes = mock(ReacaoDeMensagemRepositorio.class);
        UsuarioContext usuarios = mock(UsuarioContext.class);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(
                Atendimento.abrirComIa(atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now())));
        when(mensagens.porId(atendimentoId, mensagemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListarHistoricoMensagensUseCase(atendimentos, mensagens, reacoes, usuarios)
                .executarPorId(atendimentoId, mensagemId))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
    }

    private static MensagemDoHistorico historico(UUID mensagemId, UUID atendimentoId) {
        Instant enviadoEm = Instant.parse("2026-09-01T12:00:00Z");
        Mensagem mensagem = Mensagem.texto(mensagemId, atendimentoId, Remetente.lead(), "origem", enviadoEm);
        return new MensagemDoHistorico(mensagem, null, atendimentoId, enviadoEm, null, null);
    }
}
