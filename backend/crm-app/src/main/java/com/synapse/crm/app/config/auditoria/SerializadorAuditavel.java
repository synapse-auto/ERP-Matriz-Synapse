package com.synapse.crm.app.config.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializa uma entidade para {@code dados_antes}/{@code dados_depois} de {@code audit_log},
 * filtrando por allowlist de campos.
 *
 * <p><b>Allowlist, nunca blocklist.</b> Uma blocklist esquece o campo novo que alguem acrescentar
 * amanha — {@code CanalCredencial} ganha um {@code token_ref}, {@code Usuario} tem hash de senha, e
 * ninguem lembra de adicionar o campo novo na lista de exclusao ate um segredo aparecer em
 * {@code audit_log}, que ninguem trata como dado sensivel. Com allowlist, um campo novo no record so
 * aparece na auditoria se alguem decidir explicitamente que ele pode.
 *
 * <p>Entidade sem allowlist declarada falha fechado: {@link #paraJson} devolve {@code null} em vez de
 * serializar todos os campos por padrao. E o mesmo principio de "falhar fechado" que a politica RLS
 * do projeto ja aplica a leitura — aqui protege escrita de auditoria.
 */
@Component
public class SerializadorAuditavel {

    /**
     * Campos permitidos por {@code entidadeTipo}. Adicione uma entrada aqui ao criar um novo caso de
     * uso {@code @Auditable} sobre uma entidade nova — e revise a lista de campos com cuidado: e
     * exatamente aqui que um segredo deixa de vazar.
     */
    private static final Map<String, Set<String>> ALLOWLIST = Map.of(
            "TAG", Set.of("id", "nome", "cor", "icone"));

    private final ObjectMapper mapper;

    public SerializadorAuditavel(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String paraJson(String entidadeTipo, Object objeto) {
        if (objeto == null) {
            return null;
        }
        Set<String> permitidos = ALLOWLIST.get(entidadeTipo);
        if (permitidos == null || permitidos.isEmpty()) {
            return null;
        }

        Map<String, Object> campos = mapper.convertValue(objeto, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> filtrados = new LinkedHashMap<>();
        for (String campo : permitidos) {
            if (campos.containsKey(campo)) {
                filtrados.put(campo, campos.get(campo));
            }
        }

        try {
            return mapper.writeValueAsString(filtrados);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
