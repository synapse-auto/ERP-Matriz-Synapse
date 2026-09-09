package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.referencia.AlvoDeResposta;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;
import com.synapse.crm.sharedkernel.midia.ResumoSeguroDeMidia;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

@Service
public class EnviarMidiaUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnviarMidiaUseCase.class);

    private final DetectorDeTipoReal detector;
    private final ArmazenamentoDeMidia armazenamento;
    private final LimiteDeAnexoRepositorio limites;
    private final EnviarMensagemUseCase enviarMensagem;
    private final ObjectMapper json;
    private final ConversorDeAudio conversorDeAudio;

    /** Construtor principal: o conversor real é fornecido pela infraestrutura do atendimento. */
    @Autowired
    public EnviarMidiaUseCase(
            DetectorDeTipoReal detector,
            ArmazenamentoDeMidia armazenamento,
            LimiteDeAnexoRepositorio limites,
            EnviarMensagemUseCase enviarMensagem,
            ObjectMapper json,
            ConversorDeAudio conversorDeAudio) {
        this.detector = detector;
        this.armazenamento = armazenamento;
        this.limites = limites;
        this.enviarMensagem = enviarMensagem;
        this.json = json;
        this.conversorDeAudio = conversorDeAudio;
    }

    /** Mantém compatibilidade para consumidores que montam o caso de uso fora do Spring. */
    public EnviarMidiaUseCase(
            DetectorDeTipoReal detector,
            ArmazenamentoDeMidia armazenamento,
            LimiteDeAnexoRepositorio limites,
            EnviarMensagemUseCase enviarMensagem,
            ObjectMapper json) {
        this(detector, armazenamento, limites, enviarMensagem, json, (conteudo, mimetype) ->
                new ConversorDeAudio.Resultado(conteudo, mimetype));
    }

    @PreAuthorize("isAuthenticated()")
    public EnviarMensagemUseCase.Resultado executar(
            UUID leadId, byte[] conteudo, String nomeArquivoOriginal, String legenda) {
        return executar(leadId, conteudo, nomeArquivoOriginal, legenda, null, false, null);
    }

    @PreAuthorize("isAuthenticated()")
    public EnviarMensagemUseCase.Resultado executar(
            UUID leadId,
            byte[] conteudo,
            String nomeArquivoOriginal,
            String legenda,
            AlvoDeResposta resposta) {
        return executar(leadId, conteudo, nomeArquivoOriginal, legenda, resposta, false, null);
    }

    /**
     * Envia mídia, identificando explicitamente se o arquivo veio da gravação do composer.
     * Anexos escolhidos pelo atendente não entram no conversor.
     */
    @PreAuthorize("isAuthenticated()")
    public EnviarMensagemUseCase.Resultado executar(
            UUID leadId,
            byte[] conteudo,
            String nomeArquivoOriginal,
            String legenda,
            AlvoDeResposta resposta,
            boolean gravacaoDoComposer) {
        return executar(leadId, conteudo, nomeArquivoOriginal, legenda, resposta, gravacaoDoComposer, null);
    }

    /** Variante HTTP que propaga a chave estável do clique até a mensagem transacional. */
    @PreAuthorize("isAuthenticated()")
    public EnviarMensagemUseCase.Resultado executar(
            UUID leadId,
            byte[] conteudo,
            String nomeArquivoOriginal,
            String legenda,
            AlvoDeResposta resposta,
            boolean gravacaoDoComposer,
            String chaveIdempotencia) {
        String mimetypeReal =
                IsoBmffAudioOnly.mimetypeDeAudioSeCamuflado(detector.detectar(conteudo), conteudo);
        TipoMensagem tipo = TiposDeMidiaPermitidos.tipoDe(mimetypeReal).orElse(null);
        if (tipo == null) {
            throw new TipoDeMidiaNaoPermitidoException(mimetypeReal);
        }

        // Converter para a CategoriaDeMidia exigida pelos limites
        CategoriaDeMidia categoria = switch (tipo) {
            case IMAGEM -> CategoriaDeMidia.IMAGEM;
            case AUDIO -> CategoriaDeMidia.AUDIO;
            case DOCUMENTO -> CategoriaDeMidia.DOCUMENTO;
            default -> throw new IllegalStateException("TipoMensagem invalido para midia: " + tipo);
        };

        long limite = limites.limiteEmBytes(categoria).orElseGet(() -> TiposDeMidiaPermitidos.tetoDaMetaEmBytes(tipo));
        if (conteudo.length > limite) {
            throw new AnexoExcedeuLimiteException(conteudo.length, limite);
        }

        byte[] conteudoParaSalvar = conteudo;
        String mimetypeParaSalvar = mimetypeReal;
        boolean convertido = false;
        if (gravacaoDoComposer && tipo == TipoMensagem.AUDIO) {
            // Gravações do composer são sempre normalizadas para o perfil de nota de voz. A
            // conversão é deliberadamente independente do provedor: a Meta exige OGG/Opus com
            // voice=true, e a Uzapi documenta apenas audio.id, derivando a duração do contêiner.
            ConversorDeAudio.Resultado resultado = conversorDeAudio.converterParaOggOpus(conteudo, mimetypeReal);
            if (!ehOggOpus(resultado.mimetype())
                    || resultado.conteudo().length == 0
                    || !ValidadorDeOggOpus.ehValido(resultado.conteudo())) {
                throw new FalhaNaConversaoDeAudioException("conversor de áudio não produziu OGG/Opus válido");
            }
            conteudoParaSalvar = resultado.conteudo();
            mimetypeParaSalvar = resultado.mimetype();
            convertido = true;
            if (conteudoParaSalvar.length > limite) {
                throw new AnexoExcedeuLimiteException(conteudoParaSalvar.length, limite);
            }
        }

        String nomeSanitizado = sanitizar(nomeArquivoOriginal);
        if (convertido) {
            nomeSanitizado = trocarExtensao(nomeSanitizado, ".ogg");
            ResumoSeguroDeMidia resumo = ResumoSeguroDeMidia.de(conteudoParaSalvar);
            log.info(
                    "audio do composer pronto antes do storage: leadId={}, tamanho={}, mimetype={}, sha256={}, oggOpusValido={}",
                    leadId,
                    resumo.tamanho(),
                    mimetypeParaSalvar,
                    resumo.sha256(),
                    true);
        }
        String referencia = armazenamento.salvar(conteudoParaSalvar, nomeSanitizado, mimetypeParaSalvar);
        String metadados = metadadosJson(
                nomeSanitizado,
                mimetypeParaSalvar,
                conteudoParaSalvar.length,
                legenda,
                convertido);

        ConteudoDeEnvio.MensagemMidia envio =
                new ConteudoDeEnvio.MensagemMidia(tipo, referencia, metadados, legenda);
        try {
            EnviarMensagemUseCase.Resultado resultado;
            if (chaveIdempotencia == null || chaveIdempotencia.isBlank()) {
                // Mantém o contrato legado para chamadas internas que não participam do fluxo HTTP
                // idempotente (mensagens programadas e testes de anexo).
                resultado = resposta == null
                        ? enviarMensagem.executar(leadId, envio)
                        : enviarMensagem.executar(leadId, envio, resposta);
            } else {
                resultado = resposta == null
                        ? enviarMensagem.executar(leadId, envio, chaveIdempotencia)
                        : enviarMensagem.executar(leadId, envio, resposta, chaveIdempotencia);
            }
            if (resultado != null && resultado.reutilizadoIdempotente()) {
                // Retry do upload cria apenas um objeto temporário; a mensagem original já aponta
                // para o primeiro objeto e este novo não pode ficar órfão no storage.
                try {
                    armazenamento.remover(referencia);
                } catch (RuntimeException limpezaFalhou) {
                    log.warn("Nao foi possivel remover objeto temporario de upload idempotente (leadId={}).", leadId, limpezaFalhou);
                }
            }
            return resultado;
        } catch (Exception e) {
            armazenamento.remover(referencia);
            throw e;
        }
    }

    private static String sanitizar(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "arquivo";
        }
        String semCaminho = nomeOriginal.replace('\\', '/');
        int barra = semCaminho.lastIndexOf('/');
        String base = barra >= 0 ? semCaminho.substring(barra + 1) : semCaminho;
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean ehOggOpus(String mimetype) {
        String principal = tipoPrincipal(mimetype);
        return "audio/ogg".equalsIgnoreCase(principal) || "audio/opus".equalsIgnoreCase(principal);
    }

    private static String tipoPrincipal(String mimetype) {
        if (mimetype == null || mimetype.isBlank()) return "";
        int separador = mimetype.indexOf(';');
        return (separador < 0 ? mimetype : mimetype.substring(0, separador)).trim();
    }

    private static String trocarExtensao(String nome, String extensao) {
        int ponto = nome.lastIndexOf('.');
        return (ponto > 0 ? nome.substring(0, ponto) : nome) + extensao;
    }

    private String metadadosJson(
            String nome, String mimetype, long tamanho, String legenda, boolean gravacaoDoComposer) {
        ObjectNode no = json.createObjectNode();
        no.put("nome", nome);
        no.put("mimetype", mimetype);
        no.put("tamanho", tamanho);
        if (legenda != null && !legenda.isBlank()) {
            no.put("legenda", legenda);
        }
        if (gravacaoDoComposer) {
            // Marca interna para o worker revalidar a gravação ao recuperá-la; anexos manuais
            // continuam no caminho original, sem conversão ou exigência de nota de voz.
            no.put("gravacaoDoComposer", true);
        }
        return no.toString();
    }
}
