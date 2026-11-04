package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.synapse.crm.core.domain.lead.TelefoneCanonico;

class RestricaoDeNumerosWhatsAppTest {
    private static final String LISTA = "555491082498,556196791022,556198430401,558398779442";

    @Test
    void aceitaSomenteNumerosCompletosDaLista() {
        var regra = ativa(LISTA);
        assertThat(regra.permite("555491082498")).isTrue();
        assertThat(regra.permite("5554991082498")).isTrue();
        assertThat(regra.permite("556196791022")).isTrue();
        assertThat(regra.permite("556198430401")).isTrue();
        assertThat(regra.permite("558398779442")).isTrue();
        assertThat(regra.permite("445491082498")).isFalse();
    }

    @Test
    void listaVaziaOuComQualquerItemInvalidoFalhaFechada() {
        assertThat(ativa("").permite("555491082498")).isFalse();
        assertThat(ativa(LISTA + ",qualquer").permite("555491082498")).isFalse();
        assertThat(ativa("555491082498,").permite("555491082498")).isFalse();
    }

    @Test
    void desligadaPreservaTodosOsDestinosEListaNaoAceitaJidNemGrupo() {
        var desligada = new RestricaoDeNumerosWhatsApp(false, "", new TelefoneCanonico("55"));
        assertThat(desligada.permite("5511999999999")).isTrue();
        var regra = ativa(LISTA);
        assertThat(regra.permite("555491082498@s.whatsapp.net")).isFalse();
        assertThat(regra.permite("555491082498@g.us")).isFalse();
        assertThat(regra.permite(null)).isFalse();
    }

    private static RestricaoDeNumerosWhatsApp ativa(String numeros) {
        return new RestricaoDeNumerosWhatsApp(true, numeros, new TelefoneCanonico("55"));
    }
}
