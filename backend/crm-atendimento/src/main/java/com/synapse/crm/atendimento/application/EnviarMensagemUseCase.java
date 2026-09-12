package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
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
 * Alguem da equipe mandou uma mensagem ou template manual. A mensagem transfere o responsável
 * comercial para quem a enviou, desde que esse usuário alcance o lead pela RN-CRM-01. A mesma
 * transação grava lead, atendimento, mensagem e outbox; não existe intervalo em que a conversa e
 * a comissão pertençam a pessoas diferentes.
 *
 * <p>A participação continua sendo a forma de alcançar uma conversa colaborativa, mas não é uma
 * exceção à RN-CRM-06: se o participante envia manualmente, assume a responsabilidade. A
 * RN-CRM-01 continua impedindo o alcance fora do recorte de visibilidade.
 *
 * <p><b>Quem o remetente alcança continua sendo decidido pela RN-CRM-01.</b> Para um atendente, um
 * lead de colega só chega a este caso de uso depois de a participação colaborativa ser registrada;
 * gestor e subgestor aplicam o mesmo envio dentro do recorte amplo do próprio papel. Sem alcance, a
 * consulta do caminho crítico continua respondendo como recurso inexistente.
 */
@Service
public class EnviarMensagemUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnviarMensagemUseCase.class);

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
    private final IdempotenciaDeMensagemEnvioRepositorio idempotencia;

    /** Construtor usado pela aplicação: o índice de idempotência é persistente e transacional. */
    @Autowired
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
            ParticipacaoAtendimentoRepositorio participacoes,
            IdempotenciaDeMensagemEnvioRepositorio idempotencia) {
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
        this.idempotencia = idempotencia;
    }

    /** Compatibilidade para testes e consumidores que ainda não precisam de idempotência. */
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
        this(
                atendimentos,
                mensagens,
                leads,
                outbox,
                canal,
                usuarioContext,
                eventos,
                relogio,
                origens,
                idsExternos,
                referencias,
                participacoes,
                new IdempotenciaDeMensagemEnvioRepositorio() {});
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
        return executarInterno(
                leadId, conteudo, usuarioContext.atual().id(), null, null, null, null, null, true);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, ConteudoDeEnvio conteudo, String chaveIdempotencia) {
        return executarInterno(
                leadId, conteudo, usuarioContext.atual().id(), null, null, null, chaveIdempotencia, null, true);
    }

    /**
     * Envio da conversa que o navegador tem aberta. A âncora impede que uma resposta atrasada seja
     * aplicada a uma nova conversa do mesmo lead depois de a anterior ter sido finalizada.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(
            UUID leadId,
            UUID atendimentoEsperadoId,
            ConteudoDeEnvio conteudo,
            AlvoDeResposta resposta,
            String chaveIdempotencia) {
        return executarInterno(
                leadId,
                conteudo,
                usuarioContext.atual().id(),
                null,
                null,
                resposta,
                chaveIdempotencia,
                atendimentoEsperadoId,
                true);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, ConteudoDeEnvio conteudo, AlvoDeResposta resposta) {
        return executarInterno(
                leadId, conteudo, usuarioContext.atual().id(), null, null, resposta, null, null, true);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(
            UUID leadId, ConteudoDeEnvio conteudo, AlvoDeResposta resposta, String chaveIdempotencia) {
        return executarInterno(
                leadId,
                conteudo,
                usuarioContext.atual().id(),
                null,
                null,
                resposta,
                chaveIdempotencia,
                null,
                true);
    }

    /**
     * Encaminhamento ja autorizado: a origem e o destino foram validados pelo caso de uso de
     * encaminhar. Aqui so reusa o caminho de envio (janela, RN-CRM-06, outbox).
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executarComReferencia(
            UUID leadId, ConteudoDeEnvio conteudo, ReferenciaDeMensagem referencia) {
        return executarInterno(
                leadId, conteudo, usuarioContext.atual().id(), null, referencia, null, null, null, true);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executarComReferencia(
            UUID leadId,
            ConteudoDeEnvio conteudo,
            ReferenciaDeMensagem referencia,
            String chaveIdempotencia) {
        return executarInterno(
                leadId,
                conteudo,
                usuarioContext.atual().id(),
                null,
                referencia,
                null,
                chaveIdempotencia,
                null,
                true);
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
        return executarInterno(
                leadId, conteudo, remetenteId, mensagemProgramadaId, null, null, null, null, false);
    }

    private Resultado executarInterno(
            UUID leadId,
            ConteudoDeEnvio conteudo,
            UUID remetenteId,
            UUID mensagemProgramadaId,
            ReferenciaDeMensagem referencia,
            AlvoDeResposta alvoDeResposta,
            String chaveIdempotencia,
            UUID atendimentoEsperadoId,
            boolean transfereResponsabilidade) {
        Instant agora = Instant.now(relogio);

        String chave = normalizarChave(chaveIdempotencia);
        // A resposta HTTP pode ter se perdido depois do commit. Reapresentar a mesma chave deve
        // devolver exatamente a mensagem já aceita, antes de tocar no estado do lead/conversa.
        // Isso também cobre o intervalo em que a primeira mensagem foi aceita e o atendimento
        // acabou sendo finalizado: não abrimos outro atendimento nem transformamos o replay em
        // uma recusa de negócio para o navegador.
        if (chave != null) {
            Optional<IdempotenciaDeMensagemEnvioRepositorio.Reserva> existente =
                    idempotencia.existente(chave, remetenteId, leadId);
            if (existente.isPresent()
                    && existente.get().mensagemId() != null
                    && existente.get().enviadoEm() != null) {
                IdempotenciaDeMensagemEnvioRepositorio.Reserva reserva = existente.get();
                log.info(
                        "Replay idempotente reconciliado: chave={}, usuario={}, lead={}, atendimento={}, mensagem={}, resultado=REPLAY",
                        chave,
                        remetenteId,
                        leadId,
                        reserva.atendimentoId(),
                        reserva.mensagemId());
                return reconstruirResultado(reserva, null);
            }
        }

        // A referencia pode depender de uma consulta que deixa de ser visivel depois de uma
        // finalizacao. Resolva-a somente depois do replay idempotente: a repeticao do mesmo clique
        // deve devolver a mensagem ja aceita sem tocar no estado atual da conversa.
        if (referencia == null && alvoDeResposta != null) {
            referencia = resolverResposta(leadId, alvoDeResposta);
        }

        // Alcanca o lead? Telefone e janela vem juntos, numa consulta so. Esta verificacao ocorre
        // depois do replay idempotente: se a conversa mudou de dono entre o commit e a resposta,
        // a chave da propria tentativa ainda permite devolver a resposta original sem transformar
        // uma corrida de estado em uma falsa recusa para o navegador.
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
        if (atendimentoEsperadoId != null
                && (aberto == null || !atendimentoEsperadoId.equals(aberto.id()))) {
            // A transação já segura o lock do lead. Portanto este não é um snapshot velho: a
            // conversa solicitada acabou ou foi substituída e nenhuma mensagem/outbox pode nascer
            // em outro ciclo para o mesmo clique do navegador.
            throw new AtendimentoJaFinalizadoException(atendimentoEsperadoId, "envio");
        }
        boolean participanteAtivo =
                aberto != null && participacoes.eParticipanteAtivo(aberto.id(), remetenteId);

        Optional<UUID> donoAnterior;
        boolean trocouDeDono;
        if (transfereResponsabilidade) {
            // A transferência roda ainda com a identidade humana da requisição. Só depois de a
            // RLS confirmar que ela alcança o lead elevamos o papel técnico para gravar a troca de
            // dono do atendimento; elevar antes criaria uma porta lateral à RN-CRM-01.
            LeadNoCaminhoDeMensagem.Transferencia transferencia =
                    leads.transferirPara(leadId, remetenteId);
            if (!transferencia.aconteceu()) {
                throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
            }
            donoAnterior = transferencia.donoAnterior();
            trocouDeDono = donoAnterior.map(anterior -> !anterior.equals(remetenteId)).orElse(true);

            if (aberto == null) {
                aberto = atendimentos.salvar(
                        Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora)
                                .transferirPara(remetenteId));
            } else if (!remetenteId.equals(aberto.atendenteId())
                    || aberto.status() != StatusAtendimento.EM_ATENDIMENTO) {
                // A leitura e o lock aconteceram sob a identidade humana. A RLS impede o UPDATE
                // de uma linha que deixa de ser visível para o dono anterior; daqui em diante a
                // única escrita é a transição já autorizada e atômica para quem enviou.
                atendimentos.elevarRlsParaEscritaDeNovoDono();
                aberto = aberto.transferirPara(remetenteId);
                aberto = atendimentos.salvar(aberto);
            }
        } else {
            // Agendamento de serviço não é envio manual e preserva a semântica anterior: só
            // atribui quando o lead não tem dono. Isso evita um job antigo alterar a comissão.
            LeadNoCaminhoDeMensagem.Assuncao assuncao =
                    leads.assumirSeSemDono(leadId, remetenteId);
            if (!assuncao.alcancavel()) {
                throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
            }
            donoAnterior = assuncao.responsavelAtual();
            trocouDeDono = assuncao.assumiu();
            UUID responsavelOficial = assuncao.responsavelAtual().orElse(remetenteId);
            if (aberto == null) {
                aberto = atendimentos.salvar(
                        Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora)
                                .transferirPara(responsavelOficial));
            } else if (aberto.status() == StatusAtendimento.EM_IA) {
                aberto = aberto.atendenteId() == null
                        ? aberto.transferirPara(responsavelOficial)
                        : aberto.retirarDaIa();
                aberto = atendimentos.salvar(aberto);
                leads.marcarStatus(leadId, StatusBasicoLead.EM_ATENDIMENTO);
            }
        }

        if (chave != null) {
            IdempotenciaDeMensagemEnvioRepositorio.Reserva reserva =
                    idempotencia.reservar(chave, remetenteId, leadId, aberto.id());
            if (!reserva.nova()) {
                log.info(
                        "Envio manual idempotente concorrente reutilizado: chave={}, usuario={}, lead={}, atendimento={}",
                        chave,
                        remetenteId,
                        leadId,
                        reserva.atendimentoId());
                return reconstruirResultado(reserva, aberto);
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

        if (chave != null) {
            idempotencia.concluir(chave, remetenteId, gravada.id(), gravada.enviadoEm(), trocouDeDono);
            log.info(
                    "Envio manual aceito para reconciliacao: chave={}, mensagem={}, atendimento={}, resultado=PENDENTE",
                    chave,
                    gravada.id(),
                    aberto.id());
        }

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
                MontadorDeReferenciaDeMensagem.citacaoDe(referencia),
                chave));

        return new Resultado(aberto, gravada, trocouDeDono, chave, false);
    }

    private Resultado reconstruirResultado(
            IdempotenciaDeMensagemEnvioRepositorio.Reserva reserva, Atendimento atendimentoFallback) {
        Atendimento atendimento = atendimentoFallback != null && atendimentoFallback.id().equals(reserva.atendimentoId())
                ? atendimentoFallback
                : atendimentos.porId(reserva.atendimentoId())
                        .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                                "atendimento", reserva.atendimentoId()));
        if (reserva.mensagemId() == null || reserva.enviadoEm() == null) {
            throw new IllegalStateException("reserva de idempotencia de envio sem mensagem concluida");
        }
        Mensagem mensagem = mensagens.porId(reserva.mensagemId(), reserva.enviadoEm())
                .orElseThrow(() -> new IllegalStateException("mensagem idempotente nao encontrada"));
        return new Resultado(atendimento, mensagem, reserva.transferiuOLead(), reserva.chave(), true);
    }

    private static String normalizarChave(String chave) {
        if (chave == null || chave.isBlank()) return null;
        String normalizada = chave.trim();
        if (normalizada.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key excede 255 caracteres");
        }
        return normalizada;
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
    public record Resultado(
            Atendimento atendimento,
            Mensagem mensagem,
            boolean transferiuOLead,
            String chaveIdempotencia,
            boolean reutilizadoIdempotente) {
        public Resultado(Atendimento atendimento, Mensagem mensagem, boolean transferiuOLead) {
            this(atendimento, mensagem, transferiuOLead, null, false);
        }

        public Resultado(
                Atendimento atendimento,
                Mensagem mensagem,
                boolean transferiuOLead,
                String chaveIdempotencia) {
            this(atendimento, mensagem, transferiuOLead, chaveIdempotencia, false);
        }
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
