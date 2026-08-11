package com.synapse.crm.core.application.lead;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.etapa.EtapaNaoEncontradaException;
import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.domain.campocustomizado.ValidadorDeDadosCustomizados;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.evento.EtapaDoLeadAlterada;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Edita a ficha de um lead.
 *
 * <p>A checagem de propriedade nao esta em SpEL de {@code @PreAuthorize}: ela vem do repositorio,
 * que so devolve o lead se for visivel a quem pediu. Vazio vira 404 — nunca 403 —, porque 403
 * confirmaria ao atendente que o lead existe e esta com um colega.
 *
 * <p>Contadores nao entram aqui: eles so mudam por incremento relativo, na transacao que cria o
 * atendimento ou a mensagem.
 */
@Service
public class AtualizarLeadUseCase {

    private final LeadRepositorio leads;
    private final CampoCustomizadoRepositorio camposCustomizados;
    private final EtapaRepositorio etapas;
    private final UsuarioContext usuarioContext;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AtualizarLeadUseCase(
            LeadRepositorio leads,
            CampoCustomizadoRepositorio camposCustomizados,
            EtapaRepositorio etapas,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.leads = leads;
        this.camposCustomizados = camposCustomizados;
        this.etapas = etapas;
        this.usuarioContext = usuarioContext;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Optional<Lead> executar(UUID id, DadosDeAtualizacaoLead dados) {
        return leads.porId(id).flatMap(atual -> {
            Lead atualizado = dados.aplicarEm(atual);
            // dadosCustomizados so e tocado quando o cliente efetivamente mandou algo: ausencia no
            // corpo do PUT significa "nao mexa", igual a qualquer outro campo desta ficha.
            if (dados.dadosCustomizados() != null) {
                Map<String, Object> validado = ValidadorDeDadosCustomizados.validar(
                        dados.dadosCustomizados(), camposCustomizados.listarTodos());
                atualizado = atualizado.comDadosCustomizados(validado);
            }
            EtapaAtendimento etapaAnterior = etapa(atual.etapaAtendimentoId());
            EtapaAtendimento etapaNova = etapa(atualizado.etapaAtendimentoId());
            Optional<Lead> salvo = leads.salvar(atualizado);
            if (!Objects.equals(atual.etapaAtendimentoId(), atualizado.etapaAtendimentoId())) {
                salvo.ifPresent(lead -> eventos.publishEvent(new EtapaDoLeadAlterada(
                        lead.id(),
                        etapaAnterior,
                        etapaNova,
                        lead.atendenteResponsavelId(),
                        usuarioContext.atual().id(),
                        Instant.now(relogio))));
            }
            return salvo;
        });
    }

    private EtapaAtendimento etapa(UUID id) {
        return id == null ? null : etapas.porId(id).orElseThrow(() -> new EtapaNaoEncontradaException(id));
    }
}
