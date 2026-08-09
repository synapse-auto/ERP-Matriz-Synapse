package com.synapse.crm.core.application.lead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadResumo;

/**
 * Porta de acesso a lead.
 *
 * <p><strong>Nao existe metodo que devolva lead sem filtro de visibilidade.</strong> Nao ha
 * {@code findAll()}, nao ha {@code findById()} cru: o vocabulario desta interface simplesmente nao
 * consegue expressar "me da todos os leads". Um caso de uso futuro nao precisa lembrar de aplicar a
 * regra — ele nao tem como pedir uma consulta sem ela.
 *
 * <p>A visibilidade tambem nao e parametro. Se fosse, alguem em algum momento passaria "ampla" so
 * para testar e isso chegaria em producao. Ela e derivada do usuario autenticado dentro do
 * adaptador, que e o unico lugar do sistema que fala com o JPA de lead.
 *
 * <p>Listagem devolve {@link LeadResumo}, que nao tem {@code notas} nem {@code resumoIa}: a
 * exclusao dos campos longos e garantida pelo tipo, nao por disciplina de quem escreve a query.
 */
public interface LeadRepositorio {

    /** Leads visiveis ao usuario da requisicao que casem com o filtro. Sem campos longos. */
    List<LeadResumo> listar(FiltroLead filtro);

    /**
     * Idem, com a arvore de criterios do filtro modular (E03b), paginado (E16 §Bloco 1).
     *
     * <p>Sobrecarga em vez de metodo de nome proprio: as duas consultas tem a mesma garantia — o
     * resultado ja vem recortado pela RN-CRM-01 — e o vocabulario da porta continua sem conseguir
     * expressar "me da todos os leads". {@code pagina} comeca em zero; {@code tamanho} e o teto de
     * linhas por pagina, e a paginacao acontece <b>depois</b> da visibilidade — nunca ha como pedir a
     * pagina 0 de tamanho grande o bastante para transformar um recorte em "quase tudo".
     */
    PaginaDeLeads listar(FiltroDeLeads filtro, int pagina, int tamanho);

    /**
     * Ficha completa do lead, <em>se</em> visivel ao usuario da requisicao.
     *
     * <p>Vazio significa "nao existe ou nao e seu", e os dois casos viram 404. Distinguir os dois
     * na resposta contaria ao atendente que o lead existe e esta com um colega — que e exatamente a
     * informacao que a RN-CRM-01 protege.
     */
    Optional<Lead> porId(UUID id);

    long contar(FiltroLead filtro);

    /**
     * Quantos leads o usuario veria sob este filtro.
     *
     * <p>A tela chama isto a cada mudanca de criterio, entao e {@code COUNT} sobre a mesma condicao —
     * nunca "lista e mede o tamanho". Contar no banco evita transferir milhares de linhas para
     * mostrar um numero.
     */
    long contar(FiltroDeLeads filtro);

    /**
     * Grava alteracoes de um lead ja existente.
     *
     * @return o lead como ficou, ou vazio se ele nao for visivel a quem pediu
     */
    Optional<Lead> salvar(Lead lead);

    /**
     * Soma ao contador de atendimentos do lead (RF-CRM-71).
     *
     * <p>Contador denormalizado: quem cria o atendimento chama isto <b>dentro da mesma
     * transacao</b>, para que ou os dois acontecem ou nenhum. A alternativa — {@code COUNT(*)} ao
     * abrir a ficha — significaria varrer milhoes de linhas toda vez que a aba lateral abre.
     *
     * <p>O incremento e relativo ({@code num_atendimentos + ?}), nao um valor calculado em memoria:
     * dois atendimentos criados ao mesmo tempo somam dois, e nao um.
     */
    void somarAtendimentos(UUID leadId, int quantidade);

    /** Idem para mensagens. Ver {@link #somarAtendimentos(UUID, int)}. */
    void somarMensagens(UUID leadId, int quantidade);
}
