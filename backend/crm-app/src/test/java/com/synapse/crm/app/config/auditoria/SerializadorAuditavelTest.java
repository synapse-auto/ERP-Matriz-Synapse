package com.synapse.crm.app.config.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SerializadorAuditavelTest {

    private final SerializadorAuditavel serializador = new SerializadorAuditavel(new ObjectMapper());

    /** Simula uma entidade cujo record ganhou um campo sensivel — exatamente o cenario que a
     * allowlist (em vez de blocklist) protege: ninguem precisou lembrar de adicionar
     * {@code tokenSecreto} a uma lista de exclusao. */
    private record TagComCampoSensivelDeTeste(UUID id, String nome, String cor, String icone, String tokenSecreto) {}

    private record QualquerEntidade(String campo) {}

    @Test
    @DisplayName("campo fora da allowlist nunca aparece no JSON, mesmo estando no objeto")
    void paraJson_campoForaDaAllowlist_nuncaAparece() {
        TagComCampoSensivelDeTeste comSegredo = new TagComCampoSensivelDeTeste(
                UUID.randomUUID(), "promo", "#fff", "star", "segredo-nao-pode-vazar");

        String json = serializador.paraJson("TAG", comSegredo);

        assertThat(json)
                .isNotNull()
                .contains("\"nome\":\"promo\"")
                .doesNotContain("tokenSecreto")
                .doesNotContain("segredo-nao-pode-vazar");
    }

    @Test
    @DisplayName("entidade sem allowlist declarada falha fechado: nada e gravado")
    void paraJson_semAllowlistDeclarada_devolveNull() {
        String json = serializador.paraJson("CANAL_CREDENCIAL", new QualquerEntidade("valor"));

        assertThat(json).isNull();
    }

    @Test
    @DisplayName("objeto nulo devolve nulo, sem lancar")
    void paraJson_objetoNulo_devolveNull() {
        assertThat(serializador.paraJson("TAG", null)).isNull();
    }
}
