package com.synapse.crm.equipe.application.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Encaminha uma cópia para uma única conversa interna autorizada pelo participante. */
@Service
public class EncaminharMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final IdempotenciaDeMidiaChatRepositorio idempotencia;

    public EncaminharMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, IdempotenciaDeMidiaChatRepositorio idempotencia) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "ENCAMINHAR_MENSAGEM_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_MENSAGEM", capturarDados = false)
    public ChatInternoRepositorio.MensagemResumo executar(UUID mensagemOrigemId, UUID conversaOrigemId,
            UUID conversaDestinoId, UUID chave) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaOrigemId, remetente)
                || !repositorio.participante(conversaDestinoId, remetente)) {
            throw new ChatSemAcessoException();
        }
        ChatInternoRepositorio.MensagemResumo origem = repositorio.mensagem(conversaOrigemId, mensagemOrigemId)
                .orElseThrow(ChatSemAcessoException::new);
        if (origem.removida() || "SISTEMA".equals(origem.tipo())) {
            throw new OperacaoDeGrupoInvalidaException("Mensagem nao pode ser encaminhada.");
        }
        String reserva = chave == null ? null : "encaminhar:" + chave;
        if (reserva != null) {
            // Identidade da operação, não texto/horário nem URL assinada.
            var resultado = idempotencia.reservar(reserva, remetente, conversaDestinoId,
                    impressao(conversaOrigemId, mensagemOrigemId));
            if (!resultado.nova()) {
                return repositorio.mensagem(conversaDestinoId, resultado.mensagemId()).orElseThrow(ChatSemAcessoException::new);
            }
        }
        ChatInternoRepositorio.MensagemResumo salva = repositorio.salvarMensagemComReferencia(
                conversaDestinoId, remetente, origem.conteudo(), origem.tipo(), origem.midiaUrl(),
                origem.midiaMetadados(), conversaOrigemId, mensagemOrigemId, "ENCAMINHAMENTO");
        if (reserva != null) idempotencia.concluir(reserva, salva.id());
        var destinatarios = repositorio.participantes(conversaDestinoId);
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaDestinoId, salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm(),
                salva.remetenteNome(), salva.tipo(), salva.midiaMetadados()));
        return salva;
    }

    private static String impressao(UUID conversa, UUID mensagem) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((conversa + ":" + mensagem).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
