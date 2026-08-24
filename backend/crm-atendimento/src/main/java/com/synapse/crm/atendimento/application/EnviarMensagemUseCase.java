package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Alguem da equipe mandou mensagem — e por isso o lead passa a ser dele (RN-CRM-06).
 *
 * <p>A transferencia e a contrapartida do isolamento de agenda: a RN-CRM-01 impede pegar o lead do
 * colega, e esta regra garante que quem trabalhou fica com ele. Como os atendentes trabalham por
 * comissao, as duas juntas sao politica comercial da casa do cliente, nao preferencia tecnica.
 *
 * <p><b>Quem o remetente alcanca continua sendo decidido pela RN-CRM-01.</b> A transferencia acontece
 * dentro do recorte de visibilidade, nao por cima dele: um atendente manda mensagem no proprio lead
 * (transferencia sem efeito) ou num lead sem dono do grupo "Potenciais" (e o lead passa a ser dele).
 * Um lead que ja e de um colega nao e alcancavel — o {@code UPDATE} nao encontra a linha e o caso de
 * uso responde como se nao existisse. Quem enxerga a base inteira (gestor) alcanca qualquer lead, e
 * para esse a regra transfere de fato.
 */
@Service
public class EnviarMensagemUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final LeadNoCaminhoDeMensagem leads;
    private final Outbox outbox;
    private final CanalGateway canal;
    private final UsuarioContext usuarioContext;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public EnviarMensagemUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            Outbox outbox,
            CanalGateway canal,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.leads = leads;
        this.outbox = outbox;
        // O gateway do provedor ativo, escolhido por configuracao. Este caso de uso nao
        // sabe qual e — so pergunta se pode mandar texto livre.
        this.canal = canal;
        this.usuarioContext = usuarioContext;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Atalho para o caso comum: o atendente digitou um texto.
     *
     * <p>Anotado como a sobrecarga principal, e nao apenas delegando. A chamada interna abaixo e
     * auto-invocacao: ela nao passa pelo proxy do Spring, entao as anotacoes do outro metodo <b>nao
     * valem</b> por este caminho. Sem estas duas linhas, o envio por texto rodaria sem transacao e
     * sem autorizacao — e a trava de {@code TransacaoObrigatoria} foi exatamente o que expos isso.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, String conteudo) {
        return executar(leadId, new ConteudoDeEnvio.MensagemLivre(conteudo));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, ConteudoDeEnvio conteudo) {
        UUID remetenteId = usuarioContext.atual().id();
        Instant agora = Instant.now(relogio);

        // Alcanca o lead? Telefone e janela vem juntos, numa consulta so.
        LeadNoCaminhoDeMensagem.ContatoParaEnvio contato = leads.contatoParaEnvio(leadId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("lead", leadId));

        // A janela de 24h e verificada AQUI, antes de gravar e antes de enfileirar.
        // Deixar a Meta recusar custaria uma chamada de rede, um 400 cru para traduzir,
        // uma linha de outbox que vai esgotar, e um atendente vendo "erro de envio" sem
        // entender que precisava de um template. Quem responde e o adaptador do provedor
        // ativo: um filho com provedor nao oficial responde sempre sim.
        if (conteudo instanceof ConteudoDeEnvio.MensagemLivre
                && !canal.aceitaTextoLivre(contato.ultimaInteracao(), agora)) {
            throw new ForaDaJanelaException(leadId);
        }

        // RN-CRM-06: se o lead nao e alcancavel por quem esta enviando, nada mais
        // acontece. Gravar a mensagem antes deixaria mensagem orfa num lead que o
        // remetente nao pode tocar.
        LeadNoCaminhoDeMensagem.Transferencia transferencia = leads.transferirPara(leadId, remetenteId);
        if (!transferencia.aconteceu()) {
            throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
        }
        boolean trocouDeDono = transferencia
                .donoAnterior()
                .map(anterior -> !anterior.equals(remetenteId))
                .orElse(true);

        Atendimento aberto = atendimentos
                .abertoDoLead(leadId)
                .orElseGet(() -> atendimentos.salvar(
                        Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora)));

        // O atendimento acompanha o lead: deixar a conversa com a IA depois de um humano
        // responder faria a automacao continuar falando por cima do atendente.
        if (!aberto.pertenceA(remetenteId)) {
            aberto = atendimentos.salvar(aberto.transferirPara(remetenteId));
        }

        // PENDENTE, nao ENVIADO: nenhum provedor viu esta mensagem ainda. Gravar ENVIADO
        // aqui — como a E04 fazia — poe um tique de enviado numa mensagem que talvez
        // nunca saia. O publisher da outbox move para ENVIADO ou FALHOU.
        //
        // Texto e midia (E11b) gravam pelo MESMO metodo: so o que muda e tipo/midiaUrl/
        // midiaMetadados, extraidos do ConteudoDeEnvio abaixo. Midia nao tem um caminho
        // paralelo de envio — usa a mesma janela, a mesma transferencia (RN-CRM-06) e a
        // mesma outbox que o texto sempre usou.
        Mensagem gravada = mensagens.registrar(new Mensagem(
                UUID.randomUUID(),
                aberto.id(),
                Remetente.atendente(remetenteId),
                tipoDe(conteudo),
                conteudo.paraHistorico(),
                midiaUrlDe(conteudo),
                midiaMetadadosDe(conteudo),
                StatusEntrega.PENDENTE,
                agora));

        // A intencao de enviar entra na MESMA transacao que a mensagem. Ou as duas
        // gravam, ou nenhuma: nao existe conversa mostrando mensagem que ninguem tentou
        // enviar, nem envio de mensagem que nao esta na conversa.
        outbox.enfileirarEnvio(
                gravada.id(),
                agora,
                aberto.id(),
                leadId,
                contato.telefone(),
                aberto.canalCredencialId(),
                conteudo);

        leads.registrarInteracao(leadId, agora, 0, 1);

        eventos.publishEvent(new EventoDeAtendimento.MensagemEnviada(
                leadId,
                leads.nomeParaTempoReal(leadId).orElse(""),
                aberto.id(),
                gravada.id(),
                remetenteId,
                transferencia.donoAnterior(),
                trocouDeDono,
                agora));

        // Evento a parte, so para a tela: o WebSocket (E06) entrega isto sem
        // uma segunda consulta ao banco.
        eventos.publishEvent(new MensagemParaTempoReal(
                aberto.id(),
                leadId,
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

        return new Resultado(aberto, gravada, trocouDeDono);
    }

    private static TipoMensagem tipoDe(ConteudoDeEnvio conteudo) {
        return conteudo instanceof ConteudoDeEnvio.MensagemMidia midia ? midia.tipo() : TipoMensagem.TEXTO;
    }

    private static String midiaUrlDe(ConteudoDeEnvio conteudo) {
        return conteudo instanceof ConteudoDeEnvio.MensagemMidia midia ? midia.referenciaStorage() : null;
    }

    private static String midiaMetadadosDe(ConteudoDeEnvio conteudo) {
        return conteudo instanceof ConteudoDeEnvio.MensagemMidia midia ? midia.metadados() : null;
    }

    /** @param transferiuOLead se a RN-CRM-06 mudou o dono de fato */
    public record Resultado(Atendimento atendimento, Mensagem mensagem, boolean transferiuOLead) {
        public Atendimento atendimento() {
            return atendimento;
        }

        public Mensagem mensagem() {
            return mensagem;
        }

        public boolean transferiuOLead() {
            return transferiuOLead;
        }
    }
}
