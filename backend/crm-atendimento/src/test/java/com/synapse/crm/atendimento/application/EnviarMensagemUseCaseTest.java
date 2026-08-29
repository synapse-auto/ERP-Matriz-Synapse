package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class EnviarMensagemUseCaseTest {

    @Test
    void mensagemDoConvidadoTransfereLeadEPreservaDonoAnteriorNoEvento() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID donoAnterior = UUID.randomUUID();
        UUID convidado = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

        Atendimento antes = new Atendimento(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                donoAnterior,
                com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO,
                agora.minusSeconds(60),
                null);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(convidado, PapelUsuario.ATENDENTE, false));
        when(leads.contatoParaEnvio(leadId))
                .thenReturn(Optional.of(new LeadNoCaminhoDeMensagem.ContatoParaEnvio(
                        "5561999999999", Optional.of(agora.minusSeconds(10)))));
        when(leads.transferirPara(leadId, convidado))
                .thenReturn(LeadNoCaminhoDeMensagem.Transferencia.de(donoAnterior));
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(antes));
        when(atendimentos.salvar(any(Atendimento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(canal.aceitaTextoLivre(any(), any())).thenReturn(true);

        EnviarMensagemUseCase useCase = new EnviarMensagemUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos,
                Clock.fixed(agora, ZoneOffset.UTC),
                mock(com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio.class),
                mock(com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio.class),
                mock(com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio.class));

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "oi");

        assertThat(resultado.transferiuOLead()).isTrue();
        assertThat(resultado.atendimento().atendenteId()).isEqualTo(convidado);
        verify(leads).transferirPara(leadId, convidado);

        ArgumentCaptor<Object> eventoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, times(2)).publishEvent(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues()).anySatisfy(evento -> {
            assertThat(evento).isInstanceOf(EventoDeAtendimento.MensagemEnviada.class);
            EventoDeAtendimento.MensagemEnviada mensagem = (EventoDeAtendimento.MensagemEnviada) evento;
            assertThat(mensagem.transferiu()).isTrue();
            assertThat(mensagem.donoAnterior()).contains(donoAnterior);
            assertThat(mensagem.remetenteId()).isEqualTo(convidado);
        });
    }

    @Test
    void respostaSemWamidNaoGravaMensagem() {
        UUID leadId = UUID.randomUUID();
        UUID origemId = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-29T12:00:00Z");
        Instant origemEm = agora.minusSeconds(30);

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        var origens = mock(com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio.class);
        var idsExternos = mock(com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio.class);
        var referencias = mock(com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio.class);

        UUID dono = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(dono, PapelUsuario.ATENDENTE, false));
        Mensagem origem = Mensagem.texto(
                origemId,
                atendimentoId,
                Remetente.lead(),
                "oi",
                origemEm);
        when(origens.buscar(origemId, origemEm))
                .thenReturn(Optional.of(new com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem(
                        origem, leadId, "Cliente", null)));
        when(atendimentos.porId(atendimentoId))
                .thenReturn(Optional.of(new Atendimento(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        dono,
                        com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO,
                        agora.minusSeconds(60),
                        null)));
        when(idsExternos.wamidDe(origemId, origemEm)).thenReturn(Optional.empty());

        EnviarMensagemUseCase useCase = new EnviarMensagemUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos,
                Clock.fixed(agora, ZoneOffset.UTC), origens, idsExternos, referencias);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.executar(
                        leadId,
                        new com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio.MensagemLivre("resposta"),
                        new com.synapse.crm.atendimento.application.referencia.AlvoDeResposta(origemId, origemEm)))
                .isInstanceOf(com.synapse.crm.atendimento.domain.mensagem.RespostaAoCanalIndevidaException.class);

        verify(mensagens, org.mockito.Mockito.never()).registrar(any());
        verify(outbox, org.mockito.Mockito.never()).enfileirarEnvio(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
