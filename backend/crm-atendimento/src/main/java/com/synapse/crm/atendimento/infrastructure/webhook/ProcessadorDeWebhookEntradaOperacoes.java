package com.synapse.crm.atendimento.infrastructure.webhook;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetGeralRepositorio;
import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetRepositorio;
import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemRecebidaRepositorio;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalEntradaAtiva;
import com.synapse.crm.atendimento.application.reacao.RegistrarReacaoDoClienteUseCase;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.application.referencia.MontadorDeReferenciaDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.application.lead.ResetarFichaDoLeadUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Drena a fila de entrada: traduz o payload cru e registra todas as mensagens contidas nele.
 *
 * <p>Espelho do publisher da outbox, no sentido oposto. Fica fora do ciclo de request de proposito:
 * assim uma falha de traducao, um lead que precisa ser criado ou um pico de carga nao viram timeout
 * no webhook — e timeout no webhook vira reentrega, que vira mais carga.
 *
 * <p>Bean separado de {@link ProcessadorDeWebhookEntrada} de proposito: quem tem o {@code @Scheduled}
 * e o outro, e ele chama {@link #rodada()} atraves desta instancia injetada — uma chamada externa de
 * verdade, que passa pelo proxy do Spring. Ver o Javadoc de {@link ProcessadorDeWebhookEntrada} para
 * o porque (E07b).
 *
 * <p>Roda em {@link ContextoDeServico}, publicado por {@link ProcessadorDeWebhookEntrada} antes de
 * chamar: nao ha usuario numa mensagem que chega do cliente. E o contexto tambem e o que permite
 * {@code resolverPorTelefone} enxergar leads de qualquer atendente — com contexto de usuario, a RLS
 * esconderia o lead de um colega e criariamos um duplicado.
 */
@Component
public class ProcessadorDeWebhookEntradaOperacoes {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeWebhookEntradaOperacoes.class);

    public static final String MARCADOR_ALARME = "[ALERTA_WEBHOOK_ESGOTADO]";

    /**
     * Item de mensagem do cliente que nao virou mensagem no historico. Fixo para alerta de log; o
     * detalhe duravel fica em {@code webhook_entrada.descartes}.
     */
    public static final String MARCADOR_DESCARTE = "[DESCARTE_WEBHOOK]";

    /**
     * Midia que o provedor nao entregou dentro do prazo e entrou na conversa sem arquivo. Fixo para
     * alerta de log: e anexo do cliente que o atendente precisa pedir de novo.
     */
    public static final String MARCADOR_MIDIA_NAO_RECEBIDA = "[MIDIA_NAO_RECEBIDA]";

    private final WebhookEntrada entrada;
    private final TradutorDeCanal tradutor;
    private final IdempotenciaDeMensagemRecebidaRepositorio idempotencia;
    private final RegistrarMensagemRecebidaUseCase registrar;
    private final RegistrarReacaoDoClienteUseCase registrarReacao;
    private final MensagemIdExternoRepositorio idsExternos;
    private final OrigemDeMensagemRepositorio origens;
    private final AtendimentoRepositorio atendimentos;
    private final ConfiguracaoDoComandoResetRepositorio configuracaoDoReset;
    private final ConfiguracaoDoComandoResetGeralRepositorio configuracaoDoResetGeral;
    private final TransferirAtendimentoUseCase transferirAtendimento;
    private final ResetarFichaDoLeadUseCase resetarFichaDoLead;
    private final LeadNoCaminhoDeMensagem leads;
    private final CanalGateway canal;
    private final ArmazenamentoDeMidia armazenamento;
    private final CanalCredencialAtivaRepositorio canaisAtivos;
    private final ObjectMapper json;
    private final Clock relogio;
    private final int lote;
    private final int maximoDeTentativas;
    private final Duration backoffInicial;
    private final Duration backoffMaximo;
    private final Duration prazoAbsoluto;
    private final Duration prazoMidia;
    private final TransactionTemplate transacoes;

    public ProcessadorDeWebhookEntradaOperacoes(
            WebhookEntrada entrada,
            TradutorDeCanal tradutor,
            IdempotenciaDeMensagemRecebidaRepositorio idempotencia,
            RegistrarMensagemRecebidaUseCase registrar,
            RegistrarReacaoDoClienteUseCase registrarReacao,
            MensagemIdExternoRepositorio idsExternos,
            OrigemDeMensagemRepositorio origens,
            AtendimentoRepositorio atendimentos,
            ConfiguracaoDoComandoResetRepositorio configuracaoDoReset,
            ConfiguracaoDoComandoResetGeralRepositorio configuracaoDoResetGeral,
            TransferirAtendimentoUseCase transferirAtendimento,
            ResetarFichaDoLeadUseCase resetarFichaDoLead,
            LeadNoCaminhoDeMensagem leads,
            CanalGateway canal,
            ArmazenamentoDeMidia armazenamento,
            CanalCredencialAtivaRepositorio canaisAtivos,
            ObjectMapper json,
            Clock relogio,
            @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) PlatformTransactionManager chatTransactionManager,
            @Value("${synapse.canal.webhook.lote:50}") int lote,
            @Value("${synapse.canal.webhook.maximo-de-tentativas:5}") int maximoDeTentativas,
            @Value("${synapse.canal.webhook.prazo-absoluto:2h}") Duration prazoAbsoluto,
            @Value("${synapse.canal.webhook.backoff-inicial:5s}") Duration backoffInicial,
            @Value("${synapse.canal.webhook.backoff-maximo:30m}") Duration backoffMaximo,
            @Value("${synapse.canal.webhook.prazo-midia:10m}") Duration prazoMidia) {
        this.entrada = entrada;
        this.tradutor = tradutor;
        this.idempotencia = idempotencia;
        this.registrar = registrar;
        this.registrarReacao = registrarReacao;
        this.idsExternos = idsExternos;
        this.origens = origens;
        this.atendimentos = atendimentos;
        this.configuracaoDoReset = configuracaoDoReset;
        this.configuracaoDoResetGeral = configuracaoDoResetGeral;
        this.transferirAtendimento = transferirAtendimento;
        this.resetarFichaDoLead = resetarFichaDoLead;
        this.leads = leads;
        this.canal = canal;
        this.armazenamento = armazenamento;
        this.canaisAtivos = canaisAtivos;
        this.json = json;
        this.relogio = relogio;
        this.lote = lote;
        this.maximoDeTentativas = maximoDeTentativas;
        this.backoffInicial = backoffInicial;
        this.backoffMaximo = backoffMaximo;
        this.prazoAbsoluto = prazoAbsoluto;
        this.prazoMidia = prazoMidia;
        this.transacoes = new TransactionTemplate(chatTransactionManager);
        this.transacoes.setName("processar webhook de entrada");
    }

    public int rodada() {
        List<WebhookEntrada.Pendente> pendentes = transacoes.execute(status -> entrada.reservarPendentes(lote));
        for (WebhookEntrada.Pendente pendente : pendentes) {
            processar(pendente);
        }
        return pendentes.size();
    }

    private void processar(WebhookEntrada.Pendente pendente) {
        Instant agora = Instant.now(relogio);
        boolean ultimaChanceDaMidia = prazoDaMidiaEstourado(pendente, agora);
        try {
            transacoes.executeWithoutResult(
                    status -> processarEmTransacao(pendente, agora, ultimaChanceDaMidia));

        } catch (RuntimeException e) {
            // O estado de retry precisa de uma transação própria: a transação da mensagem foi
            // desfeita inteira, inclusive a reserva de idempotência, quando algo falhou.
            transacoes.executeWithoutResult(status -> falhar(pendente, agora, e));
        }
    }

    private void processarEmTransacao(
            WebhookEntrada.Pendente pendente, Instant agora, boolean ultimaChanceDaMidia) {
        TradutorDeCanal.Traducao traducao = tradutor.traduzirComDescartes(pendente.payloadCru());
        List<TradutorDeCanal.ItemDescartado> descartes = new ArrayList<>(traducao.descartes());
        String comandoReset = configuracaoDoReset.valor().orElse("");
        if (comandoReset.isBlank()) {
            log.warn(
                    "Comando de reset da Automacao indisponivel; mensagens de reset nao serao reconhecidas.");
        }
        String comandoResetGeral = configuracaoDoResetGeral.valor().orElse("");
        if (comandoResetGeral.isBlank()) {
            log.warn(
                    "Comando de reset geral indisponivel; mensagens de reset geral nao serao reconhecidas.");
        }

        for (TradutorDeCanal.MensagemRecebidaDoCanal mensagem : traducao.mensagens()) {
            if (mensagem.idExterno() == null || mensagem.idExterno().isBlank()) {
                descartes.add(descarteDoProcessador(mensagem));
                continue;
            }
            if (!idempotencia.reservarSeNova(mensagem.idExterno())) {
                // Reentrega de mensagem ja registrada: e deduplicacao, nao perda.
                continue;
            }

            UUID leadId =
                    leads.resolverPorTelefone(mensagem.telefoneRemetente(), mensagem.nomeExibicao());
            CanalEntradaAtiva canalEntrada = canaisAtivos
                    .porIdentificadorExterno(mensagem.identificadorDestino())
                    .orElseThrow(() -> new IllegalStateException(
                            "canal de entrada nao configurado: " + mensagem.identificadorDestino()));
            ReferenciaDeMensagem referencia = referenciaDaMensagem(mensagem, leadId);
            RegistrarMensagemRecebidaUseCase.MensagemRecebida requisicao;
            if (mensagem.ehMidia()) {
                if (mensagem.midiaIdExterno() == null || mensagem.midiaIdExterno().isBlank()) {
                    // Um webhook de midia sem referencia nao pode ser baixado com seguranca. O item
                    // e descartado isoladamente para que as demais mensagens do mesmo POST sigam.
                    log.warn("Mensagem de midia sem id externo; item descartado.");
                    descartes.add(descarteDoProcessador(mensagem));
                    continue;
                }
                try {
                    requisicao = mensagemRecebidaDeMidia(leadId, mensagem, canalEntrada, referencia);
                } catch (MidiaRecebidaTemporariamenteIndisponivelException e) {
                    if (!ultimaChanceDaMidia) {
                        // Tipo normalizado + id tecnico ficam no motivo seguro de retry; o payload
                        // e o corpo da resposta do provedor nunca atravessam esta fronteira.
                        throw e.comTipo(mensagem.tipo());
                    }
                    // E207: o prazo acabou e o provedor nao entregou o arquivo. Esgotar a linha
                    // deixaria o anexo do cliente invisivel para o atendente; registrar sem
                    // arquivo mostra na conversa que ele existiu e precisa ser pedido de novo.
                    requisicao = mensagemRecebidaSemArquivo(leadId, mensagem, canalEntrada, referencia);
                    log.warn(
                            "{} entrada={} {}",
                            MARCADOR_MIDIA_NAO_RECEBIDA,
                            pendente.idExterno(),
                            e.comTipo(mensagem.tipo()).getMessage());
                }
            } else if (TipoMensagem.valueOf(mensagem.tipo()).exigeMetadados()) {
                // Localizacao e contato compartilhado: o tradutor ja normalizou o conteudo em JSON,
                // que vai para midia_metadados; conteudo fica nulo.
                requisicao = new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                        leadId,
                        canalEntrada.canalId(),
                        canalEntrada.canalCredencialId(),
                        null,
                        TipoMensagem.valueOf(mensagem.tipo()),
                        null,
                        mensagem.texto(),
                        referencia);
            } else {
                requisicao = new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                        leadId,
                        canalEntrada.canalId(),
                        canalEntrada.canalCredencialId(),
                        mensagem.texto(),
                        TipoMensagem.valueOf(mensagem.tipo()),
                        null,
                        null,
                        referencia);
            }
            RegistrarMensagemRecebidaUseCase.Resultado resultado = registrar.executar(requisicao);
            // O endereco cru e parte do mesmo commit da mensagem. A escrita fica depois do registro
            // para nao tomar o lock do lead antes que o caminho critico atualize seus contadores.
            // Assim a concorrencia de recebimento e finalizacao conserva a ordem de locks existente.
            leads.registrarTelefoneProvedor(leadId, mensagem.telefoneRemetente());
            idsExternos.gravar(
                    mensagem.idExterno(),
                    resultado.mensagem().id(),
                    resultado.mensagem().enviadoEm(),
                    resultado.atendimento().id());

            // A mensagem fica gravada e seus eventos de mensagem seguem normalmente. So depois
            // disso o CRM aplica a metade que lhe cabe do #reset: devolver um atendimento humano
            // para a IA. A Automacao limpa o proprio contexto ao observar o mesmo literal.
            // O #resetgeral (E195) faz o mesmo do #reset e ainda zera a ficha do lead. Os dois sao
            // testados em cadeia, com o geral primeiro: um filho pode, por engano, configurar o
            // mesmo literal nas duas chaves, e o else-if garante que nesse caso roda o efeito maior
            // uma vez so, em vez de os dois ramos disputarem a mesma mensagem.
            if (!mensagem.ehMidia() && ehComandoReset(mensagem.texto(), comandoResetGeral)) {
                if (resultado.atendimento().status().estaAberto()) {
                    transferirAtendimento.devolverParaIaPeloSistema(resultado.atendimento().id());
                }
                // A ficha zera mesmo com o atendimento ja finalizado: o objetivo e o proximo teste
                // comecar do zero, e isso nao depende de haver conversa aberta agora.
                resetarFichaDoLead.executar(leadId);
            } else if (!mensagem.ehMidia() && ehComandoReset(mensagem.texto(), comandoReset)) {
                if (resultado.atendimento().status().estaAberto()) {
                    transferirAtendimento.devolverParaIaPeloSistema(resultado.atendimento().id());
                }
            }
        }

        // Depois das mensagens: reagir a uma mensagem que chegou no mesmo POST encontra o alvo. A
        // reacao nao passa pelo reset nem pela Automacao, nao cria lead e nao abre atendimento.
        for (TradutorDeCanal.ReacaoRecebidaDoCanal reacao : traducao.reacoes()) {
            if (!idempotencia.reservarSeNova(reacao.idExterno())) {
                continue;
            }
            if (registrarReacao.executar(reacao) == RegistrarReacaoDoClienteUseCase.Resultado.ALVO_DESCONHECIDO) {
                descartes.add(new TradutorDeCanal.ItemDescartado(
                        "reaction", TradutorDeCanal.MotivoDeDescarte.ALVO_DESCONHECIDO));
            }
        }

        // Payload sem mensagem suportada tambem e consumido — reentregar nao faria o tipo passar a
        // ser suportado. O que muda e que a perda fica registrada na propria linha, por tipo e
        // motivo, e aparece no log com um marcador fixo para alerta.
        entrada.marcarProcessado(pendente.idExterno(), agora, descartes);
        if (!descartes.isEmpty()) {
            log.warn(
                    "{} entrada={} provedor={} itens={} descartes={}",
                    MARCADOR_DESCARTE,
                    pendente.idExterno(),
                    tradutor.provedor(),
                    descartes.size(),
                    resumo(descartes));
        }
    }

    /**
     * {@code TipoMensagem} do CRM -> tipo de item como os provedores o chamam. O descarte do
     * processador usa o mesmo vocabulario dos tradutores, para que a mesma causa nao apareca com
     * dois nomes na consulta operacional.
     */
    private static final Map<String, String> TIPO_DO_ITEM_POR_TIPO_DO_CRM = Map.of(
            "TEXTO", "text",
            "IMAGEM", "image",
            "AUDIO", "audio",
            "DOCUMENTO", "document",
            "VIDEO", "video",
            "LOCALIZACAO", "location",
            "CONTATO", "contacts");

    /**
     * Defesa em profundidade: os tradutores ja recusam item sem id ou sem referencia de midia, mas
     * o processador nao registra mensagem que chegue assim de um tradutor futuro.
     */
    private static TradutorDeCanal.ItemDescartado descarteDoProcessador(
            TradutorDeCanal.MensagemRecebidaDoCanal mensagem) {
        String tipo = mensagem.tipo() == null
                ? "outro"
                : TIPO_DO_ITEM_POR_TIPO_DO_CRM.getOrDefault(mensagem.tipo(), "outro");
        return new TradutorDeCanal.ItemDescartado(
                tipo, TradutorDeCanal.MotivoDeDescarte.SEM_IDENTIFICADOR);
    }

    /** {@code tipo:MOTIVO} por item, sem id, telefone ou conteudo. */
    private static String resumo(List<TradutorDeCanal.ItemDescartado> descartes) {
        return descartes.stream()
                .map(descarte -> descarte.tipo() + ":" + descarte.motivo())
                .toList()
                .toString();
    }

    static boolean ehComandoReset(String texto, String comando) {
        if (texto == null || comando == null) {
            return false;
        }
        String mensagemNormalizada = texto.trim().toLowerCase(Locale.ROOT);
        String comandoNormalizado = comando.trim().toLowerCase(Locale.ROOT);
        return !comandoNormalizado.isBlank() && mensagemNormalizada.equals(comandoNormalizado);
    }

    /**
     * Troca o id de midia da Meta pelos bytes de verdade e persiste no storage proprio. A URL da
     * Meta expira em minutos; guardar so a referencia opaca do nosso storage e o que faz o historico
     * do cliente continuar acessivel depois disso (E11b, secao 3 do prompt).
     */
    private RegistrarMensagemRecebidaUseCase.MensagemRecebida mensagemRecebidaDeMidia(
            UUID leadId,
            TradutorDeCanal.MensagemRecebidaDoCanal mensagem,
            CanalEntradaAtiva canalEntrada,
            ReferenciaDeMensagem referenciaDaMensagem) {
        CanalGateway.MidiaRecebida baixada = canal.baixarMidiaRecebida(mensagem.midiaIdExterno());
        TipoMensagem tipo = TipoMensagem.valueOf(mensagem.tipo());
        String referenciaStorage = armazenamento.salvar(
                baixada.conteudo(), mensagem.nomeArquivo(), baixada.mimetype());

        ObjectNode metadados = json.createObjectNode();
        if (mensagem.nomeArquivo() != null) {
            metadados.put("nome", mensagem.nomeArquivo());
        }
        metadados.put("mimetype", baixada.mimetype());
        metadados.put("tamanho", baixada.conteudo().length);
        if (mensagem.legenda() != null && !mensagem.legenda().isBlank()) {
            metadados.put("legenda", mensagem.legenda());
        }

        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                leadId,
                canalEntrada.canalId(),
                canalEntrada.canalCredencialId(),
                null,
                tipo,
                referenciaStorage,
                metadados.toString(),
                referenciaDaMensagem);
    }

    /**
     * O anexo que o provedor nao entregou: mesmo tipo, sem arquivo, com {@code indisponivel} nos
     * metadados para a tela explicar o que houve. Nome e legenda seguem porque ajudam o atendente a
     * pedir o arquivo certo; nenhum id do provedor entra na conversa.
     */
    private RegistrarMensagemRecebidaUseCase.MensagemRecebida mensagemRecebidaSemArquivo(
            UUID leadId,
            TradutorDeCanal.MensagemRecebidaDoCanal mensagem,
            CanalEntradaAtiva canalEntrada,
            ReferenciaDeMensagem referenciaDaMensagem) {
        ObjectNode metadados = json.createObjectNode();
        metadados.put("indisponivel", true);
        if (mensagem.nomeArquivo() != null) {
            metadados.put("nome", mensagem.nomeArquivo());
        }
        if (mensagem.legenda() != null && !mensagem.legenda().isBlank()) {
            metadados.put("legenda", mensagem.legenda());
        }
        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                leadId,
                canalEntrada.canalId(),
                canalEntrada.canalCredencialId(),
                null,
                TipoMensagem.valueOf(mensagem.tipo()),
                null,
                metadados.toString(),
                referenciaDaMensagem);
    }

    private ReferenciaDeMensagem referenciaDaMensagem(
            TradutorDeCanal.MensagemRecebidaDoCanal mensagem, UUID leadId) {
        String contextoWamid = mensagem.contextoWamid();
        if (contextoWamid == null || contextoWamid.isBlank()) {
            return null;
        }
        return origens.buscarPorWamid(contextoWamid)
                .filter(origem -> leadId.equals(origem.leadId()))
                .map(origem -> MontadorDeReferenciaDeMensagem.resposta(origem, null))
                .orElse(null);
    }

    private void falhar(WebhookEntrada.Pendente pendente, Instant agora, RuntimeException e) {
        if (e instanceof ProvedorTemporariamenteIndisponivelException) {
            if (prazoAbsolutoEstourado(pendente, agora)) {
                entrada.esgotar(pendente.idExterno(), agora, e.toString());
                log.error(
                        "{} evento {} esgotou o prazo absoluto de {} a partir de recebido_em sem o"
                                + " disjuntor fechar. O payload cru fica em webhook_entrada para"
                                + " reprocessamento manual. Ultimo erro: {}",
                        MARCADOR_ALARME,
                        pendente.idExterno(),
                        prazoAbsoluto,
                        e.toString());
            } else {
                entrada.adiar(
                        pendente.idExterno(),
                        proximaTentativa(agora, pendente.tentativas()),
                        e.toString());
                log.warn(
                        "Disjuntor aberto ao processar o evento {}; a linha volta para a fila sem"
                                + " gastar tentativa.",
                        pendente.idExterno(),
                        e);
            }
            return;
        }

        if (e instanceof MidiaRecebidaTemporariamenteIndisponivelException) {
            // Arquivo ainda nao disponivel no provedor: retenta com backoff ate o prazo de midia,
            // sem o teto de tentativas — com ele a linha desistia em ~77s. Passado o prazo, a
            // proxima rodada registra a mensagem sem arquivo em vez de esgotar (E207).
            entrada.reagendar(
                    pendente.idExterno(),
                    proximaTentativa(agora, pendente.tentativas()),
                    e.toString());
            log.warn("Midia do evento {} ainda indisponivel no provedor; sera retentada.",
                    pendente.idExterno());
            return;
        }

        int tentativasFeitas = pendente.tentativas() + 1;

        if (tentativasFeitas >= maximoDeTentativas) {
            entrada.esgotar(pendente.idExterno(), agora, e.toString());
            log.error(
                    "{} evento {} nao pode ser processado apos {} tentativa(s) e NAO virou mensagem na "
                            + "conversa. O payload cru fica em webhook_entrada para reprocessamento "
                            + "manual. Ultimo erro: {}",
                    MARCADOR_ALARME,
                    pendente.idExterno(),
                    tentativasFeitas,
                    e.toString());
        } else {
            entrada.reagendar(
                    pendente.idExterno(),
                    proximaTentativa(agora, pendente.tentativas()),
                    e.toString());
            log.warn("Falha ao processar o evento {}; sera retentado.", pendente.idExterno(), e);
        }
    }

    private Instant proximaTentativa(Instant agora, int tentativasJaFeitas) {
        long fator = 1L << Math.min(Math.max(0, tentativasJaFeitas), 20);
        Duration espera = backoffInicial.multipliedBy(fator);
        if (espera.compareTo(backoffMaximo) > 0) {
            espera = backoffMaximo;
        }
        return agora.plus(espera);
    }

    private boolean prazoDaMidiaEstourado(WebhookEntrada.Pendente pendente, Instant agora) {
        return !agora.isBefore(pendente.recebidoEm().plus(prazoMidia));
    }

    private boolean prazoAbsolutoEstourado(WebhookEntrada.Pendente pendente, Instant agora) {
        return !agora.isBefore(pendente.recebidoEm().plus(prazoAbsoluto));
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotados() {
        return entrada.quantidadeEsgotada();
    }
}
