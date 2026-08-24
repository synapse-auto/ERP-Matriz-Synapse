package com.synapse.crm.core.application.lead;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * As unicas escritas em {@code lead} que o caminho critico de mensagem faz.
 *
 * <p>A porta existe por causa de uma restricao de transacao, nao de gosto arquitetural. O registro de
 * mensagem roda no pool do chat ({@code chatDataSource}); {@link LeadRepositorio} roda em JPA sobre o
 * pool geral. Chamar um do outro significaria <b>duas conexoes e portanto duas transacoes</b> — a
 * mensagem poderia gravar e o contador do lead nao, ou o contrario. As implementacoes desta porta
 * usam a mesma conexao do chat, entao ou tudo grava ou nada grava.
 *
 * <p>Quem implementa mora em crm-core, e nao em crm-atendimento, porque a tabela {@code lead} e deste
 * modulo. crm-atendimento pede; crm-core decide como escreve.
 *
 * <p>Repare no que esta porta <b>nao</b> tem: nenhum metodo de leitura de ficha. A unica excecao e o
 * nome curto usado no envelope de tempo real, lido na mesma conexao do caminho critico; consultar a
 * ficha continua sendo assunto do {@link LeadRepositorio}, que nao sabe consultar sem a visibilidade.
 */
public interface LeadNoCaminhoDeMensagem {

    /** Nome curto para eventos publicados depois do commit, sem carregar a ficha completa. */
    Optional<String> nomeParaTempoReal(UUID leadId);

    /**
     * Paga a divida da E03b: contadores e {@code ultima_interacao_em} numa tacada so.
     *
     * <p>Enquanto ninguem escrevia {@code ultima_interacao_em}, o filtro {@code semRetornoDias} caia
     * no {@code COALESCE} com {@code criado_em} e <b>mentia</b> — "sem retorno ha 30 dias" significava
     * "criado ha 30 dias", e lead ativo entrava em campanha de reativacao. Falha silenciosa,
     * plausivel, e visivel para o cliente final.
     *
     * <p>Os contadores sao somados de forma relativa ({@code num_mensagens + ?}), nunca com valor
     * calculado em memoria: duas mensagens concorrentes somam duas, e nao uma.
     */
    void registrarInteracao(UUID leadId, Instant quando, int atendimentosASomar, int mensagensASomar);

    /**
     * RN-CRM-06: o lead passa a ser de quem mandou a mensagem.
     *
     * <p>E a contrapartida do isolamento de agenda — o lead fica com quem trabalhou nele. Como
     * envolve comissao, devolve quem era o dono antes: a timeline precisa dizer de quem para quem.
     *
     * <p>A transferencia respeita a RLS de {@code lead}: um atendente so alcanca o proprio lead ou o
     * que ainda nao tem dono. Nao ha aqui caminho para puxar o lead de um colega — o {@code UPDATE}
     * simplesmente nao encontra a linha.
     */
    Transferencia transferirPara(UUID leadId, UUID novoAtendenteId);

    /** Acompanha a mudanca de estado do atendimento (RF-CRM-71). */
    void marcarStatus(UUID leadId, StatusBasicoLead status);

    /** Se o usuario da transacao corrente alcanca este lead — mesma politica da RN-CRM-01. */
    boolean alcancavel(UUID leadId);

    /**
     * O que o canal precisa saber para mandar mensagem a este lead.
     *
     * <p>Um metodo so, e nao um por campo, porque o caminho critico paga uma ida ao banco por
     * chamada: telefone e janela sao lidos juntos porque sao usados juntos.
     *
     * <p>Vazio significa "nao existe ou nao e seu" — a mesma resposta de sempre.
     */
    Optional<ContatoParaEnvio> contatoParaEnvio(UUID leadId);

    /**
     * Acha o lead deste telefone, ou cria um novo sem dono.
     *
     * <p>O provedor so conhece o numero — o id do CRM e nosso. Sem esta resolucao, uma mensagem de
     * quem nunca falou com a empresa nao teria onde pousar.
     *
     * <p>O lead novo nasce em {@code IA}, sem responsavel: cai no grupo "Potenciais" e fica visivel a
     * todos os atendentes ate alguem responder e assumi-lo pela RN-CRM-06. Nascer atribuido a alguem
     * seria distribuir comissao por ordem de chegada de webhook.
     *
     * <p>So faz sentido em contexto de servico (o webhook nao tem usuario): com um usuario atendente,
     * a RLS esconderia o lead de um colega e o metodo criaria um duplicado.
     */
    UUID resolverPorTelefone(String telefone, String nomeSugerido);

    /**
     * @param telefone destino no canal; sem ele nao ha para onde enviar
     * @param ultimaInteracao base da janela de 24h da Meta. Vazio quando o lead nunca interagiu, o
     *     que para a janela equivale a fechada. Quem decide se a janela esta aberta e o adaptador de
     *     canal — aqui so se le o instante.
     */
    record ContatoParaEnvio(String telefone, Optional<Instant> ultimaInteracao) {}

    /**
     * Resultado de uma transferencia.
     *
     * @param aconteceu falso quando o lead nao e alcancavel por quem pediu
     * @param donoAnterior de quem era antes; vazio se estava sem dono
     */
    record Transferencia(boolean aconteceu, Optional<UUID> donoAnterior) {

        public static Transferencia naoAlcancado() {
            return new Transferencia(false, Optional.empty());
        }

        public static Transferencia de(UUID donoAnterior) {
            return new Transferencia(true, Optional.ofNullable(donoAnterior));
        }
    }
}
