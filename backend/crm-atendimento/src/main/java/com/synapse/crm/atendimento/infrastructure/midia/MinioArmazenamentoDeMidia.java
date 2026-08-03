package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;

/**
 * Adaptador S3-compativel (MinIO em desenvolvimento, S3 ou equivalente em producao — docs/01,
 * docs/09). So esta classe fala com o storage; o resto do sistema conhece {@link
 * ArmazenamentoDeMidia}.
 *
 * <p>Dois clientes deliberadamente: {@code io} fala com {@link MidiaProperties#endpoint()} (upload
 * e download reais, sempre do backend); {@code assinador} fala com {@link
 * MidiaProperties#urlPublica()} (so para {@code getPresignedObjectUrl} — em dev os dois hosts
 * divergem, ver o Javadoc de {@link MidiaProperties}).
 */
@Component
class MinioArmazenamentoDeMidia implements ArmazenamentoDeMidia {

    private final MinioClient io;
    private final MinioClient assinador;
    private final MidiaProperties propriedades;
    private final java.util.concurrent.atomic.AtomicBoolean bucketGarantido =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    MinioArmazenamentoDeMidia(MidiaProperties propriedades) {
        this.propriedades = propriedades;
        this.io = MinioClient.builder()
                .endpoint(propriedades.endpoint())
                .credentials(propriedades.accessKey(), propriedades.secretKey())
                .build();
        this.assinador = propriedades.urlPublica().equals(propriedades.endpoint())
                ? this.io
                : MinioClient.builder()
                        .endpoint(propriedades.urlPublica())
                        .credentials(propriedades.accessKey(), propriedades.secretKey())
                        .build();
        // Preguicoso de proposito: o storage de midia nao pode ser um motivo para a
        // aplicacao inteira falhar ao subir (regra de precedencia do CLAUDE.md — a aba
        // Atendimentos nao pode ficar indisponivel). O primeiro upload real e que garante
        // o bucket, nao o boot.
    }

    /** Idempotente: evita depender de um servico de init separado no compose. */
    private void garantirBucket() {
        if (!bucketGarantido.compareAndSet(false, true)) {
            return;
        }
        try {
            boolean existe =
                    io.bucketExists(BucketExistsArgs.builder().bucket(propriedades.bucket()).build());
            if (!existe) {
                io.makeBucket(MakeBucketArgs.builder().bucket(propriedades.bucket()).build());
            }
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException(
                    "falha ao garantir o bucket " + propriedades.bucket() + " no storage de midia", e);
        }
    }

    @Override
    public String salvar(byte[] conteudo, String nomeArquivoSanitizado, String mimetype) {
        garantirBucket();
        // Chave gerada, nunca o nome do cliente: evita colisao, path traversal e vazar nome de
        // arquivo de um cliente para outro no path do objeto.
        String extensao = extensaoDe(nomeArquivoSanitizado);
        String chave = "midia/" + UUID.randomUUID() + extensao;
        try (InputStream fonte = new ByteArrayInputStream(conteudo)) {
            io.putObject(PutObjectArgs.builder()
                    .bucket(propriedades.bucket())
                    .object(chave)
                    .stream(fonte, conteudo.length, -1)
                    .contentType(mimetype)
                    .build());
            return chave;
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao salvar midia no storage: " + chave, e);
        }
    }

    @Override
    public byte[] baixar(String referencia) {
        try (InputStream conteudo = io.getObject(GetObjectArgs.builder()
                .bucket(propriedades.bucket())
                .object(referencia)
                .build())) {
            return conteudo.readAllBytes();
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao baixar midia do storage: " + referencia, e);
        }
    }

    @Override
    public String urlAssinada(String referencia, Duration validade) {
        try {
            return assinador.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(propriedades.bucket())
                    .object(referencia)
                    .expiry((int) validade.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao assinar URL de midia: " + referencia, e);
        }
    }

    private static String extensaoDe(String nomeArquivo) {
        if (nomeArquivo == null) {
            return "";
        }
        int ponto = nomeArquivo.lastIndexOf('.');
        return ponto < 0 ? "" : nomeArquivo.substring(ponto);
    }
}
