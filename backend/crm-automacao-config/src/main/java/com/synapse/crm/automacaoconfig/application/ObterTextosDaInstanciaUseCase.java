package com.synapse.crm.automacaoconfig.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/** Resolve o catalogo do classpath e sobrepoe apenas a identidade customizada da instancia. */
@Service
public class ObterTextosDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final RecursosDeMarcaDaInstancia recursos;

    public ObterTextosDaInstanciaUseCase(MarcaDaInstanciaRepositorio marcas, RecursosDeMarcaDaInstancia recursos) {
        this.marcas = marcas;
        this.recursos = recursos;
    }

    public JsonNode executar() {
        // O recurso e cacheado no boot. Copiar antes de sobrescrever evita contaminar as proximas
        // requisicoes (inclusive as de uma instancia sem customizacao).
        JsonNode copia = recursos.textos().deepCopy();
        var marca = marcas.obter();
        JsonNode appNode = copia.path("app");
        if (!(appNode instanceof ObjectNode app)) {
            return copia;
        }
        if (marca.nomeDaMarca() != null) {
            app.put("marca", marca.nomeDaMarca());
        }
        if (marca.subtitulo() != null) {
            app.put("subtitulo", marca.subtitulo());
        }
        return copia;
    }
}
