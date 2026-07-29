package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lead.AtualizarLeadUseCase;
import com.synapse.crm.core.application.lead.DadosDeAtualizacaoLead;
import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.ListarLeadsUseCase;
import com.synapse.crm.core.application.lead.ObterLeadUseCase;
import com.synapse.crm.core.domain.campocustomizado.DadosCustomizadosInvalidosException;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Leitura e edicao de leads.
 *
 * <p>O recorte por papel acontece no repositorio, nunca aqui e nunca no frontend. "Nao e seu" e
 * "nao existe" respondem igual — 404 — de proposito.
 */
@RestController
@RequestMapping("/api/v1/leads")
class LeadController {

    private final ListarLeadsUseCase listar;
    private final ObterLeadUseCase obter;
    private final AtualizarLeadUseCase atualizar;

    LeadController(ListarLeadsUseCase listar, ObterLeadUseCase obter, AtualizarLeadUseCase atualizar) {
        this.listar = listar;
        this.obter = obter;
        this.atualizar = atualizar;
    }

    /** Listagem: devolve o resumo, que nao carrega notas nem resumo de IA. */
    @GetMapping
    List<LeadDaLista> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusBasicoLead status) {
        return listar.executar(new FiltroLead(busca, status)).stream()
                .map(LeadDaLista::de)
                .toList();
    }

    @GetMapping("/{id}")
    FichaDoLead porId(@PathVariable UUID id) {
        return obter.executar(id).map(FichaDoLead::de).orElseThrow(LeadController::naoEncontrado);
    }

    @PutMapping("/{id}")
    FichaDoLead atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizacaoRequisicao requisicao) {
        return atualizar
                .executar(id, requisicao.paraDados())
                .map(FichaDoLead::de)
                .orElseThrow(LeadController::naoEncontrado);
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead nao encontrado");
    }

    /** Chave de campo customizado nao cadastrada, tipo incompativel, ou obrigatorio ausente. */
    @ExceptionHandler(DadosCustomizadosInvalidosException.class)
    ProblemDetail aoReceberDadosCustomizadosInvalidos(DadosCustomizadosInvalidosException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Dados customizados invalidos");
        return problema;
    }

    /** Ficha completa, so na consulta por id. */
    record FichaDoLead(
            UUID id,
            String nome,
            String fotoUrl,
            String telefone,
            String email,
            String cpf,
            String empresa,
            String localizacao,
            UUID canalOrigemId,
            StatusBasicoLead status,
            UUID etapaAtendimentoId,
            UUID atendenteResponsavelId,
            String notas,
            String resumoIa,
            int numAtendimentos,
            int numMensagens,
            Instant criadoEm,
            Map<String, Object> dadosCustomizados) {

        static FichaDoLead de(Lead lead) {
            return new FichaDoLead(
                    lead.id(), lead.nome(), lead.fotoUrl(), lead.telefone(), lead.email(), lead.cpf(),
                    lead.empresa(), lead.localizacao(), lead.canalOrigemId(), lead.statusBasico(),
                    lead.etapaAtendimentoId(), lead.atendenteResponsavelId(), lead.notas(),
                    lead.resumoIa(), lead.numAtendimentos(), lead.numMensagens(), lead.criadoEm(),
                    lead.dadosCustomizados());
        }
    }

    /**
     * Corpo da edicao de ficha. Campo ausente significa "nao mexa".
     *
     * <p>Nao aceita {@code atendenteResponsavelId}: trocar o dono e transferencia de lead, nao
     * edicao de ficha, e teria virado uma forma de largar um lead dificil ou puxar o de um colega.
     * Nao aceita {@code resumoIa} porque quem escreve e a Automacao.
     */
    record AtualizacaoRequisicao(
            @Size(max = 150) String nome,
            String fotoUrl,
            @Size(max = 30) String telefone,
            @Size(max = 200) String email,
            @Size(max = 14) String cpf,
            @Size(max = 150) String empresa,
            @Size(max = 200) String localizacao,
            UUID canalOrigemId,
            StatusBasicoLead status,
            UUID etapaAtendimentoId,
            String notas,
            Map<String, Object> dadosCustomizados) {

        DadosDeAtualizacaoLead paraDados() {
            return new DadosDeAtualizacaoLead(
                    nome, fotoUrl, telefone, email, cpf, empresa, localizacao, canalOrigemId, status,
                    etapaAtendimentoId, notas, dadosCustomizados);
        }
    }
}
