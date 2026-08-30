package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.EncaminhamentoIncompativelException;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

class EncaminharMensagemUseCaseTest {

    @Test
    void botoesNaoSaoEncaminhaveisENaoChamamEnvio() {
        UUID origemAtendimentoId = UUID.randomUUID();
        UUID destinoAtendimentoId = UUID.randomUUID();
        UUID origemLead = UUID.randomUUID();
        UUID destinoLead = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        Instant quando = Instant.parse("2026-08-29T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        OrigemDeMensagemRepositorio origens = mock(OrigemDeMensagemRepositorio.class);
        EnviarMensagemUseCase enviar = mock(EnviarMensagemUseCase.class);

        Atendimento origemAtendimento = new Atendimento(
                origemAtendimentoId,
                origemLead,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                quando,
                null);
        Atendimento destino = new Atendimento(
                destinoAtendimentoId,
                destinoLead,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                quando,
                null);
        Mensagem origem = Mensagem.interativa(
                mensagemId,
                origemAtendimentoId,
                Remetente.ia(),
                TipoMensagem.BOTOES,
                "escolha",
                "[{\"id\":\"1\",\"titulo\":\"A\"}]",
                quando);

        when(atendimentos.porId(origemAtendimentoId)).thenReturn(Optional.of(origemAtendimento));
        when(atendimentos.porId(destinoAtendimentoId)).thenReturn(Optional.of(destino));
        when(origens.buscar(mensagemId, quando))
                .thenReturn(Optional.of(new OrigemDeMensagem(origem, origemLead, "Lead", "IA")));

        EncaminharMensagemUseCase useCase = new EncaminharMensagemUseCase(atendimentos, origens, enviar);

        assertThatThrownBy(() -> useCase.executar(
                        origemAtendimentoId, mensagemId, quando, destinoAtendimentoId))
                .isInstanceOf(EncaminhamentoIncompativelException.class);

        verify(enviar, never()).executarComReferencia(any(), any(), any());
    }

    @Test
    void destinoForaDaVisibilidadeNaoChamaEnvio() {
        UUID origemAtendimentoId = UUID.randomUUID();
        UUID destinoAtendimentoId = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        Instant quando = Instant.parse("2026-08-29T12:00:00Z");
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        OrigemDeMensagemRepositorio origens = mock(OrigemDeMensagemRepositorio.class);
        EnviarMensagemUseCase enviar = mock(EnviarMensagemUseCase.class);

        Atendimento origemAtendimento = new Atendimento(
                origemAtendimentoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                quando,
                null);
        Mensagem origem = Mensagem.texto(
                mensagemId, origemAtendimentoId, Remetente.lead(), "oi", quando);

        when(atendimentos.porId(origemAtendimentoId)).thenReturn(Optional.of(origemAtendimento));
        when(origens.buscar(mensagemId, quando))
                .thenReturn(Optional.of(new OrigemDeMensagem(
                        origem, origemAtendimento.leadId(), "Lead", null)));
        when(atendimentos.porId(destinoAtendimentoId)).thenReturn(Optional.empty());

        EncaminharMensagemUseCase useCase = new EncaminharMensagemUseCase(atendimentos, origens, enviar);

        assertThatThrownBy(() -> useCase.executar(
                        origemAtendimentoId, mensagemId, quando, destinoAtendimentoId))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);

        verify(enviar, never()).executarComReferencia(any(), any(), any());
    }

    @Test
    void encaminhaImagemReusandoAChaveDeStorage() {
        UUID origemAtendimentoId = UUID.randomUUID();
        UUID destinoAtendimentoId = UUID.randomUUID();
        UUID origemLead = UUID.randomUUID();
        UUID destinoLead = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        Instant quando = Instant.parse("2026-08-29T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        OrigemDeMensagemRepositorio origens = mock(OrigemDeMensagemRepositorio.class);
        EnviarMensagemUseCase enviar = mock(EnviarMensagemUseCase.class);

        Atendimento origemAtendimento = new Atendimento(
                origemAtendimentoId,
                origemLead,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                quando,
                null);
        Atendimento destino = new Atendimento(
                destinoAtendimentoId,
                destinoLead,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                quando,
                null);
        Mensagem origem = Mensagem.midia(
                mensagemId,
                origemAtendimentoId,
                Remetente.lead(),
                TipoMensagem.IMAGEM,
                "arquivos/chave-origem",
                "{\"legenda\":\"vao\"}",
                quando);

        when(atendimentos.porId(origemAtendimentoId)).thenReturn(Optional.of(origemAtendimento));
        when(atendimentos.porId(destinoAtendimentoId)).thenReturn(Optional.of(destino));
        when(origens.buscar(mensagemId, quando))
                .thenReturn(Optional.of(new OrigemDeMensagem(origem, origemLead, "Lead", null)));

        EncaminharMensagemUseCase useCase = new EncaminharMensagemUseCase(atendimentos, origens, enviar);
        useCase.executar(origemAtendimentoId, mensagemId, quando, destinoAtendimentoId);

        verify(enviar)
                .executarComReferencia(
                        org.mockito.ArgumentMatchers.eq(destinoLead),
                        org.mockito.ArgumentMatchers.argThat(
                                conteudo ->
                                        conteudo instanceof com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio.MensagemMidia midia
                                                && "arquivos/chave-origem".equals(midia.referenciaStorage())
                                                && "vao".equals(midia.legenda())),
                        any());
    }
}
