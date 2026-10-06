package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.encaminhamentodochat.PreviaDoEncaminhamento.Efeito;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Lê o destino do encaminhamento sob a RLS de quem pede, sem gravar nada, e diz o que o envio faria.
 * Um atendimento que o usuário não alcança responde como inexistente — a prévia não revela que ele existe.
 *
 * <p>Telefone, lead e canal saem daqui, do backend; o navegador só informou qual atendimento.
 */
@Component
public class DestinoDoEncaminhamentoDoChat {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ParticipacaoAtendimentoRepositorio participacoes;
    private final CanalGateway canal;
    private final UsuarioRepositorio usuarios;
    private final UsuarioContext usuarioContext;
    private final MontadorDeConteudoDoChatParaCliente montador;
    private final Clock relogio;

    public DestinoDoEncaminhamentoDoChat(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ParticipacaoAtendimentoRepositorio participacoes,
            CanalGateway canal,
            UsuarioRepositorio usuarios,
            UsuarioContext usuarioContext,
            MontadorDeConteudoDoChatParaCliente montador,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.participacoes = participacoes;
        this.canal = canal;
        this.usuarios = usuarios;
        this.usuarioContext = usuarioContext;
        this.montador = montador;
        this.relogio = relogio;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public PreviaDoEncaminhamento previa(MensagemDoChatParaCliente mensagem, UUID atendimentoId) {
        Atendimento atendimento = atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        LeadNoCaminhoDeMensagem.ContatoParaEnvio contato = leads.contatoParaEnvio(atendimento.leadId())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("lead", atendimento.leadId()));
        UUID eu = usuarioContext.atual().id();
        UUID responsavel = atendimento.atendenteId();
        MotivoDeBloqueio bloqueio = bloqueio(mensagem, atendimento, contato);
        return new PreviaDoEncaminhamento(
                atendimento.id(),
                leads.nomeParaTempoReal(atendimento.leadId()).orElse(""),
                TelefoneMascarado.de(contato.telefone()),
                atendimento.status().name(),
                responsavel == null ? null : usuarios.porId(responsavel).map(u -> u.nome()).orElse(null),
                efeito(atendimento, responsavel, eu),
                mensagem.tipo(),
                mensagem.texto(),
                mensagem.legenda(),
                mensagem.nomeArquivo(),
                mensagem.mimetype(),
                mensagem.tamanhoBytes(),
                bloqueio == null,
                bloqueio);
    }

    private MotivoDeBloqueio bloqueio(
            MensagemDoChatParaCliente mensagem,
            Atendimento atendimento,
            LeadNoCaminhoDeMensagem.ContatoParaEnvio contato) {
        if (!atendimento.estaAberto()) {
            return MotivoDeBloqueio.ATENDIMENTO_FINALIZADO;
        }
        try {
            ConteudoDeEnvio conteudo = montador.montar(mensagem);
            boolean foraDaJanela = conteudo instanceof ConteudoDeEnvio.MensagemLivre
                    && !canal.aceitaTextoLivre(contato.ultimaMensagemDoLead(), Instant.now(relogio));
            return foraDaJanela ? MotivoDeBloqueio.FORA_DA_JANELA : null;
        } catch (ConteudoDoChatNaoEnviavelAoClienteException e) {
            return e.motivo();
        }
    }

    private Efeito efeito(Atendimento atendimento, UUID responsavel, UUID eu) {
        if (responsavel == null) {
            return Efeito.ASSUME_O_LEAD;
        }
        if (responsavel.equals(eu)) {
            return Efeito.VOCE_E_RESPONSAVEL;
        }
        return participacoes.eParticipanteAtivo(atendimento.id(), eu)
                ? Efeito.MANTEM_RESPONSAVEL
                : Efeito.MANTEM_RESPONSAVEL_E_CONVIDA;
    }
}
