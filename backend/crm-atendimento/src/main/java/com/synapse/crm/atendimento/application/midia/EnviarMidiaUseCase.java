package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.sharedkernel.midia.*;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

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

        String nomeSanitizado = sanitizar(nomeArquivoOriginal);
        String referencia = armazenamento.salvar(conteudo, nomeSanitizado, mimetypeReal);
        String metadados = metadadosJson(nomeSanitizado, mimetypeReal, conteudo.length, legenda);

        ConteudoDeEnvio.MensagemMidia envio =
                new ConteudoDeEnvio.MensagemMidia(tipo, referencia, metadados, legenda);
        try {
            return enviarMensagem.executar(leadId, envio);
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
