package com.synapse.crm.app.avatar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.chat.ArmazenamentoDeFotoDeGrupo;

/** Storage de foto de grupo em memoria: prova o contrato sem MinIO e permite simular falha. */
@Component
@Primary
public class ArmazenamentoDeFotoDeGrupoFake implements ArmazenamentoDeFotoDeGrupo {

    private final Map<String, Arquivo> objetos = new LinkedHashMap<>();
    private boolean falharAoSalvar;
    private int salvamentos;
    private int remocoes;

    @Override
    public synchronized String salvar(byte[] conteudo, String mimetype) {
        if (falharAoSalvar) {
            throw new IllegalStateException("storage indisponivel (simulado)");
        }
        String referencia = "grupo/" + UUID.randomUUID() + ".png";
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
        falharAoSalvar = false;
        salvamentos = 0;
        remocoes = 0;
    }

    public synchronized void falharAoSalvar(boolean falhar) {
        this.falharAoSalvar = falhar;
    }

    public synchronized int quantidade() {
        return objetos.size();
    }

    public synchronized int salvamentos() {
        return salvamentos;
    }

    public synchronized int remocoes() {
        return remocoes;
    }

    public synchronized boolean existe(String referencia) {
        return objetos.containsKey(referencia);
    }

    public synchronized Arquivo unicoArquivo() {
        if (objetos.size() != 1) {
            throw new IllegalStateException("esperava exatamente uma foto, mas havia " + objetos.size());
        }
        return objetos.values().iterator().next();
    }
}
