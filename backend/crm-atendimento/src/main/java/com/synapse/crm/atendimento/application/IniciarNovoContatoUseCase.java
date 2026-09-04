package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalEntradaAtiva;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Atendente inicia conversa WhatsApp com um contato que ainda nao falou com a empresa.
 *
 * <p>A Meta so permite texto livre dentro da janela de 24h aberta por mensagem do cliente. Contato
 * novo nunca teve essa janela: texto livre e recusado <em>antes</em> de gravar o lead, para ninguem
 * achar que a mensagem saiu. Template pre-aprovado passa. Sem primeira mensagem, a conversa abre em
 * modo humano e o composer oferece os templates.
 *
 * <p>Telefone já presente na Agenda é reutilizado. Quando o contato pertence a outro atendente,
 * a abertura registra o solicitante como participante e preserva o responsável comercial; somente
 * uma transferência explícita troca a posse.
 */
@Service
public class IniciarNovoContatoUseCase {

    private final LeadNoCaminhoDeMensagem leads;
    private final AtendimentoRepositorio atendimentos;
    private final EnviarMensagemUseCase enviar;
    private final CanalGateway canal;
    private final CanalCredencialAtivaRepositorio canaisAtivos;
    private final TelefoneCanonico telefoneCanonico;
    private final UsuarioContext usuarioContext;
    private final Clock relogio;
    private final ParticipacaoAtendimentoRepositorio participacoes;

    public IniciarNovoContatoUseCase(
            LeadNoCaminhoDeMensagem leads,
            AtendimentoRepositorio atendimentos,
            EnviarMensagemUseCase enviar,
            CanalGateway canal,
            CanalCredencialAtivaRepositorio canaisAtivos,
            TelefoneCanonico telefoneCanonico,
            UsuarioContext usuarioContext,
            Clock relogio,
            ParticipacaoAtendimentoRepositorio participacoes) {
        this.leads = leads;
        this.atendimentos = atendimentos;
        this.enviar = enviar;
        this.canal = canal;
        this.canaisAtivos = canaisAtivos;
        this.telefoneCanonico = telefoneCanonico;
        this.usuarioContext = usuarioContext;
        this.relogio = relogio;
        this.participacoes = participacoes;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(Pedido pedido) {
        String nome = pedido.nome() == null ? "" : pedido.nome().trim();
        if (nome.isBlank()) {
            throw new PedidoDeNovoContatoInvalidoException("nome do contato e obrigatorio");
        }
        String telefone;
        try {
            telefone = telefoneCanonico.normalizar(pedido.telefone());
        } catch (TelefoneInvalidoException e) {
            throw e;
        }
        if (telefone == null) {
            throw new PedidoDeNovoContatoInvalidoException("telefone e obrigatorio");
        }

        String mensagemLivre = pedido.primeiraMensagem() == null ? "" : pedido.primeiraMensagem().trim();
        boolean temLivre = !mensagemLivre.isBlank();
        Pedido.Template modelo = pedido.template();
        boolean temTemplate = modelo != null && preenchido(modelo.nome());
        if (temLivre && temTemplate) {
            throw new PedidoDeNovoContatoInvalidoException(
                    "informe texto livre ou template, nao os dois");
        }
        if (temTemplate && !preenchido(modelo.idioma())) {
            throw new PedidoDeNovoContatoInvalidoException("template exige idioma");
        }

        Instant agora = Instant.now(relogio);
        UUID quemPediu = usuarioContext.atual().id();

        Optional<UUID> existente = leads.visivelPorTelefone(telefone);
        Optional<Instant> ultimaMensagemDoLead = existente
                .flatMap(leads::contatoParaEnvio)
                .flatMap(LeadNoCaminhoDeMensagem.ContatoParaEnvio::ultimaMensagemDoLead);

        if (temLivre && !canal.aceitaTextoLivre(ultimaMensagemDoLead, agora)) {
            throw existente
                    .map(ForaDaJanelaException::new)
                    .orElseGet(ForaDaJanelaException::new);
        }

        CanalEntradaAtiva canalAtivo = canaisAtivos.primeiraAtiva().orElse(null);
        UUID leadId = existente.orElseGet(() -> leads.criarParaAtendente(
                        nome, telefone, quemPediu, canalAtivo == null ? null : canalAtivo.canalId())
                .orElseThrow(ContatoIndisponivelParaInicioException::new));

        LeadNoCaminhoDeMensagem.Assuncao assuncao = assumirLead(leadId, quemPediu);
        if (!assuncao.alcancavel()) {
            throw new ContatoIndisponivelParaInicioException();
        }
        Optional<Atendimento> abertoAtual = atendimentos.abertoDoLead(leadId);
        if (abertoAtual.isPresent()) {
            Atendimento atendimentoAtual = abertoAtual.get();
            // Um lead sem responsável é assumido pelo primeiro atendente que inicia o contato.
            // O atendimento aberto correspondente precisa refletir a mesma posse; caso contrário,
            // lead e atendimento ficariam divergentes e a próxima mensagem voltaria à IA.
            if (assuncao.assumiu() && atendimentoAtual.atendenteId() == null) {
                atendimentoAtual = atendimentos.salvar(atendimentoAtual.transferirPara(quemPediu));
            }
            if (!atendimentoAtual.pertenceA(quemPediu)) {
                entrarComoColaborador(atendimentoAtual, quemPediu, agora);
            }
        }

        if (temLivre) {
            EnviarMensagemUseCase.Resultado envio = enviar.executar(leadId, mensagemLivre);
            if (!envio.atendimento().pertenceA(quemPediu)) {
                entrarComoColaborador(envio.atendimento(), quemPediu, agora);
            }
            return new Resultado(leadId, envio.atendimento(), envio.mensagem(), existente.isEmpty());
        }
        if (temTemplate) {
            EnviarMensagemUseCase.Resultado envio = enviar.executar(
                    leadId,
                    new ConteudoDeEnvio.MensagemTemplate(
                            modelo.nome().trim(), modelo.idioma().trim(), modelo.parametros()));
            if (!envio.atendimento().pertenceA(quemPediu)) {
                entrarComoColaborador(envio.atendimento(), quemPediu, agora);
            }
            return new Resultado(leadId, envio.atendimento(), envio.mensagem(), existente.isEmpty());
        }

        Atendimento aberto = abrirSemMensagem(
                leadId,
                quemPediu,
                assuncao.responsavelAtual().orElse(quemPediu),
                agora,
                canalAtivo);
        // Sem mensagem do cliente e sem envio: nao toca ultima_interacao_em. Registrar agora
        // fingiria janela de 24h aberta — a Meta so abre essa janela quando o usuario fala.
        return new Resultado(leadId, aberto, null, existente.isEmpty());
    }

    /**
     * Abre um atendimento novo para um lead visível, sem alterar o atendimento finalizado anterior
     * e sem enviar mensagem. É uma segunda entrada do mesmo caso de uso porque a tela já conhece o
     * lead; o caminho por telefone continua atendendo contatos ainda não cadastrados.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado abrirParaLeadExistente(UUID leadId) {
        if (leadId == null) {
            throw new ContatoIndisponivelParaInicioException();
        }
        UUID quemPediu = usuarioContext.atual().id();
        Instant agora = Instant.now(relogio);
        LeadNoCaminhoDeMensagem.Assuncao assuncao = assumirLead(leadId, quemPediu);
        if (!assuncao.alcancavel()) {
            throw new ContatoIndisponivelParaInicioException();
        }
        CanalEntradaAtiva canalAtivo = canaisAtivos.primeiraAtiva().orElse(null);
        Atendimento aberto = abrirSemMensagem(
                leadId,
                quemPediu,
                assuncao.responsavelAtual().orElse(quemPediu),
                agora,
                canalAtivo);
        return new Resultado(leadId, aberto, null, false);
    }

    private LeadNoCaminhoDeMensagem.Assuncao assumirLead(UUID leadId, UUID quemPediu) {
        return leads.assumirSeSemDono(leadId, quemPediu);
    }

    private Atendimento abrirSemMensagem(
            UUID leadId,
            UUID quemPediu,
            UUID responsavelOficial,
            Instant agora,
            CanalEntradaAtiva canalAtivo) {
        Atendimento aberto = atendimentos.abertoDoLead(leadId).orElse(null);
        if (aberto == null) {
            aberto = atendimentos.salvar(Atendimento.abrirComIa(
                            UUID.randomUUID(),
                            leadId,
                            canalAtivo == null ? null : canalAtivo.canalId(),
                            canalAtivo == null ? null : canalAtivo.canalCredencialId(),
                            agora)
                    .transferirPara(responsavelOficial));
        } else if (aberto.atendenteId() == null) {
            // A assunção do lead sem dono também vale para a conversa já aberta pela IA.
            aberto = atendimentos.salvar(aberto.transferirPara(responsavelOficial));
        }
        if (!aberto.pertenceA(quemPediu)) {
            entrarComoColaborador(aberto, quemPediu, agora);
        }
        return aberto;
    }

    private void entrarComoColaborador(Atendimento atendimento, UUID usuarioId, Instant agora) {
        if (participacoes.eParticipanteAtivo(atendimento.id(), usuarioId)) {
            return;
        }
        participacoes.entrar(atendimento.id(), usuarioId, agora);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    public record Pedido(String nome, String telefone, String primeiraMensagem, Template template) {
        public record Template(String nome, String idioma, List<String> parametros) {
            public Template {
                parametros = parametros == null ? List.of() : List.copyOf(parametros);
            }
        }
    }

    public record Resultado(
            UUID leadId, Atendimento atendimento, Mensagem mensagem, boolean leadCriado) {}
}
