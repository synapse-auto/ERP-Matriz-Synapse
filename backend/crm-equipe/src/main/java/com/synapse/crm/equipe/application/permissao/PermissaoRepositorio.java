package com.synapse.crm.equipe.application.permissao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Persistencia de perfis, excecoes, revisoes e historico.
 *
 * <p>Escritas exigem a revisao lida ({@code revisaoEsperada}); se outro gestor salvou antes, lancam
 * {@link RevisaoDesatualizadaException} sem tocar em nada. As chaves de modulos fora de
 * {@code modulosGerenciados} (desligados por flag) ficam intocadas na substituicao — desligar a
 * flag nao apaga revogacoes ja salvas, e religar volta a aplica-las.
 */
public interface PermissaoRepositorio {

    /** Perfil armazenado (so as chaves salvas) e sua revisao. */
    Armazenado perfil(PapelUsuario papel);

    /** Excecoes armazenadas do usuario e sua revisao (0 e vazio se nunca gravou). */
    Armazenado excecoesDe(UUID usuarioId);

    /** Tudo que o calculo efetivo de um usuario precisa, numa ida ao banco. */
    Optional<ContextoDeAcesso> contextoDe(UUID usuarioId);

    /** Excecoes persistidas de todos os usuarios que tem alguma, numa consulta (badge da lista). */
    Map<UUID, ConfiguracaoDePermissoes> todasAsExcecoes();

    /** Usuarios ativos com o papel — afetados por uma mudanca no perfil. */
    Set<UUID> usuariosAtivosComPapel(PapelUsuario papel);

    long revisaoGlobal();

    /**
     * Trava a linha do usuario ate o fim da transacao (FOR SHARE) e devolve papel e situacao. Serializa
     * com mudanca de papel/desativacao: ninguem valida excecoes contra um papel que esta mudando.
     */
    Optional<AlvoTravado> travarAlvo(UUID usuarioId);

    /** Papel e situacao, sem trava (leituras e previas em transacao somente leitura). */
    Optional<AlvoTravado> alvo(UUID usuarioId);

    long substituirPerfil(PapelUsuario papel, long revisaoEsperada, ConfiguracaoDePermissoes nova,
            Set<Modulo> modulosGerenciados, UUID autorId);

    long substituirExcecoes(UUID usuarioId, long revisaoEsperada, ConfiguracaoDePermissoes novas,
            Set<Modulo> modulosGerenciados, UUID autorId);

    /** Mudanca de papel: descarta todas as excecoes (nenhum privilegio do papel anterior sobrevive). */
    Descartadas descartarExcecoesPorMudancaDePapel(UUID usuarioId, UUID autorId);

    /** Sobe a revisao global; chamado na mesma transacao de toda mudanca de acesso. */
    long incrementarRevisaoGlobal();

    void registrarHistorico(RegistroDeHistorico registro);

    List<RegistroDeHistorico> historicoDoUsuario(UUID usuarioId, int limite);

    record Armazenado(long revisao, ConfiguracaoDePermissoes configuracao) {}

    record ContextoDeAcesso(UUID usuarioId, PapelUsuario papel, boolean ativo,
            ConfiguracaoDePermissoes perfil, ConfiguracaoDePermissoes excecoes) {}

    record AlvoTravado(UUID id, PapelUsuario papel, boolean ativo) {}

    record Descartadas(long revisaoAnterior, long revisaoNova, ConfiguracaoDePermissoes antes) {}

    /**
     * Linha de {@code permissao_historico}. {@code antes}/{@code depois} sao as chaves persistidas
     * (nunca senha, token ou conteudo de conversa).
     */
    record RegistroDeHistorico(
            Escopo escopo, PapelUsuario papel, UUID usuarioId, Operacao operacao,
            long revisaoAnterior, long revisaoNova,
            ConfiguracaoDePermissoes antes, ConfiguracaoDePermissoes depois,
            PapelUsuario origemPapel, UUID origemUsuarioId, UUID autorId, java.time.Instant criadoEm) {}

    enum Escopo { PERFIL, USUARIO }

    enum Operacao { SALVAR, COPIAR, RESTAURAR, MUDANCA_DE_PAPEL }
}
