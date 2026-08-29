package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.synapse.crm.atendimento.domain.canal.CanalIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.PedidoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

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
    /** Listar/criar template nao pode abrir o breaker do envio — a aba Atendimentos continua. */
    private static final String NOME_DO_BREAKER_TEMPLATES = "canal-meta-cloud-templates";
    private static final int EXCESSO_DE_CHAMADAS = 429;
    private static final Pattern VARIAVEL_DO_CORPO = Pattern.compile("\\{\\{(\\d+)\\}\\}");
    /** A Meta recusa amostra com "exemplo", "test", "sample". */
    private static final String[] AMOSTRAS_DE_PARAMETRO = {"Maria", "Ana", "Joao", "Paulo", "Carla"};

    private static final Logger log = LoggerFactory.getLogger(MetaCloudApiAdapter.class);

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final CircuitBreaker breakerTemplates;
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
        this.breakerTemplates = breakers.circuitBreaker(NOME_DO_BREAKER_TEMPLATES);
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
        if (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()) {
            raiz.putObject("context").put("message_id", envio.contextoWamid());
        }

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
            case BOTOES, LISTA -> throw new IllegalArgumentException("mensagem interativa nao e midia");
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
    public List<TemplateDoCanal> listarTemplates() {
        String conta = exigirContaNegocio();
        try {
            return breakerTemplates.executeSupplier(() -> buscarTemplates(conta));
        } catch (CallNotPermittedException breakerAberto) {
            throw new CanalIndisponivelException("circuit breaker aberto para templates da Meta");
        } catch (RestClientResponseException e) {
            throw new CanalIndisponivelException(
                    "provedor recusou listar templates: HTTP "
                            + e.getStatusCode().value()
                            + " "
                            + resumoDoErro(e.getResponseBodyAsString()));
        }
    }

    @Override
    public ResultadoDeTemplate criarTemplate(PedidoDeTemplate pedido) {
        String conta = exigirContaNegocio();
        try {
            return breakerTemplates.executeSupplier(() -> submeterTemplate(pedido, conta));
        } catch (CallNotPermittedException breakerAberto) {
            throw new CanalIndisponivelException("circuit breaker aberto para templates da Meta");
        } catch (RestClientResponseException e) {
            throw new CanalIndisponivelException(
                    "provedor recusou criar template: HTTP "
                            + e.getStatusCode().value()
                            + " "
                            + resumoDoErro(e.getResponseBodyAsString()));
        }
    }

    private List<TemplateDoCanal> buscarTemplates(String conta) {
        JsonNode raiz = http.get()
                .uri(
                        "/{conta}/message_templates?limit=100&fields=name,language,status,category,components",
                        conta)
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .body(JsonNode.class);
        List<TemplateDoCanal> templates = new ArrayList<>();
        if (raiz == null) {
            return templates;
        }
        JsonNode dados = raiz.path("data");
        if (!dados.isArray()) {
            return templates;
        }
        for (JsonNode item : dados) {
            templates.add(paraTemplateDoCanal(item));
        }
        return templates;
    }

    private ResultadoDeTemplate submeterTemplate(PedidoDeTemplate pedido, String conta) {
        try {
            JsonNode resposta = http.post()
                    .uri("/{conta}/message_templates", conta)
                    .header("Authorization", "Bearer " + propriedades.token())
                    .header("Content-Type", "application/json")
                    .body(corpoDoPedidoDeTemplate(pedido))
                    .retrieve()
                    .body(JsonNode.class);
            TemplateDoCanal.Status status = traduzirStatus(
                    resposta == null ? "" : resposta.path("status").asText());
            TemplateDoCanal.Categoria categoria = pedido.categoria();
            if (resposta != null && !resposta.path("category").asText().isBlank()) {
                categoria = traduzirCategoria(resposta.path("category").asText());
            }
            return new ResultadoDeTemplate.Aceito(new TemplateDoCanal(
                    pedido.nome(),
                    pedido.idioma(),
                    categoria,
                    status == TemplateDoCanal.Status.DESCONHECIDO
                            ? TemplateDoCanal.Status.PENDENTE
                            : status,
                    pedido.corpo(),
                    contarParametros(pedido.corpo())));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 500 || status == EXCESSO_DE_CHAMADAS) {
                throw e;
            }
            String motivo = resumoDoErro(e.getResponseBodyAsString());
            log.warn("Meta recusou o template {}: HTTP {} {}", pedido.nome(), status, motivo);
            return new ResultadoDeTemplate.Recusado(motivo.isBlank() ? ("HTTP " + status) : motivo);
        }
    }

    private ObjectNode corpoDoPedidoDeTemplate(PedidoDeTemplate pedido) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("name", pedido.nome());
        raiz.put("language", pedido.idioma());
        raiz.put("category", categoriaParaMeta(pedido.categoria()));
        raiz.put("parameter_format", "positional");
        raiz.put("allow_category_change", true);
        ObjectNode corpo = raiz.putArray("components").addObject();
        corpo.put("type", "BODY");
        corpo.put("text", pedido.corpo());
        int parametros = contarParametros(pedido.corpo());
        if (parametros > 0) {
            ArrayNode amostra = corpo.putObject("example").putArray("body_text").addArray();
            for (int i = 1; i <= parametros; i++) {
                amostra.add(AMOSTRAS_DE_PARAMETRO[(i - 1) % AMOSTRAS_DE_PARAMETRO.length]);
            }
        }
        return raiz;
    }

    private String exigirContaNegocio() {
        if (!propriedades.temContaNegocio()) {
            throw new CanalIndisponivelException(
                    "WHATSAPP_CONTA_NEGOCIO nao configurada; informe o WABA ID para administrar templates");
        }
        return propriedades.contaNegocio();
    }

    private static TemplateDoCanal paraTemplateDoCanal(JsonNode item) {
        String corpo = corpoDoTemplate(item.path("components"));
        return new TemplateDoCanal(
                item.path("name").asText(),
                idiomaDoItem(item.path("language")),
                traduzirCategoria(item.path("category").asText()),
                traduzirStatus(item.path("status").asText()),
                corpo,
                contarParametros(corpo));
    }

    private static String idiomaDoItem(JsonNode idioma) {
        if (idioma.isTextual()) {
            return idioma.asText();
        }
        String codigo = idioma.path("code").asText();
        return codigo.isBlank() ? idioma.path("language").asText() : codigo;
    }

    private static String corpoDoTemplate(JsonNode componentes) {
        if (!componentes.isArray()) {
            return "";
        }
        for (JsonNode componente : componentes) {
            if ("BODY".equalsIgnoreCase(componente.path("type").asText())) {
                return componente.path("text").asText("");
            }
        }
        return "";
    }

    private static int contarParametros(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return 0;
        }
        Matcher casamento = VARIAVEL_DO_CORPO.matcher(corpo);
        int maximo = 0;
        while (casamento.find()) {
            maximo = Math.max(maximo, Integer.parseInt(casamento.group(1)));
        }
        return maximo;
    }

    private static String categoriaParaMeta(TemplateDoCanal.Categoria categoria) {
        return switch (categoria) {
            case UTILIDADE -> "UTILITY";
            case MARKETING -> "MARKETING";
            case AUTENTICACAO -> "AUTHENTICATION";
        };
    }

    private static TemplateDoCanal.Categoria traduzirCategoria(String categoria) {
        return switch (categoria == null ? "" : categoria.toUpperCase()) {
            case "UTILITY", "UTILIDADE" -> TemplateDoCanal.Categoria.UTILIDADE;
            case "MARKETING" -> TemplateDoCanal.Categoria.MARKETING;
            case "AUTHENTICATION", "AUTENTICACAO" -> TemplateDoCanal.Categoria.AUTENTICACAO;
            default -> TemplateDoCanal.Categoria.UTILIDADE;
        };
    }

    private static TemplateDoCanal.Status traduzirStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "APPROVED", "APROVADO" -> TemplateDoCanal.Status.APROVADO;
            case "PENDING", "PENDING_DELETION", "IN_APPEAL", "PENDENTE" -> TemplateDoCanal.Status.PENDENTE;
            case "REJECTED", "REJEITADO" -> TemplateDoCanal.Status.REJEITADO;
            case "PAUSED", "DISABLED", "PAUSADO" -> TemplateDoCanal.Status.PAUSADO;
            default -> TemplateDoCanal.Status.DESCONHECIDO;
        };
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
            if (erro.isMissingNode()) {
                return corpo;
            }
            String paraUsuario = textoDoNo(erro, "error_user_msg");
            if (paraUsuario.isBlank()) {
                paraUsuario = textoDoNo(erro.path("error_data"), "details");
            }
            if (!paraUsuario.isBlank()) {
                return paraUsuario;
            }
            String codigo = textoDoNo(erro, "code");
            String mensagem = textoDoNo(erro, "message");
            return (codigo + " " + mensagem).trim();
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return corpo;
        }
    }

    private static String textoDoNo(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asText("");
    }
}
