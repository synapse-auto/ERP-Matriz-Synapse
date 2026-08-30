package com.synapse.crm.app.config.avatar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

/**
 * Bucket separado dos anexos, com um prefixo por tipo de dono da foto.
 *
 * <p>Existe para que a chegada da foto de lead (E97) nao duplicasse a conversa com o MinIO. O que
 * muda entre um avatar de usuario e a foto de um lead e uma string — {@code avatar/} contra
 * {@code lead/} —, e {@link #buscar}/{@link #remover} filtram por ela justamente para que um
 * adaptador nao alcance objeto do outro se receber uma referencia trocada.
 *
 * <p>E um bean unico porque avatar de usuario e foto de lead compartilham o bucket. Assim, a
 * criacao preguiçosa do bucket tem um unico controle de concorrencia. O browser nunca recebe URL do
 * MinIO.
 */
@Component
class BucketDeAvatares {

    private final MinioClient io;
    private final String bucket;
    private final AtomicBoolean bucketGarantido = new AtomicBoolean();

    BucketDeAvatares(MidiaProperties midia) {
        io = MinioClient.builder()
                .endpoint(midia.endpoint())
                .credentials(midia.accessKey(), midia.secretKey())
                .build();
        bucket = midia.bucket() + "-avatares";
    }

    String salvar(String prefixo, byte[] conteudo, String mimetype) {
        garantirBucket();
        String referencia = prefixo + UUID.randomUUID() + ".png";
        try (InputStream fonte = new ByteArrayInputStream(conteudo)) {
            io.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(referencia)
                    .stream(fonte, conteudo.length, -1)
                    .contentType(mimetype)
                    .build());
            return referencia;
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao salvar foto no storage", e);
        }
    }

    Optional<byte[]> buscar(String prefixo, String referencia) {
        if (referencia == null || !referencia.startsWith(prefixo)) {
            return Optional.empty();
        }
        try (InputStream fonte = io.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(referencia)
                .build())) {
            return Optional.of(fonte.readAllBytes());
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    void remover(String prefixo, String referencia) {
        if (referencia == null || !referencia.startsWith(prefixo)) {
            return;
        }
        try {
            io.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(referencia).build());
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao remover foto do storage", e);
        }
    }

    private void garantirBucket() {
        if (!bucketGarantido.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!io.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                io.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao garantir bucket de avatares", e);
        }
    }
}
