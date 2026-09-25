package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;

/**
 * Traducao do webhook da Meta Cloud API. A outra metade do ACL.
 *
 * <p>O formato da Meta e profundamente aninhado — {@code entry[].changes[].value.messages[]} — e nada
 * disso sai daqui. Quem chama recebe {@link TradutorDeCanal.MensagemRecebidaDoCanal} e nao tem como
 * saber de onde veio.
 */
@Component
class MetaCloudWebhookTradutor implements TradutorDeCanal {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudWebhookTradutor.class);

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO_ASSINATURA = "sha256=";

    private final CanalProperties propriedades;
    private final ObjectMapper json;

    MetaCloudWebhookTradutor(CanalProperties propriedades, ObjectMapper json) {
        this.propriedades = propriedades;
        this.json = json;
    }

    @Override
    public String provedor() {
        return MetaCloudApiAdapter.PROVEDOR;
    }

    /** Token do desafio de cadastro, distinto do App Secret que assina o {@code POST}. */
    @Override
    public boolean tokenDeVerificacaoValido(String tokenRecebido) {
        if (!propriedades.temTokenDeVerificacao()) {
            log.error(
                    "synapse.canal.whatsapp.webhook-verify-token ausente: recusando desafio de webhook.");
            return false;
        }
        if (tokenRecebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                propriedades.webhookVerifyToken().getBytes(StandardCharsets.UTF_8),
                tokenRecebido.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * HMAC-SHA256 do corpo cru com o segredo do app, como a Meta manda em
     * {@code X-Hub-Signature-256}.
     *
     * <p>Duas decisoes deliberadas de falhar fechado:
     *
     * <ul>
     *   <li>sem segredo configurado, <b>nada</b> e aceito. A alternativa — pular a verificacao quando
     *       falta configuracao — significa que esquecer uma variavel de ambiente abre a rota para a
     *       internet inteira, sem nenhum sinal;
     *   <li>a comparacao usa {@link MessageDigest#isEqual}, que nao retorna cedo. Um {@code equals()}
     *       comum vaza, pelo tempo de resposta, quantos bytes iniciais bateram — e assinatura se
     *       descobre byte a byte assim.
     * </ul>
     */
    @Override
    public boolean assinaturaValida(
            String payloadCru, String assinaturaCabecalho, String segredoConsulta) {
        if (!propriedades.temSegredoDeWebhook()) {
            log.error("synapse.canal.whatsapp.webhook-secret ausente: recusando todo webhook.");
            return false;
        }
        if (assinaturaCabecalho == null || !assinaturaCabecalho.startsWith(PREFIXO_ASSINATURA)) {
            return false;
        }

        byte[] esperada = calcular(payloadCru);
        byte[] recebida = decodificar(assinaturaCabecalho.substring(PREFIXO_ASSINATURA.length()));
        return recebida.length > 0 && MessageDigest.isEqual(esperada, recebida);
    }

    @Override
    public DestinosDoWebhook destinos(String payloadCru) {
        List<String> identificadores = new ArrayList<>();
        int quantidadeEventos = 0;
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                quantidadeEventos++;
                String identificador = mudanca.path("value")
                        .path("metadata")
                        .path("phone_number_id")
                        .asText(null);
                if (identificador != null && !identificador.isBlank()) {
                    identificadores.add(identificador);
                }
            }
        }
        return new DestinosDoWebhook(quantidadeEventos, identificadores);
    }

    @Override
    public List<String> idsExternos(String payloadCru) {
        return mensagens(payloadCru).stream()
                .map(MensagemDoPayload::mensagem)
                .map(mensagem -> mensagem.path("id").asText(null))
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    @Override
    public List<StatusDeEntregaDoCanal> statusDeEntrega(String payloadCru) {
        List<StatusDeEntregaDoCanal> resultado = new ArrayList<>();
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                JsonNode statuses = mudanca.path("value").path("statuses");
                if (!statuses.isArray()) {
                    continue;
                }
                for (JsonNode status : statuses) {
                    StatusDeEntregaDoCanal traduzido = traduzirStatus(status);
                    if (traduzido != null) {
                        resultado.add(traduzido);
                    }
                }
            }
        }
        return List.copyOf(resultado);
    }

    private static final Map<String, String> STATUS_META_PARA_CRM =
            Map.of("sent", "ENVIADO", "delivered", "ENTREGUE", "read", "LIDO", "failed", "FALHOU");

    private static StatusDeEntregaDoCanal traduzirStatus(JsonNode status) {
        String wamid = status.path("id").asText(null);
        if (wamid == null || wamid.isBlank()) {
            return null;
        }
        String crm = STATUS_META_PARA_CRM.get(status.path("status").asText());
        if (crm == null) {
            return null;
        }
        Integer codigo = null;
        String titulo = null;
        JsonNode erros = status.path("errors");
        if (erros.isArray() && !erros.isEmpty()) {
            JsonNode primeiro = erros.get(0);
            if (primeiro.path("code").canConvertToInt()) {
                codigo = primeiro.path("code").asInt();
            }
            String lido = primeiro.path("title").asText(null);
            titulo = (lido == null || lido.isBlank()) ? null : lido;
        }
        return new StatusDeEntregaDoCanal(wamid, crm, codigo, titulo);
    }

    /** {@code type} da Meta -> {@code TipoMensagem} do CRM. {@code null} para tipo desconhecido. */
    private static final Map<String, String> TIPO_META_PARA_CRM =
            Map.of(
                    "image", "IMAGEM",
                    "audio", "AUDIO",
                    "document", "DOCUMENTO",
                    "video", "VIDEO",
                    "sticker", "IMAGEM");

    @Override
    public Traducao traduzirComDescartes(String payloadCru) {
        List<MensagemRecebidaDoCanal> traduzidas = new ArrayList<>();
        List<ItemDescartado> descartes = new ArrayList<>();
        for (MensagemDoPayload item : mensagens(payloadCru)) {
            String tipo = TipoDeItemDoProvedor.normalizar(item.mensagem().path("type").asText(null));
            try {
                if (ehGrupo(item.mensagem())) {
                    throw new ItemNaoTraduzido(MotivoDeDescarte.GRUPO_NAO_SUPORTADO);
                }
                traduzidas.add(traduzirItem(item));
            } catch (ItemNaoTraduzido e) {
                descartes.add(new ItemDescartado(tipo, e.motivo()));
            } catch (RuntimeException e) {
                // Um item malformado nao pode levar os demais do mesmo POST. Nem a excecao nem o
                // JSON vao para o log: os dois podem carregar telefone ou conteudo.
                log.warn("Item de webhook Meta malformado; item descartado. type={}", tipo);
                descartes.add(new ItemDescartado(tipo, MotivoDeDescarte.ITEM_MALFORMADO));
            }
        }
        return new Traducao(traduzidas, descartes);
    }

    /**
     * POST misto: o corpo muda, entao a assinatura e recalculada com o mesmo App Secret que validou a
     * entrada — quem confere {@code X-Hub-Signature-256} no n8n continua aceitando. Sem grupo, nada
     * muda: corpo e assinatura originais.
     */
    @Override
    public java.util.Optional<RepasseParaAutomacao> repasseSemGrupos(String payloadCru, String assinatura) {
        return switch (RepasseSemGrupos.filtrar(payloadCru, json, MetaCloudWebhookTradutor::ehGrupo)) {
            case RepasseSemGrupos.Resultado.Intacto intacto ->
                    java.util.Optional.of(new RepasseParaAutomacao(payloadCru, assinatura));
            case RepasseSemGrupos.Resultado.SemConteudo vazio -> java.util.Optional.empty();
            case RepasseSemGrupos.Resultado.Filtrado filtrado -> java.util.Optional.of(new RepasseParaAutomacao(
                    filtrado.payload(),
                    PREFIXO_ASSINATURA + HexFormat.of().formatHex(calcular(filtrado.payload()))));
        };
    }

    /**
     * Defensivo: a conta Cloud API usada é de conversa individual, mas a API de grupos da Meta marca a
     * mensagem de grupo com {@code group_id}. Se um dia chegar, o {@code from} é o participante —
     * traduzir colaria o grupo no privado dele.
     */
    private static boolean ehGrupo(JsonNode mensagem) {
        String grupo = mensagem.path("group_id").asText(null);
        return grupo != null && !grupo.isBlank();
    }

    private MensagemRecebidaDoCanal traduzirItem(MensagemDoPayload item) {
        JsonNode no = item.mensagem();
        String tipoMeta = no.path("type").asText();
        Origem origem = origemDo(item);
        if (origem.idExterno().isBlank() || origem.telefoneRemetente().isBlank()) {
            // Mesma checagem da Uzapi, aqui no tradutor: o descarte sai com o tipo no vocabulario
            // do provedor, e a mesma causa nao aparece com dois nomes conforme o provedor.
            log.warn("Mensagem Meta sem identificador obrigatorio; item descartado. type={}", tipoMeta);
            throw new ItemNaoTraduzido(MotivoDeDescarte.SEM_IDENTIFICADOR);
        }
        return switch (tipoMeta) {
            case "text" -> origem.texto(no.path("text").path("body").asText());
            // A resposta do cliente é texto do ponto de vista do histórico. O id interno da
            // opção é controle do provedor; o atendente precisa ver o título que o cliente leu.
            case "interactive" -> origem.texto(tituloExigido(no.path("interactive")));
            case "location" -> origem.estruturada("LOCALIZACAO", localizacao(no.path("location")));
            // messages[].contacts[] e o cartao compartilhado; value.contacts[] (remetente) nao
            // chega aqui.
            case "contacts" -> origem.estruturada("CONTATO", contatoCompartilhado(no.path("contacts")));
            default -> origem.midia(tipoDeMidia(tipoMeta), no.path(tipoMeta));
        };
    }

    private Origem origemDo(MensagemDoPayload item) {
        JsonNode no = item.mensagem();
        String telefoneRemetente = no.path("from").asText();
        return new Origem(
                no.path("id").asText(),
                item.valor().path("metadata").path("phone_number_id").asText(null),
                telefoneRemetente,
                nomeDeExibicao(item.valor(), telefoneRemetente),
                // A Meta manda epoch em segundos, como string.
                Instant.ofEpochSecond(no.path("timestamp").asLong(Instant.now().getEpochSecond())),
                no.path("context").path("id").asText(null));
    }

    private static String tituloExigido(JsonNode interativa) {
        String titulo = tituloDaResposta(interativa);
        if (titulo == null || titulo.isBlank()) {
            // Sem este registro a resposta do cliente sumia sem rastro no histórico (E134). type e
            // nomes das chaves bastam para diagnosticar; o payload inteiro carrega telefone/conteúdo.
            log.warn(
                    "Resposta interativa sem titulo reconhecido; item descartado. type={} chaves={}",
                    interativa.path("type").asText(""),
                    campos(interativa));
            throw new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
        }
        return titulo;
    }

    private String localizacao(JsonNode locNode) {
        double latitude = locNode.path("latitude").asDouble();
        double longitude = locNode.path("longitude").asDouble();
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            // As coordenadas nao vao para o log: localizacao do cliente e dado pessoal.
            log.warn("Coordenadas de localizacao invalidas; item descartado.");
            throw new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
        }

        ObjectNode metadados = json.createObjectNode();
        metadados.put("latitude", latitude);
        metadados.put("longitude", longitude);
        String nome = locNode.path("name").asText(null);
        if (nome != null && !nome.isBlank()) {
            metadados.put("nome", nome);
        }
        String endereco = locNode.path("address").asText(null);
        if (endereco != null && !endereco.isBlank()) {
            metadados.put("endereco", endereco);
        }
        return metadados.toString();
    }

    private String contatoCompartilhado(JsonNode contatos) {
        return ContatoCompartilhado.metadados(contatos, json).orElseThrow(() -> {
            log.warn("Contato compartilhado sem nome nem telefone; item descartado.");
            return new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
        });
    }

    private static String tipoDeMidia(String tipoMeta) {
        String tipoCrm = TIPO_META_PARA_CRM.get(tipoMeta);
        if (tipoCrm == null) {
            // Nem texto, nem midia suportada (reacao, unsupported, etc.). Descartar somente este
            // item preserva as mensagens boas que vierem no mesmo POST; o descarte vai para a
            // linha da fila, e o warn continua para quem le o log (E134).
            log.warn("Tipo de mensagem Meta desconhecido; item descartado. type={}", tipoMeta);
            throw new ItemNaoTraduzido(MotivoDeDescarte.TIPO_NAO_SUPORTADO);
        }
        return tipoCrm;
    }

    /** O que todo item de mensagem carrega, independente do tipo. */
    private record Origem(
            String idExterno,
            String identificadorDestino,
            String telefoneRemetente,
            String nomeExibicao,
            Instant enviadoEm,
            String contextoWamid) {

        MensagemRecebidaDoCanal texto(String texto) {
            return MensagemRecebidaDoCanal.texto(
                    idExterno, identificadorDestino, telefoneRemetente, nomeExibicao, texto, enviadoEm,
                    contextoWamid);
        }

        MensagemRecebidaDoCanal estruturada(String tipo, String metadados) {
            return new MensagemRecebidaDoCanal(
                    idExterno, telefoneRemetente, nomeExibicao, metadados, tipo, null, null, null, null,
                    enviadoEm, identificadorDestino, contextoWamid);
        }

        MensagemRecebidaDoCanal midia(String tipo, JsonNode midiaNo) {
            String midiaId = midiaNo.path("id").asText(null);
            if (midiaId == null || midiaId.isBlank()) {
                // Sem id nao ha como baixar a midia com seguranca.
                log.warn("Midia Meta sem id; item descartado. type={}", tipo);
                throw new ItemNaoTraduzido(MotivoDeDescarte.SEM_IDENTIFICADOR);
            }
            return new MensagemRecebidaDoCanal(
                    idExterno,
                    telefoneRemetente,
                    nomeExibicao,
                    null,
                    tipo,
                    midiaId,
                    midiaNo.path("mime_type").asText(null),
                    midiaNo.path("filename").asText(null),
                    midiaNo.path("caption").asText(null),
                    enviadoEm,
                    identificadorDestino,
                    contextoWamid);
        }
    }

    // --- formato da Meta (nada abaixo daqui sai desta classe) ------------------

    private List<MensagemDoPayload> mensagens(String payloadCru) {
        List<MensagemDoPayload> resultado = new ArrayList<>();
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                JsonNode valor = mudanca.path("value");
                JsonNode mensagens = valor.path("messages");
                if (!mensagens.isArray()) {
                    continue;
                }
                for (JsonNode mensagem : mensagens) {
                    resultado.add(new MensagemDoPayload(valor, mensagem));
                }
            }
        }
        return resultado;
    }

    private String nomeDeExibicao(JsonNode valor, String telefoneRemetente) {
        JsonNode contatos = valor.path("contacts");
        if (!contatos.isArray()) {
            return null;
        }
        for (JsonNode contato : contatos) {
            if (telefoneRemetente.equals(contato.path("wa_id").asText(null))) {
                return contato.path("profile").path("name").asText(null);
            }
        }
        return null;
    }

    /**
     * Chaves estáveis do objeto de resposta no webhook de entrada. Não usar o valor de
     * {@code interactive.type}: na mensagem que <em>sai</em> a Meta manda {@code button}/{@code list};
     * na resposta que <em>chega</em> manda {@code button_reply}/{@code list_reply}. Comparar o
     * {@code type} descartou em silêncio todas as escolhas do cliente (E134).
     */
    private static final List<String> CHAVES_DE_RESPOSTA_INTERATIVA =
            List.of("list_reply", "button_reply", "nfm_reply");

    private static String tituloDaResposta(JsonNode interativa) {
        for (String chave : CHAVES_DE_RESPOSTA_INTERATIVA) {
            String titulo = interativa.path(chave).path("title").asText(null);
            if (titulo != null && !titulo.isBlank()) {
                return titulo;
            }
        }
        return null;
    }

    /** Nomes das chaves do nó — sem valores, para não vazar telefone/conteúdo no log. */
    private static String campos(JsonNode no) {
        List<String> nomes = new ArrayList<>();
        no.fieldNames().forEachRemaining(nomes::add);
        return nomes.toString();
    }

    private record MensagemDoPayload(JsonNode valor, JsonNode mensagem) {}

    private JsonNode entradas(String payloadCru) {
        try {
            JsonNode entradas = json.readTree(payloadCru).path("entry");
            if (!entradas.isArray()) {
                return json.createArrayNode();
            }
            return entradas;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Payload de webhook ilegivel.", e);
            return json.createArrayNode();
        }
    }

    private byte[] calcular(String payloadCru) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(
                    propriedades.webhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(payloadCru.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao calcular a assinatura do webhook", e);
        }
    }

    private static byte[] decodificar(String hexadecimal) {
        try {
            return HexFormat.of().parseHex(hexadecimal);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
