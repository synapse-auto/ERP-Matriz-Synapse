package com.synapse.crm.automacaoconfig.application;

import org.springframework.stereotype.Service;

import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/** Resolve a logo atual sem cache, baixando a customizacao ou usando o classpath. */
@Service
public class ObterLogoDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final RecursosDeMarcaDaInstancia recursos;
    private final ArmazenamentoDeMidia armazenamento;

    public ObterLogoDaInstanciaUseCase(
            MarcaDaInstanciaRepositorio marcas,
            RecursosDeMarcaDaInstancia recursos,
            ArmazenamentoDeMidia armazenamento) {
        this.marcas = marcas;
        this.recursos = recursos;
        this.armazenamento = armazenamento;
    }

    public byte[] executar() {
        String referencia = marcas.obter().logoReferenciaStorage();
        return referencia == null ? recursos.logo() : armazenamento.baixar(referencia);
    }
}
