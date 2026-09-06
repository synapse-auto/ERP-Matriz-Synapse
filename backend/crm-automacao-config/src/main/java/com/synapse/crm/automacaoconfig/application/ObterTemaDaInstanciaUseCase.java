package com.synapse.crm.automacaoconfig.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;


/** Resolve o tema atual sem cache: customizacao no banco, ou o recurso de deploy como fallback. */
@Service
public class ObterTemaDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final RecursosDeMarcaDaInstancia recursos;
    private final ObjectMapper json;

    public ObterTemaDaInstanciaUseCase(
            MarcaDaInstanciaRepositorio marcas,
            RecursosDeMarcaDaInstancia recursos,
            ObjectMapper json) {
        this.marcas = marcas;
        this.recursos = recursos;
        this.json = json;
    }

    public JsonNode executar() {
        String temaPersistido = marcas.obter().temaJson();
        if (temaPersistido == null) {
            return recursos.tema();
        }
        try {
            return json.readTree(temaPersistido);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("tema da instancia persistido nao e um JSON valido", erro);
        }
    }
}
