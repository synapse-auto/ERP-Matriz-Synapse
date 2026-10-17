package com.synapse.crm.app.mensagemprogramada;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.core.application.mensagemprogramada.MensagemProgramadaRepositorio;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

class ProcessarMensagemProgramadaUseCaseTest {

    @Test
    @DisplayName("falha ao materializar nao marca programada como ENVIADA")
    void falhaNaoMarcaComoEnviada() {
        MensagemProgramadaRepositorio mensagens = org.mockito.Mockito.mock(MensagemProgramadaRepositorio.class);
        EnviarMensagemUseCase enviar = org.mockito.Mockito.mock(EnviarMensagemUseCase.class);
        UUID id = UUID.randomUUID();
        MensagemProgramada programada = new MensagemProgramada(
                id,
                UUID.randomUUID(),
                "Lead de teste",
                UUID.randomUUID(),
                "Ana",
                "Falhar de forma observavel",
                Instant.parse("2026-08-26T15:00:00Z"),
                StatusMensagemProgramada.AGENDADA);
        when(mensagens.reservarVencida(id, Instant.parse("2026-08-26T15:00:00Z")))
                .thenReturn(Optional.of(programada));
        when(enviar.executarComoServico(
                        org.mockito.ArgumentMatchers.eq(programada.leadId()),
                        org.mockito.ArgumentMatchers.eq(programada.atendenteId()),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(id)))
                .thenThrow(new IllegalStateException("outbox indisponivel"));

        ProcessarMensagemProgramadaUseCase casoDeUso = new ProcessarMensagemProgramadaUseCase(
                mensagens,
                enviar,
                org.mockito.Mockito.mock(com.synapse.crm.atendimento.application.origem.RegistroDeOrigemDaMensagem.class),
                Clock.fixed(Instant.parse("2026-08-26T15:00:00Z"), ZoneOffset.UTC),
                org.mockito.Mockito.mock(com.synapse.crm.equipe.application.usuario.ClassificacaoDeTesteRepositorio.class),
                org.mockito.Mockito.mock(com.synapse.crm.equipe.application.usuario.AuditarBloqueioContaTesteUseCase.class));

        assertThatThrownBy(() -> casoDeUso.processar(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox indisponivel");
    }

    @Test
    void contaTesteBloqueadaNaExecucaoNaoEnviaNemCancelaAgendamento() {
        var mensagens = org.mockito.Mockito.mock(MensagemProgramadaRepositorio.class);
        var enviar = org.mockito.Mockito.mock(EnviarMensagemUseCase.class);
        var classificacao = org.mockito.Mockito.mock(com.synapse.crm.equipe.application.usuario.ClassificacaoDeTesteRepositorio.class);
        var auditoria = org.mockito.Mockito.mock(com.synapse.crm.equipe.application.usuario.AuditarBloqueioContaTesteUseCase.class);
        var agora = Instant.parse("2026-08-26T15:00:00Z");
        var programada = new MensagemProgramada(UUID.randomUUID(), UUID.randomUUID(), "Sintetico", UUID.randomUUID(), "Autor", "Texto sintetico", agora, StatusMensagemProgramada.AGENDADA);
        when(mensagens.reservarVencida(programada.id(), agora)).thenReturn(Optional.of(programada));
        when(classificacao.contaDeTeste(programada.atendenteId())).thenReturn(true);
        var caso = new ProcessarMensagemProgramadaUseCase(mensagens,enviar,
                org.mockito.Mockito.mock(com.synapse.crm.atendimento.application.origem.RegistroDeOrigemDaMensagem.class), Clock.fixed(agora,ZoneOffset.UTC),classificacao,auditoria);
        assertThatThrownBy(() -> caso.processar(programada.id())).isInstanceOf(com.synapse.crm.atendimento.application.ContaDeTesteSomenteLeituraException.class);
        org.mockito.Mockito.verifyNoInteractions(enviar);
        org.mockito.Mockito.verify(auditoria).executar(programada.atendenteId(),"MENSAGEM_PROGRAMADA");
        org.assertj.core.api.Assertions.assertThat(programada.status()).isEqualTo(StatusMensagemProgramada.AGENDADA);
    }
}
