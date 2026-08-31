package com.synapse.crm.atendimento.infrastructure.midia;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do storage de midia da instancia (E11b).
 *
 * <p>{@code endpoint} e {@code urlPublica} sao deliberadamente separados: em desenvolvimento o
 * backend fala com o MinIO pelo hostname interno do Docker ({@code http://minio:9000}), mas o
 * browser do atendente so alcanca {@code http://localhost:9000}. O backend usa {@code endpoint}
 * para upload/download reais; {@code urlPublica} e so o host embutido na URL assinada que sai para
 * a tela. Em producao, atras de um dominio unico, os dois valores costumam ser iguais.
 *
 * @param bucket nome do bucket desta instancia — um por cliente, nunca compartilhado
 * @param accessKey / secretKey credenciais do storage; sem default, mesmo padrao de
 *     {@code CanalProperties.webhookSecret}
 * @param expiracaoLeitura validade da URL assinada. Default 1 hora — o mesmo do chat interno.
 *     Cinco minutos quebrava a bolha de conversa aberta sem ganho de seguranca (ver
 *     {@code application.yml}).
 */
@ConfigurationProperties("synapse.midia")
public record MidiaProperties(
        String endpoint, String urlPublica, String bucket, String accessKey, String secretKey,
        Duration expiracaoLeitura) {

    public MidiaProperties {
        endpoint = (endpoint == null || endpoint.isBlank()) ? "http://localhost:9000" : endpoint;
        urlPublica = (urlPublica == null || urlPublica.isBlank()) ? endpoint : urlPublica;
        bucket = (bucket == null || bucket.isBlank()) ? "synapse-crm-midia" : bucket;
        expiracaoLeitura = expiracaoLeitura == null ? Duration.ofHours(1) : expiracaoLeitura;
    }
}
