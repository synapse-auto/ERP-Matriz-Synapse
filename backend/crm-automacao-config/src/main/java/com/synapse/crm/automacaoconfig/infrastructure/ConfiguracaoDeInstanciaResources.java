package com.synapse.crm.automacaoconfig.infrastructure;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Le {@code tema.json} e {@code textos.json} do classpath (Nivel 3 da Base PAI, docs/07) — arquivo de
 * configuracao da instancia, deploy sem codigo. Cada filho substitui os dois arquivos no proprio
 * {@code src/main/resources}; o servidor so os expoe.
 *
 * <p>Lidos uma vez, na subida: sao arquivo de deploy, nao dado que muda em runtime. Se faltar algum,
 * a aplicacao falha ao subir — falhar alto aqui e melhor que servir tema vazio silenciosamente para
 * todo cliente da instancia.
 */
@Component
public class ConfiguracaoDeInstanciaResources {

    private final JsonNode tema;
    private final JsonNode textos;

    ConfiguracaoDeInstanciaResources(ObjectMapper json) {
        this.tema = ler(json, "tema.json");
        this.textos = ler(json, "textos.json");
    }

    public JsonNode tema() {
        return tema;
    }

    public JsonNode textos() {
        return textos;
    }

    private static JsonNode ler(ObjectMapper json, String arquivo) {
        try {
            return json.readTree(new ClassPathResource(arquivo).getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(
                    arquivo + " ausente ou ilegivel no classpath — a instancia nao pode subir sem ele.", e);
        }
    }
}
