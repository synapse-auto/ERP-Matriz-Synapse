package com.synapse.crm.automacaoconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.automacaoconfig.application.festivas.GerenciarMensagensFestivasUseCase;
import com.synapse.crm.automacaoconfig.application.festivas.MensagemFestivaRepositorio;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestivaInvalidaException;

class GerenciarMensagensFestivasUseCaseTest {
    private final MensagemFestivaRepositorio repositorio = mock(MensagemFestivaRepositorio.class);
    private final GerenciarMensagensFestivasUseCase caso = new GerenciarMensagensFestivasUseCase(repositorio);

    @Test
    void criaRegistroComTituloIconeDataEMensagem() {
        UUID id = UUID.randomUUID();
        when(repositorio.salvar(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        MensagemFestiva criada = caso.criar("Data customizada", "★", LocalDate.of(2026, 12, 25), "Mensagem da data, [nome]!", false);

        assertThat(criada).extracting(MensagemFestiva::titulo, MensagemFestiva::icone, MensagemFestiva::data, MensagemFestiva::mensagem)
                .containsExactly("Data customizada", "★", LocalDate.of(2026, 12, 25), "Mensagem da data, [nome]!");
        assertThat(criada.id()).isNotNull();
    }

    @Test
    void rejeitaDataOuTextoAusenteAntesDoRepositorio() {
        assertThatThrownBy(() -> caso.criar("", "🎉", LocalDate.now(), "Mensagem", true))
                .isInstanceOf(MensagemFestivaInvalidaException.class);
        assertThatThrownBy(() -> caso.criar("Data", "🎉", null, "Mensagem", true))
                .isInstanceOf(MensagemFestivaInvalidaException.class);
        verify(repositorio, org.mockito.Mockito.never()).salvar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alternaSemPerderOsDadosDaData() {
        UUID id = UUID.randomUUID();
        MensagemFestiva atual = new MensagemFestiva(id, "Data customizada", "★", LocalDate.of(2026, 12, 25), "Mensagem da data", false);
        when(repositorio.porId(id)).thenReturn(Optional.of(atual));
        when(repositorio.salvar(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        MensagemFestiva alterada = caso.alternar(id, true);

        assertThat(alterada.ativo()).isTrue();
        assertThat(alterada.titulo()).isEqualTo("Data customizada");
        assertThat(alterada.data()).isEqualTo(atual.data());
    }
}
