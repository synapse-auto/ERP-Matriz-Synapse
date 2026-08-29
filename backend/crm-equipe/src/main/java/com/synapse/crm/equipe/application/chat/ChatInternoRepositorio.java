package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

/** Porta do read/write model do chat; nenhuma consulta ignora o participante corrente. */
public interface ChatInternoRepositorio {
    List<ConversaResumo> listarConversas(UUID usuarioId);
    List<ConversaResumo> listarConversasPaginado(UUID usuarioId, Instant depoisDe, UUID depoisDoId, int limite);
    List<ContatoResumo> listarContatos(UUID usuarioId);
    Optional<UUID> conversaDireta(UUID primeiroUsuario, UUID segundoUsuario);
    UUID criarConversaDireta(UUID primeiroUsuario, UUID segundoUsuario);
    boolean usuarioExiste(UUID usuarioId);
    boolean participante(UUID conversaId, UUID usuarioId);
    List<UUID> participantes(UUID conversaId);
    PaginaMensagens listarMensagens(UUID conversaId, UUID usuarioId, Instant antesDe, int limite);
    MensagemResumo salvarMensagem(UUID conversaId, UUID remetenteId, String conteudo);
    MensagemResumo salvarMensagemDeMidia(UUID conversaId, UUID remetenteId, String tipo, String conteudo, String midiaUrl, String midiaMetadados);
    void marcarComoLida(UUID conversaId, UUID usuarioId, Instant quando);

    record ConversaResumo(UUID id, TipoConversaChat tipo, String participantes, String ultimaMensagem,
            Instant ultimaMensagemEm, long naoLidas, String fotoUrl) {}
    record ContatoResumo(UUID id, String nome, String fotoUrl, StatusPresenca presenca) {}
    record MensagemResumo(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
            String tipo, String conteudo, String midiaUrl, String midiaMetadados, Instant enviadoEm) {}
    record PaginaMensagens(List<MensagemResumo> mensagens, Instant proximoCursor) {}
}
