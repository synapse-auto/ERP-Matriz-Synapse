package com.synapse.crm.atendimento.domain.mensagem;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** E233: o marcador de envio de template é opt-in; toda construção anterior continua "não template". */
class MensagemEnvioDeTemplateTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-19T12:00:00Z");
    private static final Remetente EQUIPE = Remetente.atendente(UUID.randomUUID());

    @Test
    @DisplayName("construtores e fábricas anteriores resultam em não template")
    void construcoesAnteriores_naoSaoTemplate() {
        assertThat(new Mensagem(ID, ATENDIMENTO, EQUIPE, TipoMensagem.TEXTO, "oi", null, null,
                        StatusEntrega.PENDENTE, AGORA)
                .envioDeTemplate()).isFalse();
        assertThat(new Mensagem(ID, ATENDIMENTO, EQUIPE, TipoMensagem.TEXTO, "oi", null, null,
                        StatusEntrega.PENDENTE, AGORA, null)
                .envioDeTemplate()).isFalse();
        assertThat(Mensagem.texto(ID, ATENDIMENTO, EQUIPE, "oi", AGORA).envioDeTemplate()).isFalse();
        assertThat(Mensagem.midia(ID, ATENDIMENTO, EQUIPE, TipoMensagem.IMAGEM, "chave/a.png", "{}", AGORA)
                .envioDeTemplate()).isFalse();
        assertThat(Mensagem.estruturada(ID, ATENDIMENTO, EQUIPE, TipoMensagem.LOCALIZACAO, "{}", AGORA)
                .envioDeTemplate()).isFalse();
        assertThat(Mensagem.interativa(ID, ATENDIMENTO, EQUIPE, TipoMensagem.BOTOES, "escolha", "[]", AGORA)
                .envioDeTemplate()).isFalse();
    }

    @Test
    @DisplayName("o marcador informado na construção é preservado")
    void marcadorInformado_ePreservado() {
        var template = new Mensagem(ID, ATENDIMENTO, EQUIPE, TipoMensagem.TEXTO, "Olá, Maria", null, null,
                StatusEntrega.PENDENTE, AGORA, null, true);

        assertThat(template.envioDeTemplate()).isTrue();
        assertThat(template.tipo()).isEqualTo(TipoMensagem.TEXTO);
        assertThat(template.conteudo()).isEqualTo("Olá, Maria");
    }
}
