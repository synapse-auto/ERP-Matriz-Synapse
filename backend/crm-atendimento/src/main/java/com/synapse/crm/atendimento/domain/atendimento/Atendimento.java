package com.synapse.crm.atendimento.domain.atendimento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Uma conversa com um lead, do primeiro contato ate o encerramento.
 *
 * <p>O comportamento mora aqui, e nao num servico: {@link #transferirPara(UUID)}, {@link
 * #retirarDaIa()}, {@link #devolverParaIa()} e {@link #finalizar(Instant)} sao as unicas formas de
 * mudar de estado, e recusam atendimento ja finalizado. Se a regra vivesse no caso de uso, o proximo
 * caso de uso teria de lembrar dela.
 *
 * <p>Imutavel: cada transicao devolve uma instancia nova. E o que permite o caso de uso comparar o
 * antes e o depois para decidir que evento publicar, sem guardar copia manual do estado anterior.
 *
 * <p>Java puro — o mapeamento para a tabela {@code atendimento} mora em infrastructure.
 */
public record Atendimento(
        UUID id,
        UUID leadId,
        UUID canalId,
        UUID canalCredencialId,
        UUID atendenteId,
        StatusAtendimento status,
        Instant iniciadoEm,
        Instant finalizadoEm,
        boolean emNegociacao,
        ResultadoVenda resultadoVenda,
        BigDecimal valorVenda,
        UUID vendaRegistradaPorId,
        Instant vendaRegistradaEm,
        OrigemResultadoVenda origemResultadoVenda) {

    /** Compatibilidade para factories e fixtures que ainda nao projetam o estado comercial. */
    public Atendimento(
            UUID id, UUID leadId, UUID canalId, UUID canalCredencialId, UUID atendenteId,
            StatusAtendimento status, Instant iniciadoEm, Instant finalizadoEm) {
        this(id, leadId, canalId, canalCredencialId, atendenteId, status, iniciadoEm, finalizadoEm,
                false, null, null, null, null, null);
    }

    public Atendimento {
        Objects.requireNonNull(id, "id do atendimento e obrigatorio");
        Objects.requireNonNull(leadId, "todo atendimento e de um lead");
        Objects.requireNonNull(status, "status do atendimento e obrigatorio");
        Objects.requireNonNull(iniciadoEm, "iniciadoEm e obrigatorio");
    }

    /**
     * Abre um atendimento com a IA — o estado em que toda conversa nasce.
     *
     * <p>Sem atendente: quem atribui e a transferencia, seja pela RN-CRM-06 (alguem mandou mensagem)
     * seja por decisao de gestor. Nascer ja atribuido furaria o grupo "Potenciais".
     */
    public static Atendimento abrirComIa(
            UUID id, UUID leadId, UUID canalId, UUID canalCredencialId, Instant quando) {
        return new Atendimento(
                id, leadId, canalId, canalCredencialId, null, StatusAtendimento.EM_IA, quando, null);
    }

    /** Reatribui para um atendente humano. */
    public Atendimento transferirPara(UUID novoAtendenteId) {
        Objects.requireNonNull(novoAtendenteId, "para devolver a IA use devolverParaIa()");
        exigirAberto("transferencia");
        return new Atendimento(
                id,
                leadId,
                canalId,
                canalCredencialId,
                novoAtendenteId,
                StatusAtendimento.EM_ATENDIMENTO,
                iniciadoEm,
                finalizadoEm,
                emNegociacao,
                resultadoVenda,
                valorVenda,
                vendaRegistradaPorId,
                vendaRegistradaEm,
                origemResultadoVenda);
    }

    /**
     * Retira a conversa da IA sem alterar o responsável que já estava registrado.
     *
     * <p>É usado por fluxos de serviço e pelo envio de um participante consentido, que retomam o
     * atendimento sem trocar o dono. O envio manual de quem não participa usa
     * {@link #transferirPara(UUID)} para aplicar a RN-CRM-06.
     */
    public Atendimento retirarDaIa() {
        exigirAberto("retirada da IA");
        if (status != StatusAtendimento.EM_IA) {
            return this;
        }
        return new Atendimento(
                id,
                leadId,
                canalId,
                canalCredencialId,
                atendenteId,
                StatusAtendimento.EM_ATENDIMENTO,
                iniciadoEm,
                finalizadoEm,
                emNegociacao,
                resultadoVenda,
                valorVenda,
                vendaRegistradaPorId,
                vendaRegistradaEm,
                origemResultadoVenda);
    }

    /** Devolve para a IA: o atendente sai e a conversa volta para o robo. */
    public Atendimento devolverParaIa() {
        exigirAberto("devolucao para a IA");
        return new Atendimento(
                id, leadId, canalId, canalCredencialId, null, StatusAtendimento.EM_IA, iniciadoEm, null,
                emNegociacao, resultadoVenda, valorVenda, vendaRegistradaPorId, vendaRegistradaEm, origemResultadoVenda);
    }

    /** Encerra. Estado terminal — finalizar duas vezes e erro, nao no-op. */
    public Atendimento finalizar(Instant quando) {
        Objects.requireNonNull(quando, "instante de finalizacao e obrigatorio");
        exigirAberto("finalizacao");
        return new Atendimento(
                id,
                leadId,
                canalId,
                canalCredencialId,
                atendenteId,
                StatusAtendimento.FINALIZADO,
                iniciadoEm,
                quando,
                emNegociacao,
                resultadoVenda,
                valorVenda,
                vendaRegistradaPorId,
                vendaRegistradaEm,
                origemResultadoVenda);
    }

    public Atendimento comNegociacao(boolean valor) {
        exigirAberto("classificacao de negociacao");
        return new Atendimento(id, leadId, canalId, canalCredencialId, atendenteId, status, iniciadoEm, finalizadoEm,
                valor, resultadoVenda, valorVenda, vendaRegistradaPorId, vendaRegistradaEm, origemResultadoVenda);
    }

    public Atendimento comResultadoVenda(
            ResultadoVenda resultado, BigDecimal valor, UUID atorId, Instant quando, OrigemResultadoVenda origem) {
        Objects.requireNonNull(resultado, "resultado de venda e obrigatorio");
        Objects.requireNonNull(atorId, "registro exige usuario");
        Objects.requireNonNull(quando, "data do resultado e obrigatoria");
        Objects.requireNonNull(origem, "origem do resultado e obrigatoria");
        return new Atendimento(id, leadId, canalId, canalCredencialId, atendenteId, status, iniciadoEm, finalizadoEm,
                emNegociacao, resultado, valor, atorId, quando, origem);
    }

    public boolean estaAberto() {
        return status.estaAberto();
    }

    public boolean pertenceA(UUID candidato) {
        return atendenteId != null && atendenteId.equals(candidato);
    }

    /**
     * Mesma politica da RLS de {@code atendimento} (V12), em Java: quem enxerga tudo enxerga este
     * tambem; quem so enxerga a propria carteira precisa ser o dono, ou o atendimento precisa estar
     * sem dono ({@code EM_IA}, o equivalente de "Potenciais" para atendimento).
     *
     * <p>Existe para a revogacao de assinatura do WebSocket (E06): apos uma transferencia, o servidor
     * precisa decidir se o dono anterior ainda enxerga o atendimento <em>sem</em> abrir uma transacao
     * de banco em nome dele — nao ha requisicao dele em andamento para carregar aquele contexto. O
     * calculo usa os dois mesmos ingredientes que constroem a politica SQL:
     * {@link PapelUsuario#enxergaTodosOsLeads()} (a fonte unica do que "papel amplo" significa) e o
     * proprio dono/status deste agregado — nao reinventa a regra, so a aplica sem round-trip.
     */
    public boolean visivelPara(UUID usuarioId, PapelUsuario papel) {
        return papel.enxergaTodosOsLeads()
                || pertenceA(usuarioId)
                || status == StatusAtendimento.EM_IA
                || status == StatusAtendimento.FINALIZADO;
    }

    private void exigirAberto(String tentativa) {
        if (!estaAberto()) {
            throw new AtendimentoJaFinalizadoException(id, tentativa);
        }
    }
}
