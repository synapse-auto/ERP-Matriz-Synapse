package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

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
 * em 09/09/2026: {@code GET .../instance} para saude, {@code POST .../messages} para envio,
 * {@code POST .../media} (multipart) para upload previo de midia. Nao existe endpoint de gestao de
 * template no Swagger — o valor {@code "template"} aparece apenas no enum solto do campo
 * {@code type}, sem nenhum schema de corpo correspondente nos doze variantes documentados
 * (Text/Image/Audio/Video/Document/Reaction/Location/Contacts/Poll/Sticker/Revoke/Interactive).
 *
 * <p>Recebimento nao foi investigado nesta etapa: {@link #baixarMidiaRecebida} recusa, e nenhum
 * {@code TradutorDeCanal} foi criado. Ligar {@code synapse.canal.whatsapp.provedor=uzapi-autotic}
 * antes disso falha a inicializacao do Spring inteira — {@link SeletorDeCanalGateway} exige um
 * {@code TradutorDeCanal} para a mesma chave, de proposito, e nao ha um.
 */
@Component
class UzapiAutoticAdapter implements CanalGateway {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. Nunca "uazapi" — ver javadoc da classe. */
    static final String PROVEDOR = "uzapi-autotic";

    private static final String NOME_DO_BREAKER = "canal-uzapi-autotic";
    private static final String NOME_DO_BREAKER_MIDIA = "canal-uzapi-autotic-midia";
    private static final String NOME_DO_BREAKER_SAUDE = "canal-uzapi-autotic-saude";

    private static final Logger log = LoggerFactory.getLogger(UzapiAutoticAdapter.class);

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final CircuitBreaker breakerMidia;
    private final CircuitBreaker breakerSaude;
    private final ArmazenamentoDeMidia armazenamento;

    UzapiAutoticAdapter(
            RestClient.Builder builder,
            CanalProperties propriedades,
            ObjectMapper json,
            CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia armazenamento) {
        this.http = builder.baseUrl(propriedades.urlBase()).build();
        this.propriedades = propriedades;
        this.json = json;
        this.breaker = breakers.circuitBreaker(NOME_DO_BREAKER);
        this.breakerMidia = breakers.circuitBreaker(NOME_DO_BREAKER_MIDIA);
        this.breakerSaude = breakers.circuitBreaker(NOME_DO_BREAKER_SAUDE);
        this.armazenamento = armazenamento;
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
     * {@code GET /{username}/{version}/{phone_number_id}/instance} — "Consultar uma instancia" no
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
                        "/{username}/{version}/{phone_number_id}/instance",
                        propriedades.usuarioApi(),
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
            return breaker.executeSupplier(() -> enviarNoBreaker(envio));
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("circuit breaker aberto para " + PROVEDOR);
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
            case ConteudoDeEnvio.MensagemMidia midia -> enviarMidia(envio, midia);
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
    private ResultadoDeEnvio enviarMidia(Envio envio, ConteudoDeEnvio.MensagemMidia midia) {
        String tipo = tipoDoProvedor(midia.tipo());
        String mediaId = breakerMidia.executeSupplier(() -> subirMidia(midia));
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
                        "/{username}/{version}/{phone_number_id}/messages",
                        propriedades.usuarioApi(),
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(String.class);
        return interpretarAceite(resposta);
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

    /** {@code POST .../media}, multipart com {@code file} + {@code messaging_product}, confirmado no Swagger. */
    private String subirMidia(ConteudoDeEnvio.MensagemMidia midia) {
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        String mimetype = campoDeMetadados(midia.metadados(), "mimetype");
        String tipoDoArquivo = MetaCloudMidiaUpload.tipoDoArquivo(
                MetaCloudMidiaUpload.tipoDoCampo(mimetype, midia.tipo()));
        String nome = MetaCloudMidiaUpload.nomeDoArquivo(
                campoDeMetadados(midia.metadados(), "nome"), tipoDoArquivo);

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("messaging_product", "whatsapp");
        multipart.part(
                "file",
                new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return nome;
                    }
                },
                MetaCloudMidiaUpload.contentType(tipoDoArquivo));

        String resposta = http.post()
                .uri(
                        "/{username}/{version}/{phone_number_id}/media",
                        propriedades.usuarioApi(),
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .body(multipart.build())
                .retrieve()
                .body(String.class);
        return idDoUpload(resposta);
    }

    /**
     * Traduz o JSON de aceite sem deixar uma resposta invalida passar por sucesso.
     *
     * <p>Confirmado em duas fontes independentes (Swagger oficial e a pagina de exemplos de
     * {@code uzapi.com.br/docs}): 2xx com corpo real devolve {@code status:"success"},
     * {@code queueId} e {@code messageId} (ambos identificadores internos da fila/instancia —
     * nunca o wamid) e {@code messages[0].id}, que e o id real gerado pelo WhatsApp. So esse ultimo
     * vira {@code idExterno}.
     */
    private ResultadoDeEnvio interpretarAceite(String resposta) {
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
        return new ResultadoDeEnvio.Aceito(id);
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
        return defeitoDoPedido
                ? ResultadoDeEnvio.Recusado.permanente(detalhe)
                : ResultadoDeEnvio.Recusado.temporario(detalhe);
    }

    private boolean credencialIncompleta() {
        return vazio(propriedades.token())
                || vazio(propriedades.numeroPrincipal())
                || vazio(propriedades.usuarioApi())
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
            case LOCALIZACAO ->
                    throw new IllegalArgumentException("LOCALIZACAO nao e midia transferida");
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

    /** Recebimento nao investigado nesta etapa (E152 Bloco 6) — ver docs/38. */
    @Override
    public MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        throw new UnsupportedOperationException("recebimento uzapi-autotic ainda nao investigado");
    }

    /** 2xx do provedor, mas o corpo nao confirma sucesso — sempre recusa permanente. */
    private static final class RespostaInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        RespostaInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
