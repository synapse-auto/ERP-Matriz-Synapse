package com.synapse.crm.app.avatar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.lead.foto.ArmazenamentoDeFotoDeLead;

/** Storage em memoria para provar o contrato de foto sem depender de um MinIO no CI. */
@Component
@Primary
public class ArmazenamentoDeFotoDeLeadFake implements ArmazenamentoDeFotoDeLead {

    private final Map<String, Arquivo> objetos = new LinkedHashMap<>();
    private int salvamentos;
    private int remocoes;

    @Override
    public synchronized String salvar(byte[] conteudo, String mimetype) {
        String referencia = "lead/" + UUID.randomUUID() + ".png";
        objetos.put(referencia, new Arquivo(conteudo.clone(), mimetype));
        salvamentos++;
        return referencia;
    }

    @Override
    public synchronized Optional<Arquivo> buscar(String referencia) {
        return Optional.ofNullable(objetos.get(referencia));
    }

    @Override
    public synchronized void remover(String referencia) {
        if (objetos.remove(referencia) != null) {
            remocoes++;
        }
    }

    public synchronized void limpar() {
        objetos.clear();
        salvamentos = 0;
        remocoes = 0;
    }

    public synchronized int salvamentos() {
        return salvamentos;
    }

    public synchronized int remocoes() {
        return remocoes;
    }

    public synchronized Arquivo unicoArquivo() {
        if (objetos.size() != 1) {
            throw new IllegalStateException("esperava exatamente uma foto, mas havia " + objetos.size());
        }
        return objetos.values().iterator().next();
    }
}
