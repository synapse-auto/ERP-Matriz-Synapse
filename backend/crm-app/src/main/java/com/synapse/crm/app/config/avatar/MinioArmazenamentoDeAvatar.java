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
import com.synapse.crm.equipe.application.usuario.ArmazenamentoDeAvatar;

/** Bucket separado de avatares; o browser nunca recebe URL do MinIO. */
@Component
class MinioArmazenamentoDeAvatar implements ArmazenamentoDeAvatar {

    private final MinioClient io;
    private final String bucket;
    private final AtomicBoolean bucketGarantido = new AtomicBoolean();

    MinioArmazenamentoDeAvatar(MidiaProperties midia) {
        io = MinioClient.builder()
                .endpoint(midia.endpoint())
                .credentials(midia.accessKey(), midia.secretKey())
                .build();
        bucket = midia.bucket() + "-avatares";
    }

    @Override
    public String salvar(byte[] conteudo, String mimetype) {
        garantirBucket();
        String referencia = "avatar/" + UUID.randomUUID() + ".png";
        try (InputStream fonte = new ByteArrayInputStream(conteudo)) {
            io.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(referencia)
                    .stream(fonte, conteudo.length, -1)
                    .contentType(mimetype)
                    .build());
            return referencia;
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao salvar avatar no storage", e);
        }
    }

    @Override
    public Optional<Arquivo> buscar(String referencia) {
        if (referencia == null || !referencia.startsWith("avatar/")) {
            return Optional.empty();
        }
        try (InputStream fonte = io.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(referencia)
                .build())) {
            return Optional.of(new Arquivo(fonte.readAllBytes(), "image/png"));
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    @Override
    public void remover(String referencia) {
        if (referencia == null || !referencia.startsWith("avatar/")) {
            return;
        }
        try {
            io.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(referencia).build());
        } catch (MinioException | IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao remover avatar do storage", e);
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
