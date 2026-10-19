package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/**
 * E233: o evento de tempo real carrega a mensagem inteira (não só ids), então leva também o marcador
 * de template. Sem isso a tela só o veria depois de reler a timeline.
 */
class RelayDoMarcadorDeTemplateTest {

    private static final Instant QUANDO = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    @DisplayName("evento de template publica template=true no STOMP")
    void eventoDeTemplate_publicaTemplateVerdadeiro() throws Exception {
        JsonNode dados = publicar(true);

        assertThat(dados.path("template").isBoolean()).isTrue();
        assertThat(dados.path("template").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("evento comum publica template=false, sempre presente")
    void eventoComum_publicaTemplateFalso() throws Exception {
        JsonNode dados = publicar(false);

        assertThat(dados.path("template").isBoolean()).isTrue();
        assertThat(dados.path("template").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("construtores anteriores do evento seguem valendo e resultam em não template")
    void construtoresAnteriores_naoSaoTemplate() throws Exception {
        UUID atendimento = UUID.randomUUID();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(
                redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
                new MidiaProperties(null, null, null, null, null, null));

        listener.aoReceberMensagem(new MensagemParaTempoReal(
                atendimento, UUID.randomUUID(), UUID.randomUUID(), "LEAD", null, "TEXTO", "oi", null, null, null,
                "ENVIADO", QUANDO));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        assertThat(new ObjectMapper().readTree(payload.getValue()).path("dados").path("template").asBoolean())
                .isFalse();
    }

    private static JsonNode publicar(boolean envioDeTemplate) throws Exception {
        UUID atendimento = UUID.randomUUID();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(
                redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
                new MidiaProperties(null, null, null, null, null, null));

        listener.aoReceberMensagem(new MensagemParaTempoReal(
                atendimento, UUID.randomUUID(), UUID.randomUUID(), "ATENDENTE", UUID.randomUUID(), "TEXTO",
                "Olá Maria", null, null, null, "PENDENTE", QUANDO, null, "chave-1", "Lead", List.of(),
                envioDeTemplate));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        return new ObjectMapper().readTree(payload.getValue()).path("dados");
    }
}
