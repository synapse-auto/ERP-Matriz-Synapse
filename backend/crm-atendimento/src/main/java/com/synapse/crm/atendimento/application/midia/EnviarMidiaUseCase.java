package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.atendimento.domain.midia.DetectorDeTipoReal;
import com.synapse.crm.atendimento.domain.midia.IsoBmffAudioOnly;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;

/**
 * Anexo do atendente para o cliente (E11b) — valida, guarda no storage e delega o envio em si para
 * {@link EnviarMensagemUseCase}, que ja cuida de janela de 24h, RN-CRM-06, outbox e eventos. Nao ha
 * um segundo caminho de envio: este caso de uso so prepara o {@link ConteudoDeEnvio.MensagemMidia}.
 *
 * <p>Ordem das validacoes e deliberada: tipo real primeiro (barato, so os bytes em memoria), tamanho
 * em segundo (tambem barato), e so entao grava no storage — um upload que vai ser rejeitado nunca
 * chega a escrever no bucket.
 */
@Service
public class EnviarMidiaUseCase {

    private final DetectorDeTipoReal detector;
    private final ArmazenamentoDeMidia armazenamento;
    private final LimiteDeAnexoRepositorio limites;
    private final EnviarMensagemUseCase enviarMensagem;
    private final ObjectMapper json;

    public EnviarMidiaUseCase(
            DetectorDeTipoReal detector,
            ArmazenamentoDeMidia armazenamento,
            LimiteDeAnexoRepositorio limites,
            EnviarMensagemUseCase enviarMensagem,
            ObjectMapper json) {
        this.detector = detector;
        this.armazenamento = armazenamento;
        this.limites = limites;
        this.enviarMensagem = enviarMensagem;
        this.json = json;
    }

    @PreAuthorize("isAuthenticated()")
    public EnviarMensagemUseCase.Resultado executar(
            UUID leadId, byte[] conteudo, String nomeArquivoOriginal, String legenda) {
        String mimetypeReal = detector.detectar(conteudo);
        if (("video/quicktime".equals(mimetypeReal) || "video/mp4".equals(mimetypeReal))
                && IsoBmffAudioOnly.ehAudioSemVideo(conteudo)) {
            mimetypeReal = "audio/mp4";
        }
        TipoMensagem tipo = TiposDeMidiaPermitidos.tipoDe(mimetypeReal).orElse(null);
        if (tipo == null) {
            throw new TipoDeMidiaNaoPermitidoException(mimetypeReal);
        }

        long limite = limites.limiteEmBytes(tipo).orElseGet(() -> TiposDeMidiaPermitidos.tetoDaMetaEmBytes(tipo));
        if (conteudo.length > limite) {
            throw new AnexoExcedeuLimiteException(conteudo.length, limite);
        }

        String nomeSanitizado = sanitizar(nomeArquivoOriginal);
        String referencia = armazenamento.salvar(conteudo, nomeSanitizado, mimetypeReal);
        String metadados = metadadosJson(nomeSanitizado, mimetypeReal, conteudo.length, legenda);

        ConteudoDeEnvio.MensagemMidia envio =
                new ConteudoDeEnvio.MensagemMidia(tipo, referencia, metadados, legenda);
        return enviarMensagem.executar(leadId, envio);
    }

    /** So o nome-base, sem separador de caminho: nunca vira parte de um path no storage. */
    private static String sanitizar(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "arquivo";
        }
        String semCaminho = nomeOriginal.replace('\\', '/');
        int barra = semCaminho.lastIndexOf('/');
        String base = barra >= 0 ? semCaminho.substring(barra + 1) : semCaminho;
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String metadadosJson(String nome, String mimetype, long tamanho, String legenda) {
        ObjectNode no = json.createObjectNode();
        no.put("nome", nome);
        no.put("mimetype", mimetype);
        no.put("tamanho", tamanho);
        if (legenda != null && !legenda.isBlank()) {
            no.put("legenda", legenda);
        }
        return no.toString();
    }
}
