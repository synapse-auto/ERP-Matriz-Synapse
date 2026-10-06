package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/**
 * Transforma uma mensagem do Chat Interno no {@link ConteudoDeEnvio} do fluxo externo, aplicando as
 * regras do <em>envio ao cliente</em> e não as do chat: o chat aceita {@code .xlsm} e, sem
 * configuração, até 100 MB; o envio ao cliente segue {@link TiposDeMidiaPermitidos} e o limite
 * configurado da categoria (ou o teto da Meta).
 *
 * <p>A mídia <b>não é copiada</b>: a mensagem externa aponta para o mesmo objeto do storage, que já foi
 * validado pelos bytes quando entrou no chat (Tika, trilhas de vídeo). Por isso a validação aqui usa o
 * tipo real gravado nos metadados, e nenhum byte passa pelo caminho de envio. Os metadados são
 * remontados na forma que os adaptadores leem ({@code nome}, {@code mimetype}, {@code tamanho},
 * {@code legenda}); o chat grava {@code nome_original} e {@code tamanho_bytes}.
 */
@Component
public class MontadorDeConteudoDoChatParaCliente {

    private static final Set<String> VIDEOS_ACEITOS = Set.of("video/mp4", "video/3gpp");

    private final LimiteDeAnexoRepositorio limites;
    private final ObjectMapper json;

    public MontadorDeConteudoDoChatParaCliente(LimiteDeAnexoRepositorio limites, ObjectMapper json) {
        this.limites = limites;
        this.json = json;
    }

    public ConteudoDeEnvio montar(MensagemDoChatParaCliente mensagem) {
        TipoMensagem tipo = TipoMensagem.valueOf(mensagem.tipo());
        if (tipo == TipoMensagem.TEXTO) {
            return new ConteudoDeEnvio.MensagemLivre(mensagem.texto());
        }
        validarFormato(tipo, mensagem.mimetype());
        validarTamanho(tipo, mensagem.tamanhoBytes());
        String nome = sanitizar(mensagem.nomeArquivo());
        // O áudio não leva legenda: as duas APIs recusam. A mensagem externa mostra só o áudio.
        String legenda = tipo == TipoMensagem.AUDIO ? null : mensagem.legenda();
        return new ConteudoDeEnvio.MensagemMidia(
                tipo, mensagem.midiaReferencia(), metadados(nome, mensagem, legenda), legenda);
    }

    private static void validarFormato(TipoMensagem tipo, String mimetype) {
        boolean aceito = switch (tipo) {
            case VIDEO -> VIDEOS_ACEITOS.contains(mimetype);
            case IMAGEM, AUDIO, DOCUMENTO -> TiposDeMidiaPermitidos.tipoDe(mimetype)
                    .map(tipoDoMimetype -> tipoDoMimetype == tipo)
                    .orElse(false);
            default -> false;
        };
        if (!aceito) {
            throw new ConteudoDoChatNaoEnviavelAoClienteException(
                    MotivoDeBloqueio.TIPO_NAO_SUPORTADO,
                    "O formato " + mimetype + " nao e aceito no envio ao cliente.");
        }
    }

    private void validarTamanho(TipoMensagem tipo, Long tamanho) {
        if (tamanho == null) {
            throw new ConteudoDoChatNaoEnviavelAoClienteException(
                    MotivoDeBloqueio.ARQUIVO_SEM_TAMANHO, "O tamanho do arquivo nao foi registrado.");
        }
        long limite = limites.limiteEmBytes(categoriaDe(tipo))
                .orElseGet(() -> TiposDeMidiaPermitidos.tetoDaMetaEmBytes(tipo));
        if (tamanho > limite) {
            throw new ConteudoDoChatNaoEnviavelAoClienteException(
                    MotivoDeBloqueio.ARQUIVO_ACIMA_DO_LIMITE,
                    "O arquivo tem " + tamanho + " bytes e o limite do envio ao cliente e " + limite + ".");
        }
    }

    private static CategoriaDeMidia categoriaDe(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> CategoriaDeMidia.IMAGEM;
            case AUDIO -> CategoriaDeMidia.AUDIO;
            case DOCUMENTO -> CategoriaDeMidia.DOCUMENTO;
            case VIDEO -> CategoriaDeMidia.VIDEO;
            default -> throw new IllegalStateException("TipoMensagem invalido para midia: " + tipo);
        };
    }

    private String metadados(String nome, MensagemDoChatParaCliente mensagem, String legenda) {
        ObjectNode no = json.createObjectNode();
        no.put("nome", nome);
        no.put("mimetype", mensagem.mimetype());
        no.put("tamanho", mensagem.tamanhoBytes());
        if (legenda != null && !legenda.isBlank()) {
            no.put("legenda", legenda);
        }
        return no.toString();
    }

    /** Mesma regra do envio de anexo do atendimento: sem caminho e só caracteres seguros. */
    static String sanitizar(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "arquivo";
        }
        String semCaminho = nomeOriginal.replace('\\', '/');
        int barra = semCaminho.lastIndexOf('/');
        String base = barra >= 0 ? semCaminho.substring(barra + 1) : semCaminho;
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
