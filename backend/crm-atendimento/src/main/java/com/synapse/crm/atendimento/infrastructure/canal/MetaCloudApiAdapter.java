package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;

/**
 * Anti-Corruption Layer da Meta Cloud API.
 *
 * <p>Esta e a <b>unica</b> classe do sistema que conhece o formato da Meta. Os nomes de campo
 * ({@code messaging_product}, {@code template.components}, {@code messages[0].id}) nascem e morrem
 * aqui; para fora saem {@link ResultadoDeEnvio} e {@link ConteudoDeEnvio}, que sao vocabulario do
 * CRM. Quando a Meta mudar a versao da API — e vai —, o diff fica neste arquivo.
 *
 * <p>Duas regras da API oficial viram comportamento aqui, e nao no dominio:
 *
 * <ul>
 *   <li><b>janela de 24h</b> — fora dela so passa template. Um filho com provedor nao oficial
 *       responde sempre {@code true} em {@link #aceitaTextoLivre}, sem que nada no dominio mude;
 *   <li><b>template posicional</b> — nome, idioma e parametros na ordem declarada.
 * </ul>
 *
 * <p>O circuit breaker cobre toda chamada de saida. Quando abre, o metodo devolve recusa
 * <em>temporaria</em>: a mensagem permanece na outbox e sai quando o provedor voltar. Nao ha excecao
 * subindo ate a tela — degradacao explicita, nao erro de usuario.
 */
@Component
class MetaCloudApiAdapter implements CanalGateway {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. */
    static final String PROVEDOR = "meta-cloud";

    private static final String NOME_DO_BREAKER = "canal-meta-cloud";
    private static final int EXCESSO_DE_CHAMADAS = 429;

    private static final Logger log = LoggerFactory.getLogger(MetaCloudApiAdapter.class);

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final ArmazenamentoDeMidia armazenamento;

    MetaCloudApiAdapter(
            RestClient.Builder builder,
            CanalProperties propriedades,
            ObjectMapper json,
            CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia armazenamento) {
        this.http = builder.baseUrl(propriedades.urlBase()).build();
        this.propriedades = propriedades;
        this.json = json;
        this.breaker = breakers.circuitBreaker(NOME_DO_BREAKER);
        this.armazenamento = armazenamento;
    }

    @Override
    public String provedor() {
        return PROVEDOR;
    }

    /**
     * A janela conta a partir da ultima interacao <em>do lead</em>, que e o que
     * {@code lead.ultima_interacao_em} guarda desde a E04.
     *
     * <p>Lead que nunca interagiu tem janela fechada: nao ha conversa iniciada pelo cliente, e a Meta
     * so aceita template nesse caso. E exatamente a situacao de toda campanha de primeira abordagem.
     */
    @Override
    public boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora) {
        return ultimaInteracaoDoLead
                .map(ultima -> ultima.isAfter(agora.minus(propriedades.janelaTextoLivre())))
                .orElse(false);
    }

    @Override
    public boolean exigeTemplateForaDaJanela() {
        return true;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (propriedades.token() == null
                || propriedades.token().isBlank()
                || propriedades.numeroPrincipal() == null
                || propriedades.numeroPrincipal().isBlank()) {
            return AutenticacaoDoCanal.recusada("token ou identificador do canal ausente");
        }
        try {
            return breaker.executeSupplier(this::consultarIdentidadeDoCanal);
        } catch (CallNotPermittedException e) {
            return AutenticacaoDoCanal.recusada("circuit breaker do provedor aberto");
        } catch (RestClientResponseException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor recusou a credencial com HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor indisponivel: " + e.getClass().getSimpleName());
        }
    }

    private AutenticacaoDoCanal consultarIdentidadeDoCanal() {
        JsonNode resposta = http.get()
                .uri("/{numero}?fields=id", propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .body(JsonNode.class);
        if (resposta == null || resposta.path("id").asText().isBlank()) {
            return AutenticacaoDoCanal.recusada("provedor respondeu sem identificar o canal");
        }
        return AutenticacaoDoCanal.aceita();
    }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        try {
            return breaker.executeSupplier(() -> chamar(envio));
        } catch (CallNotPermittedException breakerAberto) {
            // Modo degradado explicito: o provedor esta reconhecidamente fora, entao nem
            // tentamos. A mensagem fica na outbox e sai quando ele voltar.
            log.warn(
                    "Breaker {} aberto; mensagem {} continua na outbox.",
                    NOME_DO_BREAKER,
                    envio.mensagemId());
            return ResultadoDeEnvio.Recusado.temporario("circuit breaker aberto para " + PROVEDOR);
        }
    }

    private ResultadoDeEnvio chamar(Envio envio) {
        try {
            String resposta = http.post()
                    .uri("/{numero}/messages", propriedades.numeroPrincipal())
                    .header("Authorization", "Bearer " + propriedades.token())
                    .header("Content-Type", "application/json")
                    .body(corpo(envio))
                    .retrieve()
                    .body(String.class);

            return new ResultadoDeEnvio.Aceito(idDaMensagem(resposta));

        } catch (RestClientResponseException e) {
            return traduzirErro(e);
        }
        // Timeout, DNS, conexao recusada sobem como excecao de propósito: o breaker
        // precisa conta-las como falha para chegar a abrir.
    }

    /**
     * Traduz o erro HTTP da Meta em recusa permanente ou temporaria.
     *
     * <p>A distincao decide o destino da linha na outbox, entao errar aqui custa: retentar o que
     * nunca vai funcionar gasta quota, e desistir do que so precisava de trinta segundos perde a
     * mensagem. 4xx e defeito do pedido — numero invalido, template nao aprovado, fora da janela — e
     * repetir da o mesmo resultado. 429 e a excecao: e 4xx, mas significa "agora nao", nao "nunca".
     */
    private ResultadoDeEnvio traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detalhe = status + " " + resumoDoErro(e.getResponseBodyAsString());

        boolean defeitoDoPedido =
                status >= 400 && status < 500 && status != EXCESSO_DE_CHAMADAS;

        return defeitoDoPedido
                ? ResultadoDeEnvio.Recusado.permanente(detalhe)
                : ResultadoDeEnvio.Recusado.temporario(detalhe);
    }

    // --- traducao do payload (nada abaixo daqui sai desta classe) --------------

    private ObjectNode corpo(Envio envio) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("messaging_product", "whatsapp");
        raiz.put("recipient_type", "individual");
        raiz.put("to", envio.telefoneDestino());

        switch (envio.conteudo()) {
            case ConteudoDeEnvio.MensagemLivre livre -> {
                raiz.put("type", "text");
                raiz.putObject("text").put("preview_url", false).put("body", livre.texto());
            }
            case ConteudoDeEnvio.MensagemTemplate template -> {
                raiz.put("type", "template");
                ObjectNode no = raiz.putObject("template");
                no.put("name", template.nome());
                no.putObject("language").put("code", template.idioma());

                // Parametros posicionais: a Meta os aplica na ordem em que chegam.
                if (!template.parametros().isEmpty()) {
                    ObjectNode corpoDoTemplate = no.putArray("components").addObject();
                    corpoDoTemplate.put("type", "body");
                    ArrayNode parametros = corpoDoTemplate.putArray("parameters");
                    template.parametros()
                            .forEach(valor ->
                                    parametros.addObject().put("type", "text").put("text", valor));
                }
            }
            case ConteudoDeEnvio.MensagemMidia midia -> {
                // A Meta nao aceita bytes direto na mensagem: e preciso subir o arquivo para o
                // endpoint de midia dela primeiro e referenciar o "media id" devolvido. Isso
                // acontece aqui, dentro do mesmo breaker.executeSupplier de chamar() — falha de
                // upload conta para o circuit breaker igual falha de envio.
                String campoTipo = campoDeTipoMeta(midia.tipo());
                String mediaId = subirMidiaParaAMeta(midia);
                raiz.put("type", campoTipo);
                ObjectNode midiaNo = raiz.putObject(campoTipo);
                midiaNo.put("id", mediaId);
                // A API da Meta so admite caption em image, video e document. Audio com esse
                // campo e rejeitado por inteiro; a legenda continua no historico do CRM, mas nao
                // pode fazer parte deste payload.
                if (midia.tipo() != TipoMensagem.AUDIO
                        && midia.legenda() != null
                        && !midia.legenda().isBlank()) {
                    midiaNo.put("caption", midia.legenda());
                }
                if (midia.tipo() == TipoMensagem.DOCUMENTO) {
                    String nomeArquivo = campoDeMetadados(midia.metadados(), "nome");
                    if (nomeArquivo != null) {
                        midiaNo.put("filename", nomeArquivo);
                    }
                }
            }
        }
        return raiz;
    }

    private static String campoDeTipoMeta(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> "image";
            case AUDIO -> "audio";
            case DOCUMENTO -> "document";
            case TEXTO -> throw new IllegalArgumentException("TEXTO nao e um tipo de midia");
        };
    }

    /** Upload multipart para {@code /{numero}/media} — devolve o {@code media id} da Meta. */
    private String subirMidiaParaAMeta(ConteudoDeEnvio.MensagemMidia midia) {
        byte[] conteudo = armazenamento.baixar(midia.referenciaStorage());
        String mimetype = campoDeMetadados(midia.metadados(), "mimetype");
        String nomeArquivo = campoDeMetadados(midia.metadados(), "nome");

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("messaging_product", "whatsapp");
        multipart.part("type", mimetype);
        multipart.part("file", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        });

        String resposta = http.post()
                .uri("/{numero}/media", propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .body(multipart.build())
                .retrieve()
                .body(String.class);

        try {
            return json.readTree(resposta).path("id").asText();
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("resposta de upload de midia da Meta ilegivel", e);
        }
    }

    private String campoDeMetadados(String metadadosJson, String campo) {
        if (metadadosJson == null) {
            return null;
        }
        try {
            JsonNode no = json.readTree(metadadosJson).path(campo);
            return no.isMissingNode() || no.isNull() ? null : no.asText();
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public CanalGateway.MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        try {
            return breaker.executeSupplier(() -> buscarMidiaRecebida(midiaIdExterno));
        } catch (CallNotPermittedException breakerAberto) {
            // Diferente de enviar(): nao ha "recusa temporaria" para o webhook — quem chama
            // (ProcessadorDeWebhookEntradaOperacoes) ja tem seu proprio retry com backoff.
            // Lancar deixa esse mecanismo cuidar de tentar de novo mais tarde.
            throw new IllegalStateException(
                    "circuit breaker aberto para " + PROVEDOR + "; midia " + midiaIdExterno
                            + " sera retentada");
        }
    }

    /**
     * A Meta entrega midia recebida por referencia, em dois passos: {@code GET /{media-id}} devolve
     * uma URL temporaria (minutos de validade) e o mimetype; so entao um segundo {@code GET} nessa
     * URL traz os bytes.
     */
    private CanalGateway.MidiaRecebida buscarMidiaRecebida(String midiaIdExterno) {
        String respostaMeta = http.get()
                .uri("/{id}", midiaIdExterno)
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .body(String.class);

        JsonNode no;
        try {
            no = json.readTree(respostaMeta);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("resposta da Meta ilegivel ao resolver midia recebida", e);
        }
        String urlTemporaria = no.path("url").asText();
        String mimetype = no.path("mime_type").asText();

        byte[] bytes = http.get()
                .uri(java.net.URI.create(urlTemporaria))
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .body(byte[].class);

        return new CanalGateway.MidiaRecebida(bytes, mimetype);
    }

    private String idDaMensagem(String resposta) {
        try {
            JsonNode mensagens = json.readTree(resposta).path("messages");
            return mensagens.isArray() && !mensagens.isEmpty()
                    ? mensagens.get(0).path("id").asText()
                    : "";
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // A Meta aceitou (2xx); nao ter conseguido ler o id nao desfaz o envio.
            log.warn("Resposta da Meta aceita mas ilegivel ao extrair o id da mensagem.", e);
            return "";
        }
    }

    private String resumoDoErro(String corpo) {
        try {
            JsonNode erro = json.readTree(corpo).path("error");
            return erro.isMissingNode()
                    ? corpo
                    : erro.path("code").asText() + " " + erro.path("message").asText();
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return corpo;
        }
    }
}
