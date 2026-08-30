package com.synapse.crm.core.application.lead.foto;

/**
 * Valida e reprocessa a imagem recebida sem transportar o formato original para o storage.
 *
 * <p>Porta propria de crm-core, gemea da {@code ProcessadorDeAvatar} de crm-equipe. A interface e
 * duplicada de proposito: crm-core nao pode depender de crm-equipe. O que <b>nao</b> se duplica e a
 * implementacao — o adaptador em crm-app delega ao mesmo {@code ProcessadorDeAvatarImagem} que ja
 * valida magic bytes, corta no centro e reencoda em PNG.
 */
public interface ProcessadorDeFotoDeLead {

    Resultado processar(byte[] original);

    record Resultado(byte[] conteudo, String mimetype) {}
}
