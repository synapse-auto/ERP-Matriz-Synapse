package com.synapse.crm.core.application.lead.exportacao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.lead.PaginaDeLeads;
import com.synapse.crm.core.application.tag.ListarTagsDosLeadsUseCase;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadResumo;

/** Gera o CSV da Agenda usando o mesmo recorte de visibilidade e filtro da listagem. */
@Service
public class ExportarLeadsCsvUseCase {

    private static final String SO_GESTAO = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')";
    private static final int TAMANHO_DA_PAGINA = 200;
    private static final String CABECALHO = "nome,empresa,telefone,cnpj/cpf,cidade,etapa,tags\n";

    private final LeadRepositorio leads;
    private final ListarTagsDosLeadsUseCase tagsDosLeads;
    private final EtapaRepositorio etapas;

    public ExportarLeadsCsvUseCase(
            LeadRepositorio leads, ListarTagsDosLeadsUseCase tagsDosLeads, EtapaRepositorio etapas) {
        this.leads = leads;
        this.tagsDosLeads = tagsDosLeads;
        this.etapas = etapas;
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional(readOnly = true)
    public byte[] executar(FiltroDeLeads filtro) {
        Map<UUID, String> nomesDeEtapa = new HashMap<>();
        etapas.listarEmOrdem().forEach(etapa -> nomesDeEtapa.put(etapa.id(), etapa.nome()));
        List<Lead> exportados = new ArrayList<>();
        int pagina = 0;
        while (true) {
            PaginaDeLeads resultado = leads.listar(filtro, pagina, TAMANHO_DA_PAGINA);
            for (LeadResumo resumo : resultado.leads()) {
                leads.porId(resumo.id()).ifPresent(exportados::add);
            }
            if (!resultado.temMais()) break;
            pagina++;
        }

        Map<UUID, List<com.synapse.crm.core.domain.tag.Tag>> tagsPorLead = tagsDosLeads.executar(
                exportados.stream().map(Lead::id).toList());
        StringBuilder csv = new StringBuilder(CABECALHO);
        for (Lead lead : exportados) {
            String etapa = lead.etapaAtendimentoId() == null
                    ? ""
                    : nomesDeEtapa.getOrDefault(lead.etapaAtendimentoId(), "");
            String tags = tagsPorLead.getOrDefault(lead.id(), List.of()).stream()
                    .map(tag -> tag.nome())
                    .collect(java.util.stream.Collectors.joining(";"));
            adicionarLinha(csv, lead.nome(), lead.empresa(), lead.telefone(), lead.cpf(), lead.localizacao(), etapa, tags);
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void adicionarLinha(StringBuilder csv, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(escapar(valores[i]));
        }
        csv.append('\n');
    }

    private static String escapar(String valor) {
        if (valor == null) return "";
        String normalizado = valor.replace("\r", " ").replace("\n", " ");
        if (normalizado.indexOf(',') >= 0 || normalizado.indexOf('"') >= 0) {
            return '"' + normalizado.replace("\"", "\"\"") + '"';
        }
        return normalizado;
    }
}
