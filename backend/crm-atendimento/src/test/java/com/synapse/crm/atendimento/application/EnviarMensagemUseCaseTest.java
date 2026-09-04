package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class EnviarMensagemUseCaseTest {

    @Test
    void mensagemDoConvidadoPreservaDonoAnteriorNoEvento() {
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
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        Atendimento antes = atendimentoAberto(atendimentoId, leadId, donoAnterior, agora);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(convidado, PapelUsuario.ATENDENTE, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.assumirSeSemDono(leadId, convidado))
                .thenReturn(LeadNoCaminhoDeMensagem.Assuncao.preservado(donoAnterior));
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(antes));
        when(atendimentos.salvar(any(Atendimento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(participacoes.eParticipanteAtivo(atendimentoId, convidado)).thenReturn(false);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "oi");

        assertThat(resultado.transferiuOLead()).isFalse();
        assertThat(resultado.atendimento().atendenteId()).isEqualTo(donoAnterior);
        verify(leads, never()).transferirPara(leadId, convidado);
        verify(leads).bloquearParaAtendimento(leadId);

        ArgumentCaptor<Object> eventoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, times(2)).publishEvent(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues()).anySatisfy(evento -> {
            assertThat(evento).isInstanceOf(EventoDeAtendimento.MensagemEnviada.class);
            EventoDeAtendimento.MensagemEnviada mensagem = (EventoDeAtendimento.MensagemEnviada) evento;
            assertThat(mensagem.transferiu()).isFalse();
            assertThat(mensagem.participante()).isFalse();
            assertThat(mensagem.donoAnterior()).contains(donoAnterior);
            assertThat(mensagem.remetenteId()).isEqualTo(convidado);
        });
    }

    @Test
    void participanteAtivoEnviaSemTransferirLeadNemAtendimento() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        UUID participante = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        Atendimento aberto = atendimentoAberto(atendimentoId, leadId, dono, agora);
        when(contexto.atual())
                .thenReturn(new UsuarioAutenticado(participante, PapelUsuario.SUBGESTOR, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(aberto));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(participacoes.eParticipanteAtivo(atendimentoId, participante)).thenReturn(true);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "vou te ajudar");

        assertThat(resultado.transferiuOLead()).isFalse();
        assertThat(resultado.atendimento().atendenteId()).isEqualTo(dono);
        assertThat(resultado.mensagem().remetente()).isEqualTo(Remetente.atendente(participante));
        verify(leads, never()).transferirPara(leadId, participante);
        verify(leads).bloquearParaAtendimento(leadId);
        verify(leads, never()).marcarStatus(any(), any());
        verify(atendimentos, never()).salvar(any());

        ArgumentCaptor<Object> eventoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, times(2)).publishEvent(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues()).anySatisfy(evento -> {
            assertThat(evento).isInstanceOf(EventoDeAtendimento.MensagemEnviada.class);
            EventoDeAtendimento.MensagemEnviada mensagem = (EventoDeAtendimento.MensagemEnviada) evento;
            assertThat(mensagem.transferiu()).isFalse();
            assertThat(mensagem.participante()).isTrue();
            assertThat(mensagem.donoAnterior()).contains(dono);
            assertThat(mensagem.remetenteId()).isEqualTo(participante);
        });
    }

    @Test
    void participanteFalaEmAtendimentoEmIa_tiraDaIaSemHerdarPosse() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID participante = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        Atendimento emIa = Atendimento.abrirComIa(
                atendimentoId, leadId, UUID.randomUUID(), UUID.randomUUID(), agora.minusSeconds(60));
        when(contexto.atual())
                .thenReturn(new UsuarioAutenticado(participante, PapelUsuario.GESTOR, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(emIa));
        when(atendimentos.salvar(any(Atendimento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(participacoes.eParticipanteAtivo(atendimentoId, participante)).thenReturn(true);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "humano na conversa");

        assertThat(resultado.transferiuOLead()).isFalse();
        assertThat(resultado.atendimento().atendenteId()).isNull();
        assertThat(resultado.atendimento().status())
                .isEqualTo(com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO);
        assertThat(resultado.mensagem().remetente()).isEqualTo(Remetente.atendente(participante));
        verify(leads, never()).transferirPara(leadId, participante);
        verify(leads).marcarStatus(leadId, StatusBasicoLead.EM_ATENDIMENTO);
        verify(atendimentos).salvar(any(Atendimento.class));
    }

    @Test
    void participanteSemAlcanceDoLeadNaoGravaMensagem() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        UUID participante = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        when(contexto.atual())
                .thenReturn(new UsuarioAutenticado(participante, PapelUsuario.ATENDENTE, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(false);
        when(atendimentos.abertoDoLead(leadId))
                .thenReturn(Optional.of(atendimentoAberto(atendimentoId, leadId, dono, agora)));
        when(participacoes.eParticipanteAtivo(atendimentoId, participante)).thenReturn(true);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        assertThatThrownBy(() -> useCase.executar(leadId, "oi"))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);

        verify(leads, never()).transferirPara(leadId, participante);
        verify(mensagens, never()).registrar(any());
        verify(outbox, never()).enfileirarEnvio(any(), any(), any(), any(), any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void donoEnviaNoProprioAtendimentoSemTransferenciaRedundante() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        Atendimento aberto = atendimentoAberto(atendimentoId, leadId, dono, agora);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(dono, PapelUsuario.ATENDENTE, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.assumirSeSemDono(leadId, dono))
                .thenReturn(LeadNoCaminhoDeMensagem.Assuncao.preservado(dono));
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(aberto));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(participacoes.eParticipanteAtivo(atendimentoId, dono)).thenReturn(false);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "oi");

        assertThat(resultado.transferiuOLead()).isFalse();
        assertThat(resultado.atendimento().atendenteId()).isEqualTo(dono);
        verify(leads, never()).transferirPara(leadId, dono);
        verify(atendimentos, never()).salvar(any());
    }

    @Test
    void participanteQueSaiuNaoTransfereAoEnviar() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        UUID exParticipante = UUID.randomUUID();
        Instant agora = Instant.parse("2026-08-24T12:00:00Z");

        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        Outbox outbox = mock(Outbox.class);
        CanalGateway canal = mock(CanalGateway.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);

        Atendimento aberto = atendimentoAberto(atendimentoId, leadId, dono, agora);
        when(contexto.atual())
                .thenReturn(new UsuarioAutenticado(exParticipante, PapelUsuario.GESTOR, false));
        prepararEnvioLivre(leads, canal, leadId, agora);
        when(leads.assumirSeSemDono(leadId, exParticipante))
                .thenReturn(LeadNoCaminhoDeMensagem.Assuncao.preservado(dono));
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Cliente"));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(aberto));
        when(atendimentos.salvar(any(Atendimento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(mensagens.registrar(any(Mensagem.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(participacoes.eParticipanteAtivo(atendimentoId, exParticipante)).thenReturn(false);

        EnviarMensagemUseCase useCase = novoUseCase(
                atendimentos, mensagens, leads, outbox, canal, contexto, eventos, agora, participacoes);

        EnviarMensagemUseCase.Resultado resultado = useCase.executar(leadId, "assumo daqui");

        assertThat(resultado.transferiuOLead()).isFalse();
        assertThat(resultado.atendimento().atendenteId()).isEqualTo(dono);
        verify(leads, never()).transferirPara(leadId, exParticipante);
        verify(leads).bloquearParaAtendimento(leadId);
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
                .thenReturn(Optional.of(atendimentoAberto(atendimentoId, leadId, dono, agora)));
        when(idsExternos.wamidDe(origemId, origemEm)).thenReturn(Optional.empty());

        EnviarMensagemUseCase useCase = new EnviarMensagemUseCase(
                atendimentos,
                mensagens,
                leads,
                outbox,
                canal,
                contexto,
                eventos,
                Clock.fixed(agora, ZoneOffset.UTC),
                origens,
                idsExternos,
                referencias,
                mock(ParticipacaoAtendimentoRepositorio.class));

        assertThatThrownBy(() -> useCase.executar(
                        leadId,
                        new com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio.MensagemLivre("resposta"),
                        new com.synapse.crm.atendimento.application.referencia.AlvoDeResposta(origemId, origemEm)))
                .isInstanceOf(com.synapse.crm.atendimento.domain.mensagem.RespostaAoCanalIndevidaException.class);

        verify(mensagens, never()).registrar(any());
        verify(outbox, never()).enfileirarEnvio(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static void prepararEnvioLivre(
            LeadNoCaminhoDeMensagem leads, CanalGateway canal, UUID leadId, Instant agora) {
        when(leads.contatoParaEnvio(leadId))
                .thenReturn(Optional.of(new LeadNoCaminhoDeMensagem.ContatoParaEnvio(
                        "5561999999999", Optional.of(agora.minusSeconds(10)))));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(canal.aceitaTextoLivre(any(), any())).thenReturn(true);
    }

    private static Atendimento atendimentoAberto(UUID atendimentoId, UUID leadId, UUID dono, Instant agora) {
        return new Atendimento(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                dono,
                com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO,
                agora.minusSeconds(60),
                null);
    }

    private static EnviarMensagemUseCase novoUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            Outbox outbox,
            CanalGateway canal,
            UsuarioContext contexto,
            ApplicationEventPublisher eventos,
            Instant agora,
            ParticipacaoAtendimentoRepositorio participacoes) {
        return new EnviarMensagemUseCase(
                atendimentos,
                mensagens,
                leads,
                outbox,
                canal,
                contexto,
                eventos,
                Clock.fixed(agora, ZoneOffset.UTC),
                mock(com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio.class),
                mock(com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio.class),
                mock(com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio.class),
                participacoes);
    }
}
