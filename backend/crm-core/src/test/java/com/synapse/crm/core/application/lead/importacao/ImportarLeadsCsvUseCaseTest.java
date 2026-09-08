package com.synapse.crm.core.application.lead.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.application.tag.TagRepositorio;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;
import com.synapse.crm.core.domain.tag.Tag;

class ImportarLeadsCsvUseCaseTest {

    private final ImportacaoLeadsRepositorio repositorio = mock(ImportacaoLeadsRepositorio.class);
    private final EtapaRepositorio etapas = mock(EtapaRepositorio.class);
    private final TagRepositorio tags = mock(TagRepositorio.class);
    private ImportarLeadsCsvUseCase importar;
    private final UUID etapaId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    @BeforeEach
    void configurar() {
        importar = new ImportarLeadsCsvUseCase(new PrepararImportacaoLeadsCsv("55"), repositorio, etapas, tags);
        when(etapas.listarEmOrdem())
                .thenReturn(List.of(new EtapaAtendimento(etapaId, "Proposta", (short) 1, "#000000", ResultadoEtapa.EM_ANDAMENTO)));
        when(tags.listarTodas()).thenReturn(List.of(new Tag(tagId, "VIP", "#000000", null)));
        when(repositorio.telefonesExistentes(any())).thenReturn(Set.of("5561999999999"));
    }

    @Test
    void previewResolveCamposOpcionaisEIgnoraTelefoneJaExistente() throws Exception {
        var resultado = importar.preview(new StringReader("""
                nome,empresa,telefone,etapa,tags
                Novo,Acme,61988888888,proposta,vip
                Existente,Outra,61999999999,Proposta,VIP
                """));

        assertThat(resultado.totalDeLinhas()).isEqualTo(2);
        assertThat(resultado.validas()).isEqualTo(1);
        assertThat(resultado.jaExistiam()).isEqualTo(1);
        assertThat(resultado.recusados()).isEmpty();
    }

    @Test
    void confirmarInsereSomenteLinhasNovasComEtapaETagsResolvidas() throws Exception {
        when(repositorio.inserir(any())).thenReturn(1);

        var resultado = importar.confirmar(new StringReader("""
                nome,empresa,telefone,etapa,tags
                Novo,Acme,61988888888,Proposta,VIP
                """));

        assertThat(resultado.validas()).isEqualTo(1);
        verify(repositorio).inserir(any());
    }

    @Test
    void etapaOuTagDesconhecidaRecusaLinhaSemGravar() throws Exception {
        var resultado = importar.preview(new StringReader("""
                nome,telefone,etapa,tags
                Novo,61988888888,Inexistente,VIP
                """));

        assertThat(resultado.validas()).isZero();
        assertThat(resultado.recusados()).extracting(PrepararImportacaoLeadsCsv.LinhaRecusada::motivo)
                .containsExactly("etapa desconhecida: Inexistente");
    }
}
