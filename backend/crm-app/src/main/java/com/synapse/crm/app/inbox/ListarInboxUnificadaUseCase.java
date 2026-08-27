package com.synapse.crm.app.inbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;
import com.synapse.crm.equipe.application.chat.ListarConversasChatUseCase;

/** Compõe páginas limitadas das duas fontes e aplica a ordenação global no backend. */
@Service
public class ListarInboxUnificadaUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(ListarInboxUnificadaUseCase.class);
    private static final Comparator<InboxUnificada.Item> ORDEM = Comparator
            .comparing(InboxUnificada.Item::ultimaMensagemEm,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(InboxUnificada.Item::identificadorVisual, Comparator.reverseOrder());

    private final ListarAtendimentosVisiveisUseCase clientes;
    private final ListarConversasChatUseCase equipe;
    private final FeatureService features;

    public ListarInboxUnificadaUseCase(
            ListarAtendimentosVisiveisUseCase clientes,
            ListarConversasChatUseCase equipe,
            FeatureService features) {
        this.clientes = clientes;
        this.equipe = equipe;
        this.features = features;
    }

    @PreAuthorize("isAuthenticated()")
    public InboxUnificada executar(VisaoAtendimento visao, int limite, String cursor) {
        int tamanho = Math.max(1, Math.min(limite, 100));
        List<InboxUnificada.Item> itens = new ArrayList<>();
        Cursor apos = decodificar(cursor);
        try {
            clientes.executarPaginado(visao, tamanho + 1, apos == null ? null : apos.data(),
                    apos == null ? null : apos.id()).stream().map(InboxUnificada.Item::cliente).forEach(itens::add);
        } catch (RuntimeException erro) {
            LOG.warn("Não foi possível carregar a fonte de atendimentos da inbox", erro);
        }
        if (visao == VisaoAtendimento.TODOS && chatHabilitado()) {
            try {
                equipe.executarPaginado(tamanho + 1, apos == null ? null : apos.data(),
                        apos == null ? null : apos.id()).stream().map(this::paraEquipe).forEach(itens::add);
            } catch (RuntimeException erro) {
                LOG.warn("Não foi possível carregar a fonte de chat interno da inbox", erro);
            }
        }

        itens.sort(ORDEM);
        if (apos != null) {
            itens.removeIf(item -> ORDEM.compare(item, apos.item()) <= 0);
        }
        boolean temMais = itens.size() > tamanho;
        List<InboxUnificada.Item> pagina = List.copyOf(itens.subList(0, Math.min(tamanho, itens.size())));
        String proximo = temMais ? codificar(pagina.get(pagina.size() - 1)) : null;
        return new InboxUnificada(pagina, proximo);
    }

    private boolean chatHabilitado() {
        try {
            return features.habilitadas().contains("chat_interno");
        } catch (RuntimeException erro) {
            LOG.warn("Não foi possível consultar a feature flag chat_interno; ocultando a fonte interna", erro);
            return false;
        }
    }

    private InboxUnificada.Item paraEquipe(ChatInternoRepositorio.ConversaResumo conversa) {
        return InboxUnificada.Item.equipe(
                conversa.id(),
                conversa.participantes(),
                conversa.ultimaMensagem(),
                conversa.ultimaMensagemEm(),
                conversa.naoLidas(),
                conversa.tipo().name());
    }

    private static String codificar(InboxUnificada.Item item) {
        String valor = (item.ultimaMensagemEm() == null ? "" : item.ultimaMensagemEm().toString())
                + "|" + item.identificadorVisual();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodificar(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String valor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = valor.split("\\|", 2);
            return new Cursor(partes[0].isBlank() ? null : Instant.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException erro) {
            return null;
        }
    }

    private record Cursor(Instant data, UUID id) {
        InboxUnificada.Item item() {
            return InboxUnificada.Item.equipe(id, "", "", data, 0, "DIRETA");
        }
    }
}
