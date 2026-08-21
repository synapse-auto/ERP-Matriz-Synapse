package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * O cliente mandou mensagem. O caminho mais quente do sistema.
 *
 * <p>A transacao roda no {@code chatTransactionManager} — o bulkhead do RNF-CRM-01. Nao e detalhe de
 * performance: e o que impede um relatorio pesado de segurar todas as conexoes e derrubar a aba
 * Atendimentos entre 08:00 e 18:30. Omitir o nome do gerente pegaria o {@code @Primary} e, pior que a
 * perda do bulkhead, quebraria a atomicidade — a mensagem gravaria numa conexao e os contadores do
 * lead em outra.
 *
 * <p>Dentro da transacao acontece o minimo: abrir o atendimento se nao houver, gravar a mensagem,
 * somar contadores. Timeline e auditoria sao {@code AFTER_COMMIT}. Nada aqui pode falhar ou demorar
 * por causa de terceiro.
 */
@Service
public class RegistrarMensagemRecebidaUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public RegistrarMensagemRecebidaUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Nao tem {@code @PreAuthorize} porque nao ha usuario: quem chama e o adaptador de canal (E05),
     * sob {@link com.synapse.crm.sharedkernel.identidade.ContextoDeServico}. A autorizacao desse
     * caminho e a do webhook — assinatura do provedor —, verificada na borda.
     */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(MensagemRecebida entrada) {
        Instant agora = Instant.now(relogio);

        if (!leads.alcancavel(entrada.leadId())) {
            throw new RecursoDeAtendimentoIndisponivelException("lead", entrada.leadId());
        }

        // Reusa a conversa aberta; so abre outra quando nao ha nenhuma. Sem isso, cada
        // mensagem do cliente viraria um atendimento novo e a metrica de atendimentos
        // por lead (RF-CRM-71) mediria mensagens, nao conversas.
        Atendimento aberto = atendimentos.abertoDoLead(entrada.leadId()).orElse(null);
        boolean abriu = aberto == null;
        if (abriu) {
            aberto = atendimentos.salvar(Atendimento.abrirComIa(
                    UUID.randomUUID(),
                    entrada.leadId(),
                    entrada.canalId(),
                    entrada.canalCredencialId(),
                    agora));
        }

        Mensagem gravada = mensagens.registrar(entrada.ehMidia()
                ? Mensagem.midia(
                        UUID.randomUUID(), aberto.id(), Remetente.lead(), entrada.tipo(),
                        entrada.midiaUrl(), entrada.midiaMetadados(), agora)
                : Mensagem.texto(
                        UUID.randomUUID(), aberto.id(), Remetente.lead(), entrada.conteudo(), agora));

        // A divida da E03b, paga na MESMA transacao e na MESMA conexao: enquanto
        // ultima_interacao_em ficava nula, o filtro semRetornoDias media "criado ha N
        // dias" e mandava lead ativo para campanha de reativacao.
        leads.registrarInteracao(entrada.leadId(), agora, abriu ? 1 : 0, 1);

        eventos.publishEvent(new EventoDeAtendimento.MensagemRecebida(
                entrada.leadId(), aberto.id(), gravada.id(), abriu, agora));

        // Evento a parte, so para a tela: carrega o que o WebSocket precisa
        // entregar (E06) sem uma segunda consulta ao banco.
        eventos.publishEvent(new MensagemParaTempoReal(
                aberto.id(),
                entrada.leadId(),
                gravada.id(),
                gravada.remetente().tipo().name(),
                gravada.remetente().id(),
                gravada.tipo().name(),
                gravada.conteudo(),
                gravada.midiaUrl(),
                gravada.midiaMetadados(),
                gravada.opcoes(),
                gravada.statusEntrega().name(),
                agora));

        return new Resultado(aberto, gravada, abriu);
    }

    /**
     * O que o adaptador de canal informa.
     *
     * @param canalCredencialId a credencial vigente na epoca; o historico aponta para ela para que
     *     troca de numero nao corrompa conversa antiga
     * @param conteudo texto da mensagem; {@code null} quando {@code tipo} e midia
     * @param tipo {@code null} ou {@code TEXTO} tratam como texto — {@code ehMidia()} e o que decide
     * @param midiaUrl referencia opaca no storage proprio (E11b); {@code null} para texto
     * @param midiaMetadados JSON (nome, mimetype, tamanho, legenda); {@code null} para texto
     */
    public record MensagemRecebida(
            UUID leadId,
            UUID canalId,
            UUID canalCredencialId,
            String conteudo,
            TipoMensagem tipo,
            String midiaUrl,
            String midiaMetadados) {

        /** Atalho para o caso comum: mensagem de texto, sem nenhum campo de midia. */
        public MensagemRecebida(UUID leadId, UUID canalId, UUID canalCredencialId, String conteudo) {
            this(leadId, canalId, canalCredencialId, conteudo, TipoMensagem.TEXTO, null, null);
        }

        public boolean ehMidia() {
            return tipo != null && tipo != TipoMensagem.TEXTO;
        }
    }

    /** @param abriuAtendimento se esta mensagem comecou uma conversa nova */
    public record Resultado(Atendimento atendimento, Mensagem mensagem, boolean abriuAtendimento) {}
}
