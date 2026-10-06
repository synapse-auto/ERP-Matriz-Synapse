package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/** Porta do read/write model do chat; nenhuma consulta ignora o participante corrente. */
public interface ChatInternoRepositorio {
    List<ConversaResumo> listarConversas(UUID usuarioId);
    List<ConversaResumo> listarConversasPaginado(UUID usuarioId, Instant depoisDe, UUID depoisDoId, int limite);
    List<ContatoResumo> listarContatos(UUID usuarioId);
    Optional<UUID> conversaDireta(UUID primeiroUsuario, UUID segundoUsuario);
    UUID criarConversaDireta(UUID primeiroUsuario, UUID segundoUsuario);
    UUID criarConversaGrupo(String nome, List<UUID> participantes);
    boolean usuarioExiste(UUID usuarioId);
    Optional<String> nomeDoUsuario(UUID usuarioId);
    boolean participante(UUID conversaId, UUID usuarioId);
    List<UUID> participantes(UUID conversaId);
    Optional<TipoConversaChat> tipoDaConversa(UUID conversaId);
    Optional<String> nomeDoGrupo(UUID conversaId);
    void adicionarParticipante(UUID conversaId, UUID usuarioId);
    void removerParticipante(UUID conversaId, UUID usuarioId);
    void renomearGrupo(UUID conversaId, String nome);
    /**
     * Le criador e foto do grupo travando a linha ate o fim da transacao, para que duas trocas
     * simultaneas nao deixem objeto orfao no storage. Vazio em conversa DIRETA ou inexistente.
     */
    Optional<FotoDoGrupo> bloquearFotoDoGrupo(UUID conversaId);
    /** Referencia da foto sem travar nada; vazio se o grupo nao tem foto. */
    Optional<String> referenciaDaFotoDoGrupo(UUID conversaId);
    /**
     * Grava (ou, com referencia nula, remove) a foto. A condicao {@code criado_por_id = criadorId}
     * vai no proprio UPDATE: a RLS deixa qualquer participante escrever na conversa, entao e aqui
     * que a autorizacao vira atomica. Devolve false se nenhuma linha casou.
     */
    boolean definirFotoDoGrupo(UUID conversaId, UUID criadorId, String referencia, Instant versao);
    /** Apaga a conversa se nao restou ninguem — evita linha orfa invisivel. */
    boolean apagarSeSemParticipantes(UUID conversaId);
    PaginaMensagens listarMensagens(UUID conversaId, UUID usuarioId, Instant antesDe, int limite);
    List<MidiaResumo> listarMidias(UUID conversaId, int limite, int deslocamento);
    Optional<MidiaResumo> midia(UUID conversaId, UUID mensagemId);
    MensagemResumo salvarMensagem(UUID conversaId, UUID remetenteId, String conteudo);
    MensagemResumo salvarMensagemSistema(UUID conversaId, UUID atorId, String conteudoJson);
    MensagemResumo salvarMensagemDeMidia(UUID conversaId, UUID remetenteId, String tipo, String conteudo, String midiaUrl, String midiaMetadados);
    Optional<MensagemResumo> mensagem(UUID conversaId, UUID mensagemId);
    MensagemResumo editarMensagem(UUID conversaId, UUID mensagemId, UUID remetenteId, String conteudo, Instant editadoEm);
    MensagemResumo salvarMensagemComReferencia(UUID conversaId, UUID remetenteId, String conteudo,
            String tipo, String midiaUrl, String midiaMetadados, UUID origemConversaId, UUID origemId,
            String referenciaTipo);
    MensagemResumo removerMensagem(UUID conversaId, UUID mensagemId, UUID remetenteId, Instant removidaEm);
    void marcarComoLida(UUID conversaId, UUID usuarioId, Instant quando);

    /** @param criadorId pode ser nulo: criador apagado ou grupo anterior ao registro de autoria */
    record FotoDoGrupo(UUID criadorId, String referencia) {}
    /** @param podeAlterarFoto o usuario consultado e o criador do grupo (calculado no mesmo SELECT) */
    record ConversaResumo(UUID id, TipoConversaChat tipo, String participantes, String ultimaMensagem,
            Instant ultimaMensagemEm, long naoLidas, String fotoUrl, boolean podeAlterarFoto) {
        public ConversaResumo(UUID id, TipoConversaChat tipo, String participantes, String ultimaMensagem,
                Instant ultimaMensagemEm, long naoLidas, String fotoUrl) {
            this(id, tipo, participantes, ultimaMensagem, ultimaMensagemEm, naoLidas, fotoUrl, false);
        }
    }
    record ContatoResumo(UUID id, String nome, String fotoUrl, StatusPresenca presenca) {}
    record MidiaResumo(UUID mensagemId, String tipo, String nome, String mimetype, long tamanho,
            String legenda, String referenciaStorage, Instant enviadoEm) {}
    record MensagemResumo(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
            String tipo, String conteudo, String midiaUrl, String midiaMetadados, Instant enviadoEm,
            Instant editadoEm, List<ResumoDeReacao> reacoes, boolean removida, ReferenciaResumo referencia) {
        public MensagemResumo {
            reacoes = reacoes == null ? List.of() : List.copyOf(reacoes);
        }

        public MensagemResumo(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
                String tipo, String conteudo, String midiaUrl, String midiaMetadados, Instant enviadoEm) {
            this(id, conversaId, remetenteId, remetenteNome, tipo, conteudo, midiaUrl, midiaMetadados,
                    enviadoEm, null, List.of(), false, null);
        }

        public MensagemResumo(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
                String tipo, String conteudo, String midiaUrl, String midiaMetadados, Instant enviadoEm,
                List<ResumoDeReacao> reacoes) {
            this(id, conversaId, remetenteId, remetenteNome, tipo, conteudo, midiaUrl, midiaMetadados,
                    enviadoEm, null, reacoes, false, null);
        }

        public MensagemResumo(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
                String tipo, String conteudo, String midiaUrl, String midiaMetadados, Instant enviadoEm,
                List<ResumoDeReacao> reacoes, boolean removida, ReferenciaResumo referencia) {
            this(id, conversaId, remetenteId, remetenteNome, tipo, conteudo, midiaUrl, midiaMetadados,
                    enviadoEm, null, reacoes, removida, referencia);
        }

        public MensagemResumo comReacoes(List<ResumoDeReacao> novas) {
            return new MensagemResumo(id, conversaId, remetenteId, remetenteNome, tipo, conteudo,
                    midiaUrl, midiaMetadados, enviadoEm, editadoEm, novas, removida, referencia);
        }
    }
    record ReferenciaResumo(UUID origemId, String tipo, String autor, String tipoConteudo,
            String previa, boolean origemRemovida) {}
    record PaginaMensagens(List<MensagemResumo> mensagens, Instant proximoCursor) {}
}
