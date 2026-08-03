package com.synapse.crm.atendimento.domain.canal;

import java.time.Instant;
import java.util.Optional;

/**
 * A metade de entrada do ACL: transforma o que o provedor manda no que o CRM entende.
 *
 * <p>Recebe {@code String} crua de proposito. O dominio nao pode conhecer nem o formato da Meta nem
 * uma biblioteca de JSON, entao o que atravessa a fronteira e texto — e o que sai e {@link
 * MensagemRecebidaDoCanal}, vocabulario do CRM. Quando o provedor mudar o formato do webhook, o diff
 * fica na implementacao.
 *
 * <p>A validacao de assinatura vive aqui pelo mesmo motivo do resto: cada provedor assina de um
 * jeito. A Meta usa HMAC-SHA256 do corpo no cabecalho {@code X-Hub-Signature-256}; outro provedor usa
 * um token no cabecalho, outro nao assina nada.
 */
public interface TradutorDeCanal {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. */
    String provedor();

    /**
     * A requisicao veio mesmo do provedor?
     *
     * <p>Verificado <b>antes</b> de qualquer processamento e antes de gravar qualquer coisa: a rota
     * do webhook e publica, entao qualquer um na internet consegue chamar. Sem esta checagem, injetar
     * mensagem falsa na conversa de um cliente e um {@code curl}.
     */
    boolean assinaturaValida(String payloadCru, String assinaturaRecebida);

    /**
     * O id da mensagem no provedor — a chave de idempotencia.
     *
     * <p>Vazio quando o payload nao e uma mensagem (confirmacao de entrega, evento de status,
     * heartbeat). Esses sao respondidos com 200 e ignorados: reclamar deles faria o provedor
     * reentregar para sempre.
     */
    Optional<String> idExterno(String payloadCru);

    /** Traduz. Vazio quando o payload nao carrega mensagem de cliente. */
    Optional<MensagemRecebidaDoCanal> traduzir(String payloadCru);

    /**
     * Uma mensagem de cliente, ja em vocabulario do CRM.
     *
     * @param telefoneRemetente e por ele que se acha (ou se cria) o lead — o provedor nao conhece
     *     nosso id
     * @param nomeExibicao como o cliente aparece no WhatsApp; serve para nomear um lead novo
     * @param texto {@code null} quando a mensagem e midia
     * @param tipo {@code "TEXTO"}, {@code "IMAGEM"}, {@code "AUDIO"} ou {@code "DOCUMENTO"} — nome
     *     de {@code TipoMensagem} como String, e nao o enum em si, para o dominio de canal nao
     *     depender do de mensagem so por causa disto; quem converte e
     *     {@code ProcessadorDeWebhookEntradaOperacoes}
     * @param midiaIdExterno id da midia no provedor — chave para {@link
     *     CanalGateway#baixarMidiaRecebida}; {@code null} quando {@code tipo} e {@code "TEXTO"}
     * @param mimetype mimetype declarado pelo provedor; a instancia continua verificando por magic
     *     bytes depois de baixar, este e so o valor que veio no webhook
     * @param nomeArquivo so preenchido para documento
     * @param legenda caption enviada junto com a midia, se houver
     */
    record MensagemRecebidaDoCanal(
            String idExterno,
            String telefoneRemetente,
            String nomeExibicao,
            String texto,
            String tipo,
            String midiaIdExterno,
            String mimetype,
            String nomeArquivo,
            String legenda,
            Instant enviadoEm) {

        /** Atalho para o caso comum: mensagem de texto, sem nenhum campo de midia. */
        public static MensagemRecebidaDoCanal texto(
                String idExterno, String telefoneRemetente, String nomeExibicao, String texto,
                Instant enviadoEm) {
            return new MensagemRecebidaDoCanal(
                    idExterno, telefoneRemetente, nomeExibicao, texto, "TEXTO", null, null, null, null,
                    enviadoEm);
        }

        public boolean ehMidia() {
            return !"TEXTO".equals(tipo);
        }
    }
}
