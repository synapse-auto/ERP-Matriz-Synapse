package com.synapse.crm.equipe.application.permissao;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.equipe.domain.permissao.PermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

/**
 * Calcula e guarda as permissoes efetivas por usuario, invalidando por revisao.
 *
 * <p>Cache em memoria por no, isolado por usuario e marcado com a revisao global e as flags com que
 * foi calculado. A cada {@link PoliticaDeRevalidacao#intervaloDeRevalidacao()} (no maximo) o no
 * relê a revisao global e as flags — uma consulta minuscula por no, nao por usuario nem por botao.
 * Revisao diferente = recalculo em lote numa ida ao banco ({@link PermissaoRepositorio#contextoDe}).
 *
 * <p>No mesmo no, {@link #aoAlterarAcesso} derruba o instantaneo depois do commit: a proxima
 * chamada ja ve a revogacao. Em outro no, o atraso e limitado pelo intervalo configurado. Nao
 * depende de Redis: Redis fora do ar nao abre nem fecha nada aqui.
 *
 * <p>Falha de banco propaga (fecha): permissao nunca e presumida.
 */
@Service
public class ResolvedorDePermissoesEfetivas {

    private final PermissaoRepositorio repositorio;
    private final ConsultaDeFuncionalidades funcionalidades;
    private final PoliticaDeRevalidacao politica;
    private final Clock relogio;

    private final ConcurrentHashMap<UUID, Entrada> cache = new ConcurrentHashMap<>();
    private volatile Instantaneo instantaneo;

    /** Sobe a cada invalidacao: uma leitura de revisao iniciada antes dela nao e guardada. */
    private final AtomicLong geracao = new AtomicLong();
    private final AtomicLong acertos = new AtomicLong();
    private final AtomicLong recalculos = new AtomicLong();
    private final AtomicLong leiturasDaRevisao = new AtomicLong();

    public ResolvedorDePermissoesEfetivas(
            PermissaoRepositorio repositorio,
            ConsultaDeFuncionalidades funcionalidades,
            PoliticaDeRevalidacao politica,
            Clock relogio) {
        this.repositorio = repositorio;
        this.funcionalidades = funcionalidades;
        this.politica = politica;
        this.relogio = relogio;
    }

    /** Efetivas do usuario; vazio se o usuario nao existe. */
    public Optional<Resolvido> de(UUID usuarioId) {
        Instantaneo atual = instantaneoAtual();
        Entrada entrada = cache.get(usuarioId);
        if (entrada != null && entrada.revisao == atual.revisao && entrada.flags.equals(atual.flags)) {
            acertos.incrementAndGet();
            return Optional.of(entrada.resolvido);
        }
        recalculos.incrementAndGet();
        Optional<Resolvido> calculado = repositorio.contextoDe(usuarioId).map(contexto -> new Resolvido(
                contexto.papel(),
                contexto.ativo(),
                PoliticaDePermissoes.calcular(contexto.papel(), contexto.perfil(), contexto.excecoes(), atual.flags)));
        calculado.ifPresentOrElse(
                r -> cache.put(usuarioId, new Entrada(atual.revisao, atual.flags, r)),
                () -> cache.remove(usuarioId));
        return calculado;
    }

    /** Flags vigentes neste no (mesmo instantaneo usado no calculo). */
    public Set<String> flagsHabilitadas() {
        return instantaneoAtual().flags;
    }

    /** Depois do commit de qualquer alteracao de acesso: o proximo acesso relê a revisao. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarAcesso(AcessoDeUsuariosAlterado evento) {
        geracao.incrementAndGet();
        instantaneo = null;
        evento.usuarios().forEach(cache::remove);
    }

    public Metricas metricas() {
        return new Metricas(acertos.get(), recalculos.get(), leiturasDaRevisao.get(), cache.size());
    }

    private Instantaneo instantaneoAtual() {
        Instantaneo atual = instantaneo;
        Instant agora = relogio.instant();
        if (atual != null && agora.isBefore(atual.lidoEm.plus(politica.intervaloDeRevalidacao()))) {
            return atual;
        }
        synchronized (this) {
            atual = instantaneo;
            if (atual != null && agora.isBefore(atual.lidoEm.plus(politica.intervaloDeRevalidacao()))) {
                return atual;
            }
            leiturasDaRevisao.incrementAndGet();
            long geracaoDaLeitura = geracao.get();
            Instantaneo novo = new Instantaneo(
                    repositorio.revisaoGlobal(), Set.copyOf(funcionalidades.habilitadas()), agora);
            if (geracao.get() == geracaoDaLeitura) {
                instantaneo = novo;
            }
            return novo;
        }
    }

    /** Papel e situacao ATUAIS do banco (nao do JWT) mais as efetivas. */
    public record Resolvido(PapelUsuario papel, boolean ativo, PermissoesEfetivas efetivas) {}

    public record Metricas(long acertos, long recalculos, long leiturasDaRevisao, int usuariosEmCache) {}

    private record Entrada(long revisao, Set<String> flags, Resolvido resolvido) {}

    private record Instantaneo(long revisao, Set<String> flags, Instant lidoEm) {}
}
