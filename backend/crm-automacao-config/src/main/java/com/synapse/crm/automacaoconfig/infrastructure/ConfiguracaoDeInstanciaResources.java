package com.synapse.crm.automacaoconfig.infrastructure;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Le {@code tema.json}, {@code textos.json} e {@code logo.png} do classpath (Nivel 3 da Base PAI,
 * docs/07) — arquivo de configuracao da instancia, deploy sem codigo. Cada filho substitui os
 * arquivos no proprio {@code src/main/resources}; o servidor so os expoe.
 *
 * <p>Lidos uma vez, na subida: sao arquivo de deploy, nao dado que muda em runtime.
 *
 * <p>{@code tema.json}/{@code textos.json} sao obrigatorios — se faltar algum, a aplicacao falha ao
 * subir; falhar alto aqui e melhor que servir tema vazio silenciosamente para todo cliente da
 * instancia. {@code logo.png} e o oposto: um filho sem marca propria e o caso normal (E31b), entao a
 * ausencia vira {@code null} aqui, nunca uma falha de boot.
 */
@Component
public class ConfiguracaoDeInstanciaResources {

    private final JsonNode tema;
    private final JsonNode textos;
    private final byte[] logo;

    ConfiguracaoDeInstanciaResources(ObjectMapper json) {
        this.tema = ler(json, "tema.json");
        this.textos = ler(json, "textos.json");
        this.logo = lerLogoSeExistir();
    }

    public JsonNode tema() {
        return tema;
    }

    public JsonNode textos() {
        return textos;
    }

    /** {@code null} quando o filho nao tem logo configurado — nunca lanca por isso. */
    public byte[] logo() {
        return logo;
    }

    private static JsonNode ler(ObjectMapper json, String arquivo) {
        try {
            return json.readTree(new ClassPathResource(arquivo).getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(
                    arquivo + " ausente ou ilegivel no classpath — a instancia nao pode subir sem ele.", e);
        }
    }

    private static byte[] lerLogoSeExistir() {
        ClassPathResource recurso = new ClassPathResource("logo.png");
        if (!recurso.exists()) {
            return null;
        }
        try (InputStream in = recurso.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("logo.png existe mas nao pode ser lido.", e);
        }
    }
}
