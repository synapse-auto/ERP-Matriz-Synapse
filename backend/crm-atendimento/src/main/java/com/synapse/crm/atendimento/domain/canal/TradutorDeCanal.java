package com.synapse.crm.atendimento.domain.canal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A metade de entrada do ACL: transforma o que o provedor manda no que o CRM entende.
 *
 * <p>Recebe {@code String} crua de proposito. O dominio nao pode conhecer nem o formato da Meta nem
 * uma biblioteca de JSON, entao o que atravessa a fronteira e texto — e o que sai e {@link
 * MensagemRecebidaDoCanal}, vocabulario do CRM. Quando o provedor mudar o formato do webhook, o diff
 * fica na implementacao.
 *
 * <p>A verificacao de posse e a validacao de assinatura vivem aqui pelo mesmo motivo do resto: cada
 * provedor faz isso de um jeito. Na Meta sao dois mecanismos independentes: o {@code GET} compara o
 * token de verificacao escolhido pela instancia; o {@code POST} valida o HMAC-SHA256 do corpo com o
 * App Secret. Misturar os dois impede cadastrar o webhook ou, pior, reutiliza um segredo onde nao
 * deveria.
 */
public interface TradutorDeCanal {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. */
    String provedor();

    /**
     * O token do desafio de cadastro confere com o token de verificacao da instancia?
     *
     * <p>Nao e assinatura de payload. Na Meta este valor vem em {@code hub.verify_token} no
     * {@code GET} de verificacao e deve ser comparado em tempo constante.
     */
    boolean tokenDeVerificacaoValido(String tokenRecebido);

    /**
     * A requisicao veio mesmo do provedor?
     *
     * <p>Verificado <b>antes</b> de qualquer processamento e antes de gravar qualquer coisa: a rota
     * do webhook e publica, entao qualquer um na internet consegue chamar. Sem esta checagem, injetar
     * mensagem falsa na conversa de um cliente e um {@code curl}.
     */
    boolean assinaturaValida(String payloadCru, String assinaturaRecebida);

    /**
     * Destinos declarados em cada evento contido no POST do provedor.
     *
     * <p>{@code quantidadeEventos} inclui eventos sem identificador. Isso impede que uma mudanca
     * malformada desapareca da contagem e transforme um payload misto em aparentemente seguro.
     */
    DestinosDoWebhook destinos(String payloadCru);

    /** IDs das mensagens presentes no POST; a fila usa o primeiro para a chave da linha do POST. */
    List<String> idsExternos(String payloadCru);

    /**
     * Atualizacoes de entrega ({@code statuses[]} na Meta), ja em vocabulario do CRM.
     *
     * <p>Vazio quando o POST nao traz status. O dominio nao conhece {@code sent}/{@code delivered}/
     * {@code read}/{@code failed}: quem mapeia e o adaptador.
     */
    List<StatusDeEntregaDoCanal> statusDeEntrega(String payloadCru);

    /** Traduz todas as mensagens, na ordem do payload. Vazio quando nao ha mensagens de cliente. */
    List<MensagemRecebidaDoCanal> traduzir(String payloadCru);

    /**
     * Uma atualizacao de entrega, ja traduzida.
     *
     * @param statusEntrega nome de {@code StatusEntrega} ({@code ENVIADO}, {@code ENTREGUE},
     *     {@code LIDO}, {@code FALHOU}); o ACL nao vaza o literal do provedor
     * @param codigoErro codigo do provedor quando {@code statusEntrega} e {@code FALHOU}; senao
     *     {@code null}
     * @param tituloErro titulo do provedor quando falhou; senao {@code null}
     */
    record StatusDeEntregaDoCanal(
            String wamid, String statusEntrega, Integer codigoErro, String tituloErro) {

        public StatusDeEntregaDoCanal {
            Objects.requireNonNull(wamid, "wamid e obrigatorio");
            Objects.requireNonNull(statusEntrega, "status de entrega e obrigatorio");
        }
    }

    record DestinosDoWebhook(int quantidadeEventos, List<String> identificadores) {

        public DestinosDoWebhook {
            if (quantidadeEventos < 0) {
                throw new IllegalArgumentException("quantidade de eventos nao pode ser negativa");
            }
            identificadores = List.copyOf(identificadores);
        }
    }

    /**
     * Uma mensagem de cliente, ja em vocabulario do CRM.
     *
     * @param telefoneRemetente e por ele que se acha (ou se cria) o lead — o provedor nao conhece
     *     nosso id
     * @param nomeExibicao como o cliente aparece no WhatsApp; serve para nomear um lead novo
     * @param texto {@code null} quando a mensagem e midia
     * @param tipo {@code "TEXTO"}, {@code "IMAGEM"}, {@code "AUDIO"}, {@code "DOCUMENTO"} ou
     *     {@code "VIDEO"} — nome
     *     de {@code TipoMensagem} como String, e nao o enum em si, para o dominio de canal nao
     *     depender do de mensagem so por causa disto; quem converte e
     *     {@code ProcessadorDeWebhookEntradaOperacoes}
     * @param midiaIdExterno id da midia no provedor — chave para {@link
     *     CanalGateway#baixarMidiaRecebida}; {@code null} quando {@code tipo} e {@code "TEXTO"}
     * @param mimetype mimetype declarado pelo provedor; a instancia continua verificando por magic
     *     bytes depois de baixar, este e so o valor que veio no webhook
     * @param nomeArquivo so preenchido para documento
     * @param legenda caption enviada junto com a midia, se houver
     * @param identificadorDestino identificador externo do numero que recebeu a mensagem
     * @param contextoWamid identificador externo da mensagem citada, quando o cliente respondeu a
     *     uma mensagem anterior
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
            Instant enviadoEm,
            String identificadorDestino,
            String contextoWamid) {

        /** Compatibilidade para tradutores e fixtures que nao oferecem contexto. */
        public MensagemRecebidaDoCanal(
                String idExterno,
                String telefoneRemetente,
                String nomeExibicao,
                String texto,
                String tipo,
                String midiaIdExterno,
                String mimetype,
                String nomeArquivo,
                String legenda,
                Instant enviadoEm,
                String identificadorDestino) {
            this(
                    idExterno,
                    telefoneRemetente,
                    nomeExibicao,
                    texto,
                    tipo,
                    midiaIdExterno,
                    mimetype,
                    nomeArquivo,
                    legenda,
                    enviadoEm,
                    identificadorDestino,
                    null);
        }

        /** Atalho para o caso comum: mensagem de texto, sem nenhum campo de midia. */
        public static MensagemRecebidaDoCanal texto(
                String idExterno, String telefoneRemetente, String nomeExibicao, String texto,
                Instant enviadoEm) {
            return new MensagemRecebidaDoCanal(
                    idExterno, telefoneRemetente, nomeExibicao, texto, "TEXTO", null, null, null, null,
                    enviadoEm, null, null);
        }

        public static MensagemRecebidaDoCanal texto(
                String idExterno,
                String identificadorDestino,
                String telefoneRemetente,
                String nomeExibicao,
                String texto,
                Instant enviadoEm) {
            return new MensagemRecebidaDoCanal(
                    idExterno, telefoneRemetente, nomeExibicao, texto, "TEXTO", null, null, null, null,
                    enviadoEm, identificadorDestino, null);
        }

        public static MensagemRecebidaDoCanal texto(
                String idExterno,
                String identificadorDestino,
                String telefoneRemetente,
                String nomeExibicao,
                String texto,
                Instant enviadoEm,
                String contextoWamid) {
            return new MensagemRecebidaDoCanal(
                    idExterno, telefoneRemetente, nomeExibicao, texto, "TEXTO", null, null, null, null,
                    enviadoEm, identificadorDestino, contextoWamid);
        }

        public boolean ehMidia() {
            return !"TEXTO".equals(tipo);
        }
    }
}
