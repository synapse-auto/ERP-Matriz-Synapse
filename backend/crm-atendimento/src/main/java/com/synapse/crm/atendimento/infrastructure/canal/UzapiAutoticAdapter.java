package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.synapse.crm.atendimento.application.midia.FalhaNaConversaoDeAudioException;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaRemovidaNoProvedorException;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.ResumoSeguroDeMidia;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

/**
 * Anti-Corruption Layer da Uzapi/Autotic ({@code uzapi.com.br}) — envio apenas (E152).
 *
 * <p><b>Este e um fornecedor diferente da UazAPI</b> ({@code uazapi.dev}/uazapiGO, documentada em
 * {@code docs/37-contrato-uazapi.md}). Os nomes sao quase identicos e ja causaram uma etapa inteira
 * (E148c) implementada contra o contrato errado. Por isso a chave de provedor, o nome desta classe
 * e toda variavel de ambiente usam deliberadamente {@code uzapi-autotic}, nunca {@code uazapi}
 * sozinho — ver {@code docs/38-contrato-uzapi-autotic.md}.
 *
 * <p>Contrato confirmado contra o Swagger oficial ({@code https://api.uzapi.com.br/docs/swagger.json})
 * em 11/09/2026: as rotas de negocio usam somente {@code {version}} e
 * {@code {phone_number_id}} — nunca {@code {username}}; o resolvedor de midia usa
 * {@code GET /{version}/{mediaId}}. {@code GET .../instance} consulta a saude,
 * {@code POST .../messages} envia, {@code POST .../media} (multipart) faz upload previo de midia.
 * Nao existe endpoint de gestao de
 * template no Swagger — o valor {@code "template"} aparece apenas no enum solto do campo
 * {@code type}, sem nenhum schema de corpo correspondente nos doze variantes documentados
 * (Text/Image/Audio/Video/Document/Reaction/Location/Contacts/Poll/Sticker/Revoke/Interactive).
 *
 * <p>O recebimento usa o mesmo identificador de midia que chega no webhook: primeiro resolve a URL
 * pelo endpoint oficial {@code GET /{version}/{mediaId}} e depois baixa os bytes nessa URL. O
 * segundo passo fica protegido pelo disjuntor dedicado de midia, assim a fila de entrada pode
 * retentar sem bloquear o caminho sincrono do webhook.
 */
@Component
class UzapiAutoticAdapter implements CanalGateway {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. Nunca "uazapi" — ver javadoc da classe. */
    static final String PROVEDOR = "uzapi-autotic";

    private static final String NOME_DO_BREAKER = "canal-uzapi-autotic";
    private static final String NOME_DO_BREAKER_MIDIA = "canal-uzapi-autotic-midia";
    private static final String NOME_DO_BREAKER_SAUDE = "canal-uzapi-autotic-saude";

    private static final int LIMITE_PADRAO_FOTO = 5 * 1024 * 1024;
    private static final int PROFUNDIDADE_MAXIMA_FOTO = 6;
    private static final int NOS_MAXIMOS_FOTO = 200;
    private static final Set<String> CAMPOS_FOTO = Set.of(
            "url", "picture", "pictureurl", "profilepicture", "profilepictureurl", "photourl",
            "profilepic", "avatar", "photo", "image", "link");

    private static final Logger log = LoggerFactory.getLogger(UzapiAutoticAdapter.class);

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final CircuitBreaker breakerMidia;
    private final CircuitBreaker breakerSaude;
    private final ArmazenamentoDeMidia armazenamento;
    private final ConversorDeAudio conversorDeAudio;
    private final int limiteRespostaFoto;
    private final java.util.Set<String> hostsDeFotoPermitidos;

    /** Construtor usado pelo Spring; o limite acompanha a configuração da captura de fotos. */
    @Autowired
    UzapiAutoticAdapter(
            RestClient.Builder builder,
            CanalProperties propriedades,
            ObjectMapper json,
            CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia armazenamento,
            ConversorDeAudio conversorDeAudio,
            @Value("${synapse.canal.foto-perfil.limite-bytes:5242880}") int limiteRespostaFoto,
            @Value("${synapse.canal.foto-perfil.hosts-permitidos:pps.whatsapp.net}") java.util.List<String> hostsPermitidos) {
        this.http = builder.baseUrl(propriedades.urlBase()).build();
        this.propriedades = propriedades;
        this.json = json;
        this.breaker = breakers.circuitBreaker(NOME_DO_BREAKER);
        this.breakerMidia = breakers.circuitBreaker(NOME_DO_BREAKER_MIDIA);
        this.breakerSaude = breakers.circuitBreaker(NOME_DO_BREAKER_SAUDE);
        this.armazenamento = armazenamento;
        this.conversorDeAudio = conversorDeAudio;
        if (limiteRespostaFoto < 1) {
            throw new IllegalArgumentException("limite de resposta da foto precisa ser positivo");
        }
        this.limiteRespostaFoto = limiteRespostaFoto;
        this.hostsDeFotoPermitidos = normalizarHostsDeFoto(hostsPermitidos);
    }

    /**
     * Hosts além do da UZAPI de onde a foto pode ser baixada. A UZAPI devolve a URL do CDN do próprio
     * WhatsApp (`pps.whatsapp.net`), não um link dela. Comparação por host exato: sem curinga, sem
     * sufixo e sem subdomínio implícito. Entrada que pareça URL, porta, credencial ou curinga derruba o
     * boot em vez de abrir a lista em silêncio.
     */
    private static java.util.Set<String> normalizarHostsDeFoto(java.util.List<String> brutos) {
        java.util.Set<String> hosts = new java.util.LinkedHashSet<>();
        if (brutos == null) {
            return hosts;
        }
        for (String bruto : brutos) {
            String host = bruto == null ? "" : bruto.trim().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                continue;
            }
            if (host.matches("[0-9.]+")
                    || !host.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")) {
                throw new IllegalArgumentException(
                        "synapse.canal.foto-perfil.hosts-permitidos aceita só nomes de host exatos, sem esquema, porta, caminho nem curinga");
            }
            hosts.add(host);
        }
        return java.util.Collections.unmodifiableSet(hosts);
    }

    /** Compatibilidade dos testes do módulo de atendimento, que não carregam o app. */
    UzapiAutoticAdapter(
            RestClient.Builder builder,
            CanalProperties propriedades,
            ObjectMapper json,
            CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia armazenamento,
            ConversorDeAudio conversorDeAudio) {
        this(builder, propriedades, json, breakers, armazenamento, conversorDeAudio, LIMITE_PADRAO_FOTO, java.util.List.of());
    }

    @Override
    public String provedor() {
        return PROVEDOR;
    }

    /** Nao e a API oficial da Meta; nao ha janela de 24h documentada para este fornecedor. */
    @Override
    public boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora) {
        return true;
    }

    @Override
    public boolean exigeTemplateForaDaJanela() {
        return false;
    }

    /**
     * {@code GET /{version}/{phone_number_id}/instance} — "Consultar uma instancia" no
     * Swagger oficial, tag "Instancias". Confirmado literalmente no spec, nao suposto.
     */
    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (credencialIncompleta()) {
            return AutenticacaoDoCanal.recusada("credencial ou identificador do canal ausente");
        }
        try {
            return breakerSaude.executeSupplier(this::consultarInstancia);
        } catch (CallNotPermittedException e) {
            return AutenticacaoDoCanal.recusada("circuit breaker do provedor aberto");
        } catch (RestClientResponseException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor recusou a credencial com HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.warn(
                    "Falha ao verificar autenticacao do canal; a sonda continua, o trafego nao e afetado.",
                    e);
            return AutenticacaoDoCanal.recusada(
                    "provedor indisponivel: " + e.getClass().getSimpleName());
        }
    }

    private AutenticacaoDoCanal consultarInstancia() {
        http.get()
                .uri(
                        "/{version}/{phone_number_id}/instance",
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .toBodilessEntity();
        return AutenticacaoDoCanal.aceita();
    }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        if (credencialIncompleta()) {
            return ResultadoDeEnvio.Recusado.permanente("configuracao do canal incompleta");
        }
        try {
            if (envio.conteudo() instanceof ConteudoDeEnvio.MensagemMidia midia) {
                MidiaParaUpload midiaParaUpload = prepararMidiaParaUpload(envio, midia);
                return breaker.executeSupplier(() -> enviarMidia(envio, midia, midiaParaUpload));
            }
            return breaker.executeSupplier(() -> enviarNoBreaker(envio));
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("circuit breaker aberto para " + PROVEDOR);
        } catch (FalhaNaConversaoDeAudioException e) {
            return ResultadoDeEnvio.Recusado.permanente(
                    "nao foi possivel converter o audio para um formato reproduzivel no WhatsApp");
        } catch (RespostaInvalidaException e) {
            return ResultadoDeEnvio.Recusado.permanente(e.getMessage());
        } catch (RestClientResponseException e) {
            return traduzirErro(e);
        }
        // Timeout, DNS, conexao recusada sobem como excecao de proposito: o breaker precisa
        // conta-las como falha para chegar a abrir.
    }

    private ResultadoDeEnvio enviarNoBreaker(Envio envio) {
        return switch (envio.conteudo()) {
            case ConteudoDeEnvio.MensagemLivre livre -> enviarTexto(envio, livre);
            case ConteudoDeEnvio.MensagemMidia midia -> throw new IllegalStateException(
                    "midia deveria ser preparada antes do disjuntor do provedor");
            // Nao ha schema de corpo para "template" nos doze variantes de POST .../messages do
            // Swagger; o valor so existe no enum solto de "type". Sem endpoint confirmado de
            // gestao de template, recusa sem HTTP em vez de arriscar um envio que a Uzapi/Autotic
            // nunca documentou.
            case ConteudoDeEnvio.MensagemTemplate template ->
                    ResultadoDeEnvio.Recusado.permanente("uzapi-autotic nao gerencia templates");
        };
    }

    private ResultadoDeEnvio enviarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre livre) {
        ObjectNode corpo = corpoBase(envio, "text");
        corpo.putObject("text").put("body", livre.texto());
        return postarMensagem(corpo);
    }

    /**
     * Upload em duas etapas, igual ao padrao da Meta: sobe os bytes para {@code .../media},
     * referencia o {@code id} devolvido no corpo de {@code .../messages}.
     *
     * <p>O contrato tambem aceita {@code link} (URL publica) no lugar de {@code id}, mas
     * {@link ConteudoDeEnvio.MensagemMidia#referenciaStorage()} e documentado como chave opaca do
     * bucket proprio, "nunca bytes nem URL" — nao existe hoje um caller deste adaptador que
     * entregue uma URL publica. Implementar deteccao de URL agora seria ramo morto, sem chamador
     * real para testar contra; fica registrado aqui e em {@code docs/38} para quando essa premissa
     * do dominio mudar.
     */
    private ResultadoDeEnvio enviarMidia(
            Envio envio, ConteudoDeEnvio.MensagemMidia midia, MidiaParaUpload midiaParaUpload) {
        String tipo = tipoDoProvedor(midia.tipo());
        String mediaId = breakerMidia.executeSupplier(() -> subirMidia(midiaParaUpload));
        ObjectNode corpo = corpoBase(envio, tipo);
        ObjectNode conteudo = corpo.putObject(tipo);
        conteudo.put("id", mediaId);
        // Confirmado no Swagger: Image/Video sao "CaptionedLinkMessage" e Document e
        // "CaptionedFileMessage" — todos com campo caption. Audio e "LinkMessage": SEM caption.
        // Enviar o campo mesmo assim arriscaria rejeicao silenciosa de um campo nao documentado.
        if (midia.tipo() != TipoMensagem.AUDIO
                && midia.legenda() != null
                && !midia.legenda().isBlank()) {
            conteudo.put("caption", midia.legenda());
        }
        if (midia.tipo() == TipoMensagem.DOCUMENTO) {
            String nome = campoDeMetadados(midia.metadados(), "nome");
            if (nome != null && !nome.isBlank()) {
                conteudo.put("filename", nome);
            }
        }
        return postarMensagem(corpo);
    }

    private ResultadoDeEnvio postarMensagem(ObjectNode corpo) {
        String resposta = http.post()
                .uri(
                        "/{version}/{phone_number_id}/messages",
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(String.class);
        return interpretarAceite(resposta, corpo.path("to").asText());
    }

    private ObjectNode corpoBase(Envio envio, String tipo) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("to", somenteDigitos(envio.telefoneDestino()));
        corpo.put("type", tipo);
        if (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()) {
            corpo.putObject("context").put("message_id", envio.contextoWamid());
        }
        return corpo;
    }

    /**
     * Prepara os bytes antes do disjuntor da Uzapi: falha local de conversao nao pode degradar
     * nem o envio do provedor nem o download de midias recebidas.
     */
    private MidiaParaUpload prepararMidiaParaUpload(
            Envio envio, ConteudoDeEnvio.MensagemMidia midia) {
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        String mimetype = campoDeMetadados(midia.metadados(), "mimetype");
        boolean gravacaoDoComposer = booleanoDeMetadados(midia.metadados(), "gravacaoDoComposer");
        if (gravacaoDoComposer
                && midia.tipo() == TipoMensagem.AUDIO
                && (!ehOggOpus(mimetype) || !ValidadorDeOggOpus.ehValido(bytes))) {
            throw new FalhaNaConversaoDeAudioException(
                    "gravacao do composer recuperada do storage nao e OGG/Opus valida");
        }
        if (gravacaoDoComposer && midia.tipo() == TipoMensagem.AUDIO) {
            ResumoSeguroDeMidia resumo = ResumoSeguroDeMidia.de(bytes);
            log.info(
                    "audio do composer recuperado para upload Uzapi: mensagemId={}, tamanho={}, mimetype={}, sha256={}, oggOpusValido={}",
                    envio.mensagemId(),
                    resumo.tamanho(),
                    mimetype,
                    resumo.sha256(),
                    true);
        }
        if (midia.tipo() == TipoMensagem.AUDIO && IsoBmffAudioOnly.ehFragmentado(bytes)) {
            ConversorDeAudio.Resultado convertido =
                    conversorDeAudio.converterParaAacAdts(bytes, mimetype);
            if (!"audio/aac".equals(MetaCloudMidiaUpload.tipoPrincipal(convertido.mimetype()))
                    || convertido.conteudo().length == 0) {
                throw new FalhaNaConversaoDeAudioException(
                        "conversor de audio nao produziu AAC/ADTS valido");
            }
            bytes = convertido.conteudo();
            mimetype = convertido.mimetype();
            log.info(
                    "audio ISO-BMFF fragmentado convertido para AAC/ADTS no worker de entrega ({} bytes)",
                    bytes.length);
        }
        String tipoDoArquivo = MetaCloudMidiaUpload.tipoDoArquivo(
                MetaCloudMidiaUpload.tipoDoCampo(mimetype, midia.tipo()));
        String nome = MetaCloudMidiaUpload.nomeDoArquivo(
                campoDeMetadados(midia.metadados(), "nome"), tipoDoArquivo);
        if (gravacaoDoComposer && midia.tipo() == TipoMensagem.AUDIO) {
            ResumoSeguroDeMidia resumo = ResumoSeguroDeMidia.de(bytes);
            log.info(
                    "audio do composer efetivamente enviado à Uzapi: mensagemId={}, tamanho={}, mimetype={}, sha256={}, uploadTipo={}",
                    envio.mensagemId(),
                    resumo.tamanho(),
                    mimetype,
                    resumo.sha256(),
                    tipoDoArquivo);
        }
        return new MidiaParaUpload(bytes, tipoDoArquivo, nome);
    }

    private static boolean ehOggOpus(String mimetype) {
        String principal = MetaCloudMidiaUpload.tipoPrincipal(mimetype);
        return "audio/ogg".equals(principal) || "audio/opus".equals(principal);
    }

    /** {@code POST .../media}, multipart com {@code file} + {@code messaging_product}, confirmado no Swagger. */
    private String subirMidia(MidiaParaUpload midia) {

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("messaging_product", "whatsapp");
        multipart.part(
                "file",
                new ByteArrayResource(midia.bytes()) {
                    @Override
                    public String getFilename() {
                        return midia.nome();
                    }
                },
                MetaCloudMidiaUpload.contentType(midia.tipoDoArquivo()));

        String resposta = http.post()
                .uri(
                        "/{version}/{phone_number_id}/media",
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                // A Uzapi so interpreta os campos file/messaging_product quando o request declara
                // multipart/form-data. Sem este cabecalho, o conversor pode escolher um formato
                // diferente e todas as categorias de midia falham antes do POST /messages.
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart.build())
                .retrieve()
                .body(String.class);
        return idDoUpload(resposta);
    }

    private record MidiaParaUpload(byte[] bytes, String tipoDoArquivo, String nome) {}

    /**
     * Traduz o JSON de aceite sem deixar uma resposta invalida passar por sucesso.
     *
     * <p>Confirmado em duas fontes independentes (Swagger oficial e a pagina de exemplos de
     * {@code uzapi.com.br/docs}): 2xx com corpo real devolve {@code status:"success"},
     * {@code queueId} e {@code messageId} (ambos identificadores internos da fila/instancia —
     * nunca o wamid) e {@code messages[0].id}, que e o id real gerado pelo WhatsApp. So esse ultimo
     * vira {@code idExterno}.
     */
    private ResultadoDeEnvio interpretarAceite(String resposta, String destinoEnviado) {
        JsonNode raiz = lerJson(resposta, "resposta do envio");
        boolean sucesso =
                "success".equalsIgnoreCase(raiz.path("status").asText()) && !raiz.has("error");
        JsonNode mensagens = raiz.path("messages");
        String id = mensagens.isArray() && !mensagens.isEmpty()
                ? mensagens.get(0).path("id").asText("").trim()
                : "";
        if (!sucesso || id.isBlank()) {
            throw new RespostaInvalidaException(
                    "resposta de sucesso do provedor invalida: id da mensagem ausente");
        }
        return new ResultadoDeEnvio.Aceito(id, enderecoDoProvedor(raiz, destinoEnviado));
    }

    /**
     * Guarda o endereco que a Uzapi associou ao destinatario, se a resposta o trouxer.
     * {@code contacts[].input} e opcional; quando presente, precisa corresponder ao {@code to} enviado.
     */
    private String enderecoDoProvedor(JsonNode raiz, String destinoEnviado) {
        JsonNode contatos = raiz.path("contacts");
        if (!contatos.isArray() || contatos.isEmpty()) {
            return null;
        }
        JsonNode contato = contatos.get(0);
        String endereco = contato.path("wa_id").asText("").trim();
        if (!endereco.matches("[0-9]{8,15}")) {
            return null;
        }
        String entrada = contato.path("input").asText("").trim();
        if (!entrada.isEmpty() && !somenteDigitos(entrada).equals(destinoEnviado)) {
            return null;
        }
        return endereco;
    }

    private String idDoUpload(String resposta) {
        JsonNode raiz = lerJson(resposta, "resposta do upload");
        String id = raiz.path("id").asText("").trim();
        if (id.isBlank() || raiz.has("error")) {
            throw new RespostaInvalidaException(
                    "resposta de sucesso do upload invalida: id de midia ausente");
        }
        return id;
    }

    private JsonNode lerJson(String resposta, String operacao) {
        if (resposta == null || resposta.isBlank()) {
            throw new RespostaInvalidaException(
                    "resposta de sucesso do provedor invalida: corpo vazio em " + operacao);
        }
        try {
            return json.readTree(resposta);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new RespostaInvalidaException(
                    "resposta de sucesso do provedor invalida: JSON ilegivel em " + operacao);
        }
    }

    /**
     * Sem confirmacao documentada de rate limit para este fornecedor — a referencia de producao
     * real (Clinica-CRM-FMNA/UazapClient) tambem nao faz retry automatico por falta de chave de
     * idempotencia comprovada. Todo 4xx e permanente aqui, sem a excecao de 429 que a Meta tem: nao
     * ha base documentada para tratar 429 como "tente de novo" para este provedor especifico.
     */
    private ResultadoDeEnvio traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        boolean defeitoDoPedido = status >= 400 && status < 500;
        String detalhe = "provedor "
                + (defeitoDoPedido ? "recusou a requisicao" : "indisponivel")
                + " com HTTP "
                + status;
        String mensagem = mensagemDaRespostaDeErro(e.getResponseBodyAsString());
        if (!mensagem.isBlank()) {
            detalhe += ": " + mensagem;
        }
        return defeitoDoPedido
                ? ResultadoDeEnvio.Recusado.permanente(detalhe)
                : ResultadoDeEnvio.Recusado.temporario(detalhe);
    }

    /** Preserva somente o campo documentado {@code message}, sem expor o corpo bruto do provedor. */
    private String mensagemDaRespostaDeErro(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "";
        }
        try {
            JsonNode mensagem = json.readTree(corpo).path("message");
            if (mensagem.isTextual()) {
                return mensagem.asText().trim();
            }
            if (mensagem.isArray()) {
                StringJoiner partes = new StringJoiner("; ");
                for (JsonNode item : mensagem) {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        partes.add(item.asText().trim());
                    }
                }
                return partes.toString();
            }
        } catch (JsonProcessingException | RuntimeException ignorada) {
            // A resposta pode estar vazia ou não ser JSON; o status HTTP ainda é informativo.
        }
        return "";
    }

    private boolean credencialIncompleta() {
        return vazio(propriedades.token())
                || vazio(propriedades.numeroPrincipal())
                || vazio(propriedades.versaoApi());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String somenteDigitos(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            throw new RespostaInvalidaException("numero de destino ausente");
        }
        return digitos;
    }

    /** Mapeamento confirmado no Swagger: os doze tipos de {@code POST .../messages}. */
    static String tipoDoProvedor(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> "image";
            case AUDIO -> "audio";
            case DOCUMENTO -> "document";
            case VIDEO -> "video";
            case TEXTO -> throw new IllegalArgumentException("TEXTO nao e um tipo de midia");
            case LOCALIZACAO, CONTATO ->
                    throw new IllegalArgumentException(tipo + " nao e midia transferida");
            case BOTOES, LISTA -> throw new IllegalArgumentException("mensagem interativa nao e midia");
        };
    }

    private String campoDeMetadados(String metadadosJson, String campo) {
        if (metadadosJson == null || metadadosJson.isBlank()) {
            return null;
        }
        try {
            JsonNode valor = json.readTree(metadadosJson).path(campo);
            return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private boolean booleanoDeMetadados(String metadadosJson, String campo) {
        if (metadadosJson == null || metadadosJson.isBlank()) {
            return false;
        }
        try {
            JsonNode valor = json.readTree(metadadosJson).path(campo);
            return valor.isBoolean() && valor.booleanValue();
        } catch (JsonProcessingException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        if (vazio(midiaIdExterno)) {
            throw new IllegalArgumentException("id de midia recebido ausente");
        }
        try {
            return breakerMidia.executeSupplier(() -> buscarMidiaRecebida(midiaIdExterno));
        } catch (CallNotPermittedException breakerAberto) {
            throw new ProvedorTemporariamenteIndisponivelException(
                    "circuit breaker aberto para " + PROVEDOR + "; midia " + midiaIdExterno
                            + " sera retentada",
                    breakerAberto);
        } catch (EtapaDaMidiaRecebidaFalhou falha) {
            String motivo = "midia recebida " + PROVEDOR + ": " + falha.getMessage()
                    + "; midiaId=" + midiaIdExterno;
            if (falha.removidaNoResolvedor()) {
                // E218 (Bloco 1): 410 no resolvedor e sobre o proprio mediaId. Repetir a mesma
                // consulta nao muda o resultado; o processador registra sem arquivo na hora.
                throw new MidiaRecebidaRemovidaNoProvedorException(motivo);
            }
            // A conta em producao responde 404 quando o arquivo ainda nao esta disponivel. Isso
            // nao e uma credencial invalida: a fila deve retentar com backoff, sem guardar o corpo
            // da resposta (que pode conter URL temporaria ou outros dados do provedor). O 410 do
            // download tambem fica aqui: pode ser so a URL temporaria vencida, e a proxima
            // tentativa pede uma URL nova ao resolvedor.
            throw new MidiaRecebidaTemporariamenteIndisponivelException(motivo);
        }
    }

    /**
     * Consulta a foto de um contato usando o contrato de {@code contacts/getPicture} da UZAPI.
     *
     * <p>A operação fica no adapter porque a resposta do fornecedor não tem schema de resposta no
     * Swagger (HTTP 201 sem corpo descrito). O domínio recebe somente bytes e mimetype. Erros 4xx
     * significam que não há foto disponível para este contato; indisponibilidade de rede e 5xx
     * sobem como temporárias para o worker assíncrono, sem afetar o webhook de mensagens.
     */
    @Override
    public Optional<MidiaRecebida> buscarFotoDePerfil(String telefone) {
        if (credencialIncompleta() || vazio(telefone)) {
            return semFoto("CREDENCIAL_OU_TELEFONE_AUSENTE", "");
        }
        try {
            return breakerMidia.executeSupplier(() -> {
                try {
                    return consultarFotoDePerfil(somenteDigitos(telefone));
                } catch (RestClientResponseException resposta) {
                    // 4xx do getPicture representam ausência/indisponibilidade do perfil, não uma
                    // falha do canal inteiro. Não deixe respostas esperadas abrirem o breaker.
                    int status = resposta.getStatusCode().value();
                    if (status != 429 && status < 500) {
                        return semFoto("HTTP_" + status, "");
                    }
                    throw resposta;
                }
            });
        } catch (CallNotPermittedException breakerAberto) {
            throw new ProvedorTemporariamenteIndisponivelException(
                    "circuit breaker aberto para " + PROVEDOR + " ao consultar foto de perfil",
                    breakerAberto);
        } catch (RestClientResponseException resposta) {
            int status = resposta.getStatusCode().value();
            if (status >= 500) {
                throw new ProvedorTemporariamenteIndisponivelException(
                        "provedor " + PROVEDOR + " indisponivel ao consultar foto de perfil; HTTP " + status,
                        resposta);
            }
            return Optional.empty();
        } catch (org.springframework.web.client.RestClientException temporaria) {
            throw new ProvedorTemporariamenteIndisponivelException(
                    "provedor " + PROVEDOR + " indisponivel ao consultar foto de perfil",
                    temporaria);
        }
    }

    private Optional<MidiaRecebida> consultarFotoDePerfil(String telefone) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("type", "contacts");
        corpo.put("action", "getPicture");
        corpo.putObject("contacts").put("to", telefone);

        ResponseEntity<byte[]> resposta = http.post()
                .uri(
                        "/{version}/{phone_number_id}/contacts",
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange((request, response) -> {
                    byte[] bytes;
                    try {
                        bytes = response.getBody() == null
                                ? new byte[0]
                                : response.getBody().readNBytes(limiteRespostaFoto + 1);
                    } catch (IOException erro) {
                        throw new org.springframework.web.client.RestClientException(
                                "falha ao ler resposta da foto de perfil", erro);
                    }
                    MediaType contentType = response.getHeaders().getContentType();
                    return ResponseEntity.status(response.getStatusCode())
                            .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                            .body(bytes);
                });
        if (!resposta.getStatusCode().is2xxSuccessful()) {
            throw new RestClientResponseException(
                    "UZAPI respondeu HTTP " + resposta.getStatusCode().value(),
                    resposta.getStatusCode().value(),
                    resposta.getStatusCode().toString(),
                    resposta.getHeaders(),
                    resposta.getBody(),
                    StandardCharsets.UTF_8);
        }
        byte[] body = resposta.getBody() == null ? new byte[0] : resposta.getBody();
        String contentType = resposta.getHeaders().getContentType() == null
                ? ""
                : resposta.getHeaders().getContentType().toString();
        if (body.length == 0 || body.length > limiteRespostaFoto) {
            return semFoto(
                    "CORPO_VAZIO_OU_ACIMA_DO_LIMITE",
                    "status=" + resposta.getStatusCode().value() + ", bytes=" + body.length
                            + ", contentType=" + contentType);
        }
        if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            log.info("UZAPI devolveu foto de perfil; origem=binario, bytes={}, mime={}", body.length, contentType);
            return Optional.of(new MidiaRecebida(body, contentType));
        }
        return interpretarRespostaDaFoto(body, contentType);
    }

    private Optional<MidiaRecebida> interpretarRespostaDaFoto(byte[] body, String contentType) {
        JsonNode raiz;
        try {
            raiz = json.readTree(body);
        } catch (IOException | RuntimeException invalido) {
            return semFoto("CORPO_NAO_E_JSON", "contentType=" + contentType + ", bytes=" + body.length);
        }
        String candidato = procurarCampoDeFoto(raiz, 0, new int[] {NOS_MAXIMOS_FOTO});
        if (candidato == null || candidato.isBlank()) {
            return semFoto("SEM_CAMPO_DE_FOTO", "chaves=" + nomesDosCampos(raiz) + ", bytes=" + body.length);
        }
        if (candidato.regionMatches(true, 0, "data:", 0, 5)) {
            return decodificarDataUri(candidato);
        }
        if (pareceBase64(candidato)) {
            try {
                byte[] bytes = Base64.getMimeDecoder().decode(candidato);
                if (bytes.length == 0 || bytes.length > limiteRespostaFoto) {
                    return semFoto("BASE64_VAZIO_OU_ACIMA_DO_LIMITE", "bytes=" + bytes.length);
                }
                log.info("UZAPI devolveu foto de perfil; origem=base64, bytes={}", bytes.length);
                return Optional.of(new MidiaRecebida(bytes, "image/jpeg"));
            } catch (IllegalArgumentException ignorado) {
                return semFoto("BASE64_INVALIDO", "");
            }
        }
        return baixarFotoTemporaria(candidato);
    }

    private Optional<MidiaRecebida> decodificarDataUri(String valor) {
        int separador = valor.indexOf(",");
        if (separador <= 5 || !valor.regionMatches(true, separador - 7, ";base64", 0, 7)) {
            return semFoto("DATA_URI_NAO_BASE64", "");
        }
        String mime = valor.substring(5, separador).split(";", 2)[0].trim();
        if (!mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return semFoto("DATA_URI_NAO_IMAGEM", "mime=" + mime);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(valor.substring(separador + 1));
            if (bytes.length == 0 || bytes.length > limiteRespostaFoto) {
                return semFoto("DATA_URI_VAZIO_OU_ACIMA_DO_LIMITE", "bytes=" + bytes.length);
            }
            log.info("UZAPI devolveu foto de perfil; origem=data-uri, bytes={}, mime={}", bytes.length, mime);
            return Optional.of(new MidiaRecebida(bytes, mime));
        } catch (IllegalArgumentException ignorado) {
            return semFoto("DATA_URI_INVALIDO", "");
        }
    }

    private Optional<MidiaRecebida> baixarFotoTemporaria(String valor) {
        URI uri;
        try {
            uri = new URI(valor.trim()).normalize();
        } catch (URISyntaxException | RuntimeException invalida) {
            return semFoto("URL_INVALIDA", "");
        }
        URI base;
        try {
            base = new URI(propriedades.urlBase());
        } catch (URISyntaxException | RuntimeException invalida) {
            return semFoto("URL_BASE_INVALIDA", "");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || base.getHost() == null
                || !hostPermitidoParaFoto(uri.getHost(), base.getHost())) {
            // Só esquema e host: o caminho e a query carregam a assinatura temporária.
            return semFoto(
                    "URL_RECUSADA",
                    "esquema=" + uri.getScheme() + ", host=" + uri.getHost() + ", hostPermitido=" + base.getHost());
        }
        ResponseEntity<byte[]> resposta = http.get()
                .uri(uri)
                .header("Accept", "image/jpeg,image/png,image/webp")
                .exchange((request, response) -> {
                    byte[] bytes;
                    try {
                        bytes = response.getBody() == null
                                ? new byte[0]
                                : response.getBody().readNBytes(limiteRespostaFoto + 1);
                    } catch (IOException erro) {
                        throw new org.springframework.web.client.RestClientException(
                                "falha ao ler resposta da foto de perfil", erro);
                    }
                    MediaType contentType = response.getHeaders().getContentType();
                    return ResponseEntity.status(response.getStatusCode())
                            .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                            .body(bytes);
                });
        if (resposta.getStatusCode().value() == 429 || resposta.getStatusCode().value() >= 500) {
            throw new ProvedorTemporariamenteIndisponivelException(
                    "provedor " + PROVEDOR + " indisponivel ao baixar foto de perfil; HTTP "
                            + resposta.getStatusCode().value());
        }
        if (!resposta.getStatusCode().is2xxSuccessful()) {
            return semFoto("DOWNLOAD_HTTP_" + resposta.getStatusCode().value(), "host=" + uri.getHost());
        }
        byte[] conteudo = resposta.getBody() == null ? new byte[0] : resposta.getBody();
        String tipo = resposta.getHeaders().getContentType() == null
                ? ""
                : resposta.getHeaders().getContentType().toString();
        if (conteudo.length == 0 || conteudo.length > limiteRespostaFoto) {
            return semFoto("DOWNLOAD_VAZIO_OU_ACIMA_DO_LIMITE", "bytes=" + conteudo.length + ", contentType=" + tipo);
        }
        if (!tipo.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return semFoto("DOWNLOAD_NAO_IMAGEM", "contentType=" + tipo + ", bytes=" + conteudo.length);
        }
        log.info("UZAPI devolveu foto de perfil; origem=url, bytes={}, mime={}", conteudo.length, tipo);
        return Optional.of(new MidiaRecebida(conteudo, tipo));
    }

    private boolean hostPermitidoParaFoto(String host, String hostDaUzapi) {
        return host.equalsIgnoreCase(hostDaUzapi) || hostsDeFotoPermitidos.contains(host.toLowerCase(Locale.ROOT));
    }

    private String procurarCampoDeFoto(JsonNode no, int profundidade, int[] orcamento) {
        if (no == null || profundidade > PROFUNDIDADE_MAXIMA_FOTO || orcamento[0]-- <= 0) {
            return null;
        }
        if (no.isObject()) {
            Iterator<java.util.Map.Entry<String, JsonNode>> campos = no.fields();
            while (campos.hasNext()) {
                var campo = campos.next();
                String chave = campo.getKey().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                JsonNode valor = campo.getValue();
                if (valor.isTextual() && CAMPOS_FOTO.contains(chave)) {
                    return valor.asText();
                }
                String encontrado = procurarCampoDeFoto(valor, profundidade + 1, orcamento);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (no.isArray()) {
            for (JsonNode item : no) {
                String encontrado = procurarCampoDeFoto(item, profundidade + 1, orcamento);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    /**
     * Registra por que uma consulta de foto terminou sem imagem. A UZAPI não documenta o corpo de
     * resposta do {@code getPicture}; sem isto, "sem foto" era indistinguível de "formato que o ACL
     * não reconhece" ou "host recusado". Só entram códigos, status, tipo de conteúdo, tamanho, NOMES
     * de campo e o host: nunca telefone, token, valor de campo nem caminho/query da URL.
     */
    private Optional<MidiaRecebida> semFoto(String motivo, String detalhe) {
        log.info("UZAPI sem foto de perfil; motivo={}{}", motivo, detalhe.isEmpty() ? "" : ", " + detalhe);
        return Optional.empty();
    }

    /** Nomes (não valores) dos campos do JSON, em profundidade limitada, para diagnosticar o formato. */
    private static String nomesDosCampos(JsonNode raiz) {
        java.util.List<String> nomes = new java.util.ArrayList<>();
        coletarNomes(raiz, "", 0, nomes);
        return nomes.isEmpty() ? "[]" : String.join(",", nomes);
    }

    private static void coletarNomes(JsonNode no, String prefixo, int profundidade, java.util.List<String> saida) {
        if (no == null || profundidade > 3 || saida.size() >= 30) {
            return;
        }
        if (no.isObject()) {
            Iterator<java.util.Map.Entry<String, JsonNode>> campos = no.fields();
            while (campos.hasNext() && saida.size() < 30) {
                var campo = campos.next();
                // Uma chave que seja um telefone/identificador longo não pode vazar no log.
                String nome = campo.getKey().replaceAll("\\d{8,}", "#");
                nome = nome.length() > 40 ? nome.substring(0, 40) : nome;
                saida.add(prefixo + nome);
                coletarNomes(campo.getValue(), prefixo + nome + ".", profundidade + 1, saida);
            }
        } else if (no.isArray() && !no.isEmpty()) {
            coletarNomes(no.get(0), prefixo + "[].", profundidade + 1, saida);
        }
    }

    private static boolean pareceBase64(String valor) {
        String compacto = valor.replaceAll("\\s", "");
        return compacto.length() >= 200 && compacto.matches("[A-Za-z0-9+/]+={0,2}");
    }

    /**
     * O endpoint oficial "Retrieve Media URL" usa somente a versao e o identificador da midia.
     * O {@code phone_number_id} fica reservado as rotas de instancia, upload e envio. A URL
     * devolvida ja e autorizada pelo fornecedor; nao enviamos o Bearer novamente para um host
     * externo e evitamos vazar a credencial do canal.
     */
    private MidiaRecebida buscarMidiaRecebida(String midiaIdExterno) {
        String resposta;
        try {
            resposta = http.get()
                    .uri(
                            "/{version}/{mediaId}",
                            propriedades.versaoApi(),
                            midiaIdExterno)
                    .header("Authorization", "Bearer " + propriedades.token())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException falha) {
            throw EtapaDaMidiaRecebidaFalhou.noResolvedor(falha.getStatusCode().value());
        }
        // 200 sem `url` utilizavel (corpo vazio, ilegivel ou sem o campo) e o provedor ainda nao ter
        // o arquivo, como o 404: segue o ciclo de retentativa e, vencido o prazo, o anexo entra na
        // conversa como indisponivel. Na falha generica a linha esgotava em ~75 s e o anexo sumia.
        String url;
        try {
            url = lerJson(resposta, "resposta da midia recebida").path("url").asText("").trim();
        } catch (RespostaInvalidaException invalida) {
            throw EtapaDaMidiaRecebidaFalhou.semConteudoNoResolvedor();
        }
        if (url.isBlank()) {
            throw EtapaDaMidiaRecebidaFalhou.semConteudoNoResolvedor();
        }

        URI destino = URI.create(url);
        ResponseEntity<byte[]> respostaDosBytes;
        try {
            respostaDosBytes = http.get()
                    .uri(destino)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException falha) {
            // So o host: caminho e query de URL temporaria podem carregar assinatura ou token.
            throw EtapaDaMidiaRecebidaFalhou.noDownload(
                    falha.getStatusCode().value(), hostOuDesconhecido(destino));
        }
        byte[] bytes = respostaDosBytes.getBody();
        if (bytes == null || bytes.length == 0) {
            // Nunca grava objeto vazio no storage: a mensagem ficaria "com arquivo" que nao abre.
            throw EtapaDaMidiaRecebidaFalhou.semBytesNoDownload(hostOuDesconhecido(destino));
        }
        MediaType contentType = respostaDosBytes.getHeaders().getContentType();
        String mimetype = contentType == null ? "application/octet-stream" : contentType.toString();
        return new MidiaRecebida(bytes, mimetype);
    }

    private static String hostOuDesconhecido(URI destino) {
        String host = destino.getHost();
        return host == null || host.isBlank() ? "desconhecido" : host;
    }

    /**
     * Resposta HTTP de erro em uma das duas chamadas do recebimento de midia, ja com a etapa e sem
     * corpo, URL ou credencial. Lancada dentro do circuit breaker, que a registra como falha do
     * mesmo jeito que registrava a {@link RestClientResponseException} original.
     */
    private static final class EtapaDaMidiaRecebidaFalhou extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final boolean removidaNoResolvedor;

        private EtapaDaMidiaRecebidaFalhou(String descricao, boolean removidaNoResolvedor) {
            super(descricao);
            this.removidaNoResolvedor = removidaNoResolvedor;
        }

        static EtapaDaMidiaRecebidaFalhou noResolvedor(int status) {
            return new EtapaDaMidiaRecebidaFalhou(
                    "etapa=resolvedor respondeu HTTP " + status, status == HttpStatus.GONE.value());
        }

        /** O 410 aqui nunca e terminal: pode ser so a URL temporaria vencida (E218). */
        static EtapaDaMidiaRecebidaFalhou noDownload(int status, String host) {
            return new EtapaDaMidiaRecebidaFalhou(
                    "etapa=download respondeu HTTP " + status + "; host=" + host, false);
        }

        /** 200 do resolvedor sem {@code url} utilizavel; nunca carrega o corpo da resposta. */
        static EtapaDaMidiaRecebidaFalhou semConteudoNoResolvedor() {
            return new EtapaDaMidiaRecebidaFalhou("etapa=resolvedor respondeu sem url utilizavel", false);
        }

        /** 200 do download sem nenhum byte; so o host, como nas demais falhas do download. */
        static EtapaDaMidiaRecebidaFalhou semBytesNoDownload(String host) {
            return new EtapaDaMidiaRecebidaFalhou(
                    "etapa=download respondeu sem bytes; host=" + host, false);
        }

        boolean removidaNoResolvedor() {
            return removidaNoResolvedor;
        }
    }

    /** 2xx do provedor, mas o corpo nao confirma sucesso — sempre recusa permanente. */
    private static final class RespostaInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        RespostaInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
