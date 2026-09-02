package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.application.referencia.AlvoDeResposta;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio;
import com.synapse.crm.atendimento.application.referencia.MontadorDeReferenciaDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.RespostaAoCanalIndevidaException;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Alguem da equipe mandou uma mensagem ou template manual. Se essa pessoa ainda nao esta na
 * conversa, o lead passa a ser dela (RN-CRM-06). Se ja e participante ativo, a mensagem e dela e o
 * dono continua o dono — mas qualquer fala humana tira a conversa de {@code EM_IA}, senão a
 * automacao responde por cima.
 *
 * <p>A transferencia e a contrapartida do isolamento de agenda: a RN-CRM-01 impede pegar o lead do
 * colega, e esta regra garante que quem falou sem ter entrado nao some deixando a conversa orfa.
 * Participar e o mecanismo que a operacao pediu para ajudar sem herdar a comissao — entrar sozinho
 * nao transfere; enviar sendo participante tampouco. Quem nao entrou continua assumindo ao falar.
 *
 * <p><b>Quem o remetente alcanca continua sendo decidido pela RN-CRM-01.</b> A transferencia acontece
 * dentro do recorte de visibilidade, nao por cima dele: um atendente manda mensagem no proprio lead
 * (transferencia sem efeito) ou num lead sem dono do grupo "Potenciais" (e o lead passa a ser dele).
 * Um lead que ja e de um colega nao e alcancavel — o {@code UPDATE} nao encontra a linha e o caso de
 * uso responde como se nao existisse. Quem enxerga a base inteira (gestor) alcanca qualquer lead, e
 * para esse a regra transfere de fato — a menos que ele ja tenha entrado como participante.
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
    private final OrigemDeMensagemRepositorio origens;
    private final MensagemIdExternoRepositorio idsExternos;
    private final MensagemReferenciaRepositorio referencias;
    private final ParticipacaoAtendimentoRepositorio participacoes;

    public EnviarMensagemUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            Outbox outbox,
            CanalGateway canal,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio,
            OrigemDeMensagemRepositorio origens,
            MensagemIdExternoRepositorio idsExternos,
            MensagemReferenciaRepositorio referencias,
            ParticipacaoAtendimentoRepositorio participacoes) {
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
        this.origens = origens;
        this.idsExternos = idsExternos;
        this.referencias = referencias;
        this.participacoes = participacoes;
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
        return executarInterno(leadId, conteudo, usuarioContext.atual().id(), null, null);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, ConteudoDeEnvio conteudo, AlvoDeResposta resposta) {
        ReferenciaDeMensagem referencia = resposta == null ? null : resolverResposta(leadId, resposta);
        return executarInterno(leadId, conteudo, usuarioContext.atual().id(), null, referencia);
    }

    /**
     * Encaminhamento ja autorizado: a origem e o destino foram validados pelo caso de uso de
     * encaminhar. Aqui so reusa o caminho de envio (janela, RN-CRM-06, outbox).
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executarComReferencia(
            UUID leadId, ConteudoDeEnvio conteudo, ReferenciaDeMensagem referencia) {
        return executarInterno(leadId, conteudo, usuarioContext.atual().id(), null, referencia);
    }

    /**
     * Envio disparado por um job de serviço em nome do responsável da mensagem programada. A
     * autoridade de serviço fica restrita ao escopo transacional pelo {@code ContextoDeServico}; o
     * remetente da mensagem continua sendo o atendente que era dono do agendamento.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executarComoServico(
            UUID leadId, UUID remetenteId, ConteudoDeEnvio conteudo, UUID mensagemProgramadaId) {
        return executarInterno(leadId, conteudo, remetenteId, mensagemProgramadaId, null);
    }

    private Resultado executarInterno(
            UUID leadId,
            ConteudoDeEnvio conteudo,
            UUID remetenteId,
            UUID mensagemProgramadaId,
            ReferenciaDeMensagem referencia) {
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
                && !canal.aceitaTextoLivre(contato.ultimaMensagemDoLead(), agora)) {
            throw new ForaDaJanelaException(leadId);
        }

        // Trava o lead visivel antes de olhar a conversa. Sem o FOR UPDATE, o envio lia o
        // atendimento aberto e so depois tentava a posse — uma finalizacao concorrente
        // encerrava a linha e o envio tentava transferir atendimento ja morto (409).
        // bloquearParaAtendimento e a RN-CRM-01 com trava: a mesma RLS de alcancavel, com
        // o lock que serializa com finalizar/transferir.
        if (!leads.bloquearParaAtendimento(leadId)) {
            throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
        }

        Atendimento aberto = atendimentos.abertoDoLead(leadId).orElse(null);
        boolean participanteAtivo =
                aberto != null && participacoes.eParticipanteAtivo(aberto.id(), remetenteId);

        Optional<UUID> donoAnterior;
        boolean trocouDeDono;
        if (participanteAtivo) {
            donoAnterior = Optional.ofNullable(aberto.atendenteId());
            trocouDeDono = false;
            // Posse e IA sao coisas diferentes: participante nao herda o lead, mas qualquer
            // humano que fala tira a conversa da IA. Sem isso o ramo de cima deixava EM_IA
            // intacto e a automacao respondia por cima de quem acabou de entrar.
            if (aberto.status() == StatusAtendimento.EM_IA) {
                aberto = atendimentos.salvar(aberto.retirarDaIa());
                leads.marcarStatus(leadId, StatusBasicoLead.EM_ATENDIMENTO);
            }
        } else {
            // RN-CRM-06: se o lead nao e alcancavel por quem esta enviando, nada mais
            // acontece. Gravar a mensagem antes deixaria mensagem orfa num lead que o
            // remetente nao pode tocar.
            LeadNoCaminhoDeMensagem.Transferencia transferencia =
                    leads.transferirPara(leadId, remetenteId);
            if (!transferencia.aconteceu()) {
                throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
            }
            donoAnterior = transferencia.donoAnterior();
            trocouDeDono = donoAnterior.map(anterior -> !anterior.equals(remetenteId)).orElse(true);

            if (aberto == null) {
                aberto = atendimentos.salvar(
                        Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora));
            }
            // O atendimento acompanha o lead: deixar a conversa com a IA depois de um humano
            // responder faria a automacao continuar falando por cima do atendente.
            if (!aberto.pertenceA(remetenteId)) {
                aberto = atendimentos.salvar(aberto.transferirPara(remetenteId));
            }
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

        if (referencia != null) {
            referencias.gravar(gravada.id(), agora, referencia);
        }

        // A intencao de enviar entra na MESMA transacao que a mensagem. Ou as duas
        // gravam, ou nenhuma: nao existe conversa mostrando mensagem que ninguem tentou
        // enviar, nem envio de mensagem que nao esta na conversa.
        String contextoWamid = referencia == null ? null : referencia.contextoWamid();
        if (mensagemProgramadaId == null) {
            outbox.enfileirarEnvio(
                    gravada.id(),
                    agora,
                    aberto.id(),
                    leadId,
                    contato.telefoneDestino(),
                    aberto.canalCredencialId(),
                    conteudo,
                    contextoWamid);
        } else {
            outbox.enfileirarEnvioProgramado(
                    gravada.id(),
                    agora,
                    aberto.id(),
                    leadId,
                    contato.telefoneDestino(),
                    aberto.canalCredencialId(),
                    conteudo,
                    mensagemProgramadaId);
        }

        leads.registrarInteracao(leadId, agora, 0, 1);

        eventos.publishEvent(new EventoDeAtendimento.MensagemEnviada(
                leadId,
                leads.nomeParaTempoReal(leadId).orElse(""),
                aberto.id(),
                gravada.id(),
                remetenteId,
                donoAnterior,
                trocouDeDono,
                participanteAtivo,
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
                agora,
                MontadorDeReferenciaDeMensagem.citacaoDe(referencia)));

        return new Resultado(aberto, gravada, trocouDeDono);
    }

    private ReferenciaDeMensagem resolverResposta(UUID leadId, AlvoDeResposta resposta) {
        OrigemDeMensagem origem = origens
                .buscar(resposta.mensagemId(), resposta.enviadoEm())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "mensagem", resposta.mensagemId()));
        if (!leadId.equals(origem.leadId())) {
            throw new RespostaAoCanalIndevidaException(
                    "a origem nao pertence a este atendimento");
        }
        atendimentos
                .porId(origem.mensagem().atendimentoId())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "mensagem", resposta.mensagemId()));
        String wamid = idsExternos
                .wamidDe(origem.mensagem().id(), origem.mensagem().enviadoEm())
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new RespostaAoCanalIndevidaException(
                        "a origem nao tem identificador externo para responder no canal"));
        return MontadorDeReferenciaDeMensagem.resposta(origem, wamid);
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
