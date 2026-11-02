package com.synapse.crm.core.infrastructure.darwin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Cancela o download antes de acumular resposta externa sem limite na memória. */
final class CorpoHttpLimitado implements HttpResponse.BodySubscriber<byte[]> {
    private final int limite;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> resultado = new CompletableFuture<>();
    private Flow.Subscription assinatura;
    CorpoHttpLimitado(int limite) { this.limite = limite; }
    @Override public CompletionStage<byte[]> getBody() { return resultado; }
    @Override public void onSubscribe(Flow.Subscription assinatura) { this.assinatura = assinatura; assinatura.request(1); }
    @Override public void onNext(List<ByteBuffer> blocos) {
        for (var bloco : blocos) {
            if (bloco.remaining() > limite - bytes.size()) {
                assinatura.cancel(); resultado.completeExceptionally(new IOException("Resposta externa excedeu limite")); return;
            }
            byte[] parte = new byte[bloco.remaining()]; bloco.get(parte); bytes.writeBytes(parte);
        }
        assinatura.request(1);
    }
    @Override public void onError(Throwable erro) { resultado.completeExceptionally(new IOException("Resposta externa interrompida")); }
    @Override public void onComplete() { resultado.complete(bytes.toByteArray()); }
}
