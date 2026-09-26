package com.synapse.crm.equipe.application.permissao;

import java.util.List;
import java.util.UUID;

import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade;
import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.equipe.domain.permissao.NivelDeAcesso;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeCopia;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Leituras montadas para a tela de Gestao. So modulos disponiveis (flag ligada) aparecem. */
public final class Visoes {

    private Visoes() {}

    /**
     * Um modulo sob a otica de um perfil ou usuario. {@code excecao} e nulo quando herda; minimo e
     * maximo delimitam os niveis que a tela pode oferecer.
     */
    public record LinhaDeModulo(Modulo modulo, NivelDeAcesso doPerfil, NivelDeAcesso excecao, NivelDeAcesso efetivo,
            NivelDeAcesso minimo, NivelDeAcesso maximo) {}

    /**
     * Uma capacidade sob a otica de um perfil ou usuario. {@code doPerfil} e nulo fora do teto ou em
     * recorte estrutural; {@code alteravelPorQuemPede} diz se o ator pode mexer nela (delegacao).
     */
    public record LinhaDeCapacidade(Capacidade capacidade, Boolean doPerfil, Boolean excecao, EstadoDaCapacidade efetivo,
            Capacidade.Alcance alcance, boolean alteravelPorQuemPede) {}

    public record Perfil(PapelUsuario papel, boolean fixo, long revisao, int usuarios, long permitidas, long total,
            boolean editavel, List<LinhaDeModulo> modulos, List<LinhaDeCapacidade> capacidades) {}

    public record ResumoDeUsuario(UUID id, String nome, String email, PapelUsuario papel, boolean ativo,
            String fotoReferencia, int excecoes, boolean editavel) {}

    public record Usuario(ResumoDeUsuario resumo, long revisao, long revisaoDoPerfil, boolean fixo,
            List<LinhaDeModulo> modulos, List<LinhaDeCapacidade> capacidades) {}

    public record Minhas(UUID usuarioId, PapelUsuario papel, long revisao, List<LinhaDeCapacidade> capacidades,
            boolean acessaGestao, boolean editaPerfis, boolean editaExcecoes) {}

    public record Alteracao(String chave, String antes, String depois) {}

    /** Rascunho proposto por uma copia: nada foi salvo. */
    public record PreviaDeCopia(java.util.Map<String, String> niveis, java.util.Map<String, Boolean> acoes,
            List<Alteracao> alteracoes, List<PoliticaDeCopia.Impedimento> impedidos) {}

    public record Gravacao(PapelUsuario papel, UUID usuarioId, PermissaoRepositorio.Operacao operacao,
            long revisaoAnterior, long revisao, int excecoes) {}
}
