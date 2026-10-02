package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;

/**
 * Porta de {@code campanha_template}. Todo metodo exige transacao ativa: o contexto de RLS e aplicado no
 * inicio dela, e fora dela a campanha simplesmente nao existe.
 */
public interface CampanhaRepositorio {

    void inserir(Campanha campanha);

    /** Reescreve a campanha inteira, menos contadores, lease e ultimo ciclo (esses so variam por incremento). */
    void atualizar(Campanha campanha);

    Optional<Campanha> porId(UUID id);

    /**
     * Le com {@code FOR UPDATE}: serializa com o ciclo de envio e com outra acao de administrador. Toda
     * transicao de status le por aqui, para nao sobrescrever a que o worker acabou de fazer.
     */
    Optional<Campanha> bloquearPorId(UUID id);

    /**
     * Le com {@code FOR NO KEY UPDATE}: o envio de cada destinatario trava a campanha por uma transacao curta, e
     * "pausar agora" (que le com {@code FOR UPDATE}) espera no maximo ela; depois nada mais e enviado. Nao pode ser
     * {@code FOR SHARE}: o envio logo em seguida atualiza os contadores da campanha, e duas transacoes que seguram
     * o lock compartilhado e tentam escalar para escrita entram em deadlock (achado pelo teste de quatro threads).
     */
    Optional<Campanha> bloquearParaEnvio(UUID id);

    List<Campanha> listar(int pagina, int tamanho);

    long contar();

    /** Em andamento, ou agendadas cuja hora ja chegou; sem as desligadas. */
    List<UUID> idsParaProcessar(Instant agora);

    /**
     * Pega o lease da campanha se ninguem o tem (ou o dele expirou). Vazio = outro worker esta nela, ou ela
     * nao esta mais elegivel. E isto que garante "nunca dois workers na mesma campanha".
     */
    Optional<Lease> adquirirLease(UUID id, Instant agora, Instant leaseAte);

    void liberarLease(UUID id, Instant ultimoCiclo);

    /** Soma incrementos aos contadores; nunca recalcula por COUNT(*). */
    void variarContadores(UUID id, Variacao variacao);

    /**
     * Reserva uma vaga no limite do dia, atomica: so incrementa se a campanha E a instancia ainda tem folga.
     * Falso = algum dos dois estourou. A transacao do chamador desfaz o incremento parcial.
     */
    boolean reservarVagaDoDia(UUID campanhaId, LocalDate dia, int limiteDaCampanha, int tetoDaInstancia);

    List<DiaEnfileirado> enfileiradasPorDia(UUID campanhaId);

    int enfileiradasNoDia(LocalDate dia);

    Indicadores indicadores();

    record Lease(Campanha campanha, Instant ultimoCicloEm) {}

    record DiaEnfileirado(LocalDate dia, int enfileiradas) {}

    /** Faixa de indicadores do topo da lista: soma dos contadores de todas as campanhas. */
    record Indicadores(long enviadas, long entregues, long lidas, long respondidas, long falhas) {}

    /** Delta dos contadores. Cada fabrica descreve UM acontecimento; some varias com {@link #mais}. */
    record Variacao(
            int pendentes,
            int enfileirados,
            int enviados,
            int entregues,
            int lidos,
            int respondidos,
            int falhas,
            int ignorados,
            int conferencia) {

        public static Variacao nenhuma() {
            return new Variacao(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public static Variacao enfileirou() {
            return new Variacao(-1, 1, 0, 0, 0, 0, 0, 0, 0);
        }

        public static Variacao ignorou() {
            return new Variacao(-1, 0, 0, 0, 0, 0, 0, 1, 0);
        }

        public static Variacao falhou() {
            return new Variacao(0, 0, 0, 0, 0, 0, 1, 0, 0);
        }

        public static Variacao respondeu() {
            return new Variacao(0, 0, 0, 0, 0, 1, 0, 0, 0);
        }

        public static Variacao conferencia(int quantidade) {
            return new Variacao(0, 0, 0, 0, 0, 0, 0, 0, quantidade);
        }

        /** Um degrau do funil de entrega cruzado: enviado, entregue ou lido (o enfileirado e de {@link #enfileirou}). */
        public static Variacao cruzou(StatusDoDestinatario degrau) {
            return switch (degrau) {
                case ENVIADO -> new Variacao(0, 0, 1, 0, 0, 0, 0, 0, 0);
                case ENTREGUE -> new Variacao(0, 0, 0, 1, 0, 0, 0, 0, 0);
                case LIDO -> new Variacao(0, 0, 0, 0, 1, 0, 0, 0, 0);
                default -> nenhuma();
            };
        }

        public Variacao mais(Variacao outra) {
            return new Variacao(
                    pendentes + outra.pendentes,
                    enfileirados + outra.enfileirados,
                    enviados + outra.enviados,
                    entregues + outra.entregues,
                    lidos + outra.lidos,
                    respondidos + outra.respondidos,
                    falhas + outra.falhas,
                    ignorados + outra.ignorados,
                    conferencia + outra.conferencia);
        }

        public boolean vazia() {
            return equals(nenhuma());
        }
    }
}
