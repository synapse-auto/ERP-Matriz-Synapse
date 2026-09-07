package com.synapse.crm.core.application.lead.importacao;

import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.application.tag.TagRepositorio;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.tag.Tag;

/** Prepara o CSV e aplica-o somente depois da confirmacao explicita da gestao. */
@Service
public class ImportarLeadsCsvUseCase {

    private static final String SO_GESTAO = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')";

    private final PrepararImportacaoLeadsCsv parser;
    private final ImportacaoLeadsRepositorio repositorio;
    private final EtapaRepositorio etapas;
    private final TagRepositorio tags;

    public ImportarLeadsCsvUseCase(
            PrepararImportacaoLeadsCsv parser,
            ImportacaoLeadsRepositorio repositorio,
            EtapaRepositorio etapas,
            TagRepositorio tags) {
        this.parser = parser;
        this.repositorio = repositorio;
        this.etapas = etapas;
        this.tags = tags;
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional(readOnly = true)
    public Resultado preview(Reader csv) throws IOException {
        return preparar(csv).resultado();
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional
    public Resultado confirmar(Reader csv) throws IOException {
        Preparacao preparacao = preparar(csv);
        int inseridos = repositorio.inserir(preparacao.leads());
        int concorrentes = preparacao.leads().size() - inseridos;
        return new Resultado(
                preparacao.resultado().totalDeLinhas(),
                inseridos,
                preparacao.resultado().jaExistiam() + concorrentes,
                preparacao.resultado().recusados());
    }

    private Preparacao preparar(Reader csv) throws IOException {
        PrepararImportacaoLeadsCsv.Resultado bruto = parser.executar(csv);
        Map<String, EtapaAtendimento> etapasPorNome = new HashMap<>();
        for (EtapaAtendimento etapa : etapas.listarEmOrdem()) {
            etapasPorNome.put(normalizar(etapa.nome()), etapa);
        }
        Map<String, Tag> tagsPorNome = new HashMap<>();
        for (Tag tag : tags.listarTodas()) {
            tagsPorNome.put(normalizar(tag.nome()), tag);
        }

        Set<String> existentes = repositorio.telefonesExistentes(
                bruto.aceitos().stream().map(PrepararImportacaoLeadsCsv.LeadImportavel::telefone).toList());
        List<PrepararImportacaoLeadsCsv.LinhaRecusada> recusados = new ArrayList<>(bruto.recusados());
        List<ImportacaoLeadsRepositorio.LeadParaInsercao> leads = new ArrayList<>();
        int jaExistiam = 0;

        for (PrepararImportacaoLeadsCsv.LeadImportavel lead : bruto.aceitos()) {
            int linha = bruto.linhaPorTelefone().getOrDefault(lead.telefone(), 0);
            EtapaAtendimento etapa = null;
            if (!lead.etapa().isBlank()) {
                etapa = etapasPorNome.get(normalizar(lead.etapa()));
                if (etapa == null) {
                    recusados.add(new PrepararImportacaoLeadsCsv.LinhaRecusada(
                            linha, "etapa desconhecida: " + lead.etapa()));
                    continue;
                }
            }
            List<java.util.UUID> tagsResolvidas = new ArrayList<>();
            boolean tagInvalida = false;
            for (String nomeTag : lead.tags()) {
                Tag tag = tagsPorNome.get(normalizar(nomeTag));
                if (tag == null) {
                    recusados.add(new PrepararImportacaoLeadsCsv.LinhaRecusada(
                            linha, "tag desconhecida: " + nomeTag));
                    tagInvalida = true;
                } else {
                    tagsResolvidas.add(tag.id());
                }
            }
            if (tagInvalida) continue;
            if (existentes.contains(lead.telefone())) {
                jaExistiam++;
                continue;
            }
            leads.add(new ImportacaoLeadsRepositorio.LeadParaInsercao(
                    lead.nome(),
                    lead.telefone(),
                    vazioParaNulo(lead.empresa()),
                    vazioParaNulo(lead.cpf()),
                    vazioParaNulo(lead.localizacao()),
                    etapa == null ? null : etapa.id(),
                    tagsResolvidas));
        }

        return new Preparacao(
                leads,
                new Resultado(bruto.totalDeLinhas(), leads.size(), jaExistiam, List.copyOf(recusados)));
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private record Preparacao(
            List<ImportacaoLeadsRepositorio.LeadParaInsercao> leads, Resultado resultado) {}

    public record Resultado(
            int totalDeLinhas,
            int validas,
            int jaExistiam,
            List<PrepararImportacaoLeadsCsv.LinhaRecusada> recusados) {}
}
