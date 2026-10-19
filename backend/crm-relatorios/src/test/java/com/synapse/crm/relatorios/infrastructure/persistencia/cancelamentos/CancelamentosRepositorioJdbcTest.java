package com.synapse.crm.relatorios.infrastructure.persistencia.cancelamentos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

class CancelamentosRepositorioJdbcTest {
    @Test void intervaloExcluiNulosEOrdemDoSqlRealCoincideComIndiceEPlano() {
        var jdbc=mock(JdbcTemplate.class);
        var repo=new CancelamentosRepositorioJdbc(jdbc,new ObjectMapper());
        var filtro=new FiltroCancelamentos(FiltroCancelamentos.BaseTemporal.REGISTRO,Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),null,null,FiltroCancelamentos.Descricao.TODAS,null,0,25);
        var pagina=repo.listar(filtro,new UsuarioAutenticado(UUID.randomUUID(),PapelUsuario.GESTOR,false));
        assertThat(pagina.itens()).isEmpty();
        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),org.mockito.ArgumentMatchers.<RowMapper<CancelamentoAgendamento>>any(),any(Object[].class));
        assertThat(sql.getValue()).contains("recebido_em>=? AND recebido_em<?", "ORDER BY recebido_em DESC,id DESC LIMIT ? OFFSET ?")
            .doesNotContain("NULLS LAST", "count(", "SELECT *");
    }
}
