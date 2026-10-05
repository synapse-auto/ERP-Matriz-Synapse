package com.synapse.crm.equipe.application.chat;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MensagemResumo;
import com.synapse.crm.equipe.application.chat.MensagemDoChatNaoEncaminhavelException.Motivo;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Lê uma mensagem do Chat Interno para encaminhá-la a um cliente, depois de provar que quem pede
 * participa da conversa. Quem encaminha precisa enxergar a mensagem; não precisa tê-la escrito.
 *
 * <p>O corpo e a mídia vêm sempre da linha persistida, nunca do navegador: o frontend só diz
 * <em>qual</em> mensagem. Evento de sistema, mensagem apagada e contato compartilhado (dados de
 * colega) não são conteúdo para cliente.
 */
@Service
public class ConsultarMensagemDoChatParaEncaminhamentoUseCase {

    private static final Set<String> TIPOS_ENCAMINHAVEIS = Set.of("TEXTO", "IMAGEM", "AUDIO", "VIDEO", "DOCUMENTO");

    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ObjectMapper mapper;

    public ConsultarMensagemDoChatParaEncaminhamentoUseCase(
            ChatInternoRepositorio repositorio, UsuarioContext usuario, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public MensagemDoChatParaCliente executar(UUID conversaId, UUID mensagemId) {
        if (!repositorio.participante(conversaId, usuario.atual().id())) {
            throw new ChatSemAcessoException();
        }
        MensagemResumo mensagem = repositorio.mensagem(conversaId, mensagemId)
                .orElseThrow(ChatSemAcessoException::new);
        if (mensagem.removida()) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.REMOVIDA);
        }
        String tipo = mensagem.tipo();
        if ("SISTEMA".equals(tipo)) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.EVENTO_DE_SISTEMA);
        }
        if (!TIPOS_ENCAMINHAVEIS.contains(tipo)) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.TIPO_NAO_SUPORTADO);
        }
        if ("TEXTO".equals(tipo)) {
            if (mensagem.conteudo() == null || mensagem.conteudo().isBlank()) {
                throw new MensagemDoChatNaoEncaminhavelException(Motivo.SEM_CONTEUDO);
            }
            return new MensagemDoChatParaCliente(
                    conversaId, mensagemId, tipo, mensagem.conteudo(), null, null, null, null, null);
        }
        return midia(conversaId, mensagemId, tipo, mensagem);
    }

    private MensagemDoChatParaCliente midia(UUID conversaId, UUID mensagemId, String tipo, MensagemResumo mensagem) {
        if (mensagem.midiaUrl() == null || mensagem.midiaUrl().isBlank() || mensagem.midiaMetadados() == null) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.MIDIA_INDISPONIVEL);
        }
        JsonNode metadados = lerMetadados(mensagem.midiaMetadados());
        String mimetype = texto(metadados, "mimetype");
        if (mimetype == null) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.MIDIA_INDISPONIVEL);
        }
        JsonNode tamanho = metadados.path("tamanho_bytes");
        return new MensagemDoChatParaCliente(
                conversaId,
                mensagemId,
                tipo,
                null,
                mensagem.midiaUrl(),
                texto(metadados, "nome_original"),
                mimetype,
                tamanho.canConvertToLong() ? tamanho.asLong() : null,
                textoOuNulo(texto(metadados, "legenda"), mensagem.conteudo()));
    }

    private JsonNode lerMetadados(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new MensagemDoChatNaoEncaminhavelException(Motivo.MIDIA_INDISPONIVEL);
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }

    private static String textoOuNulo(String preferido, String alternativo) {
        String escolhido = preferido != null ? preferido : alternativo;
        return escolhido == null || escolhido.isBlank() ? null : escolhido;
    }
}
