package com.synapse.crm.campanhas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.campanhas.application.AplicarEntregaDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.RegistrarRespostaDeCampanhaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Os ouvintes rodam em AFTER_COMMIT, com a conexao da transacao original ainda presa. Se chamassem o caso de uso na
 * propria thread, cada evento seguraria duas conexoes do pool do chat e, com carga, as threads se travariam ate o
 * timeout (visto na CI: pool de 2 conexoes, active=2). A protecao e so enfileirar e voltar.
 */
class OuvintesDeCampanhaTest {

    private final List<Runnable> fila = new ArrayList<>();
    private final Executor executorQueSoGuarda = fila::add;

    @BeforeEach
    void instalarAutoridadeDeTeste() {
        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
    }

    @Test
    @DisplayName("mudanca de status so enfileira: o caso de uso nao roda na thread do commit")
    void entrega_soEnfileira() {
        AplicarEntregaDeCampanhaUseCase aplicar = mock(AplicarEntregaDeCampanhaUseCase.class);
        OuvinteDeEntregaDeCampanha ouvinte = new OuvinteDeEntregaDeCampanha(aplicar, executorQueSoGuarda);
        UUID mensagem = UUID.randomUUID();
        Instant quando = Instant.parse("2026-10-05T12:00:00Z");

        ouvinte.aoMudarStatusDeEntrega(
                new MudancaDeStatusDeEntrega(mensagem, UUID.randomUUID(), UUID.randomUUID(), "ENVIADO", quando));

        assertThat(fila).hasSize(1);
        verify(aplicar, never()).executar(any(UUID.class), any(String.class), any(Instant.class));

        fila.get(0).run();

        verify(aplicar).executar(mensagem, "ENVIADO", quando);
    }

    @Test
    @DisplayName("falha do caso de uso e engolida com alarme: nunca chega ao caminho de mensagens")
    void entrega_falhaNaoPropaga() {
        AplicarEntregaDeCampanhaUseCase aplicar = mock(AplicarEntregaDeCampanhaUseCase.class);
        doThrow(new IllegalStateException("banco fora"))
                .when(aplicar)
                .executar(any(UUID.class), any(String.class), any(Instant.class));
        OuvinteDeEntregaDeCampanha ouvinte = new OuvinteDeEntregaDeCampanha(aplicar, executorQueSoGuarda);

        ouvinte.aoMudarStatusDeEntrega(new MudancaDeStatusDeEntrega(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "FALHOU", Instant.now()));

        fila.get(0).run();
    }

    @Test
    @DisplayName("mensagem recebida so enfileira: a resposta do cliente nunca espera por campanha")
    void resposta_soEnfileira() {
        RegistrarRespostaDeCampanhaUseCase registrar = mock(RegistrarRespostaDeCampanhaUseCase.class);
        OuvinteDeRespostaDeCampanha ouvinte = new OuvinteDeRespostaDeCampanha(registrar, executorQueSoGuarda);
        UUID lead = UUID.randomUUID();
        Instant quando = Instant.parse("2026-10-05T12:00:00Z");

        ouvinte.aoReceberMensagem(
                new EventoDeAtendimento.MensagemRecebida(lead, UUID.randomUUID(), UUID.randomUUID(), false, quando));

        assertThat(fila).hasSize(1);
        verify(registrar, never()).executar(any(UUID.class), any(Instant.class));

        fila.get(0).run();

        verify(registrar).executar(lead, quando);
    }
}
