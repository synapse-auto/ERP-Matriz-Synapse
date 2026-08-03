package com.synapse.crm.app.midia;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;

/**
 * Storage em memoria para teste — mesmo espirito de {@code CanalFake}: implementa a porta e entra
 * pelo mesmo caminho que o MinIO real entraria, sem exigir um MinIO de verdade rodando no CI.
 *
 * <p>{@code @Primary} porque {@code MinioArmazenamentoDeMidia} tambem e {@code @Component} e
 * continua no classpath do teste (nao ha selecao por configuracao para este porto, diferente do
 * canal). Sem {@code @Primary}, a injecao sem qualificador falharia por ambiguidade.
 */
@Component
@Primary
public class ArmazenamentoDeMidiaFake implements ArmazenamentoDeMidia {

    private record Objeto(byte[] conteudo, String mimetype) {}

    private record Assinatura(String referencia, Instant expiraEm) {}

    private final Map<String, Objeto> objetos = new ConcurrentHashMap<>();
    private final Map<String, Assinatura> assinaturas = new ConcurrentHashMap<>();

    @Override
    public String salvar(byte[] conteudo, String nomeArquivoSanitizado, String mimetype) {
        String referencia = "fake/" + UUID.randomUUID() + "/" + nomeArquivoSanitizado;
        objetos.put(referencia, new Objeto(conteudo, mimetype));
        return referencia;
    }

    @Override
    public byte[] baixar(String referencia) {
        Objeto objeto = objetos.get(referencia);
        if (objeto == null) {
            throw new IllegalStateException("objeto fake nao encontrado: " + referencia);
        }
        return objeto.conteudo();
    }

    @Override
    public String urlAssinada(String referencia, Duration validade) {
        String token = UUID.randomUUID().toString();
        assinaturas.put(token, new Assinatura(referencia, Instant.now().plus(validade)));
        return "https://fake-storage.local/" + referencia + "?token=" + token;
    }

    /**
     * O que um {@code GET} nessa URL faria: bytes se o token existe e ainda nao expirou, vazio caso
     * contrario — simula tanto a expiracao quanto um token que nunca existiu.
     */
    public Optional<byte[]> baixarPelaUrlAssinada(String urlAssinada) {
        String token = urlAssinada.substring(urlAssinada.indexOf("token=") + "token=".length());
        Assinatura assinatura = assinaturas.get(token);
        if (assinatura == null || Instant.now().isAfter(assinatura.expiraEm())) {
            return Optional.empty();
        }
        return Optional.ofNullable(objetos.get(assinatura.referencia())).map(Objeto::conteudo);
    }

    public void limpar() {
        objetos.clear();
        assinaturas.clear();
    }

    public int contagemDeObjetos() {
        return objetos.size();
    }
}
