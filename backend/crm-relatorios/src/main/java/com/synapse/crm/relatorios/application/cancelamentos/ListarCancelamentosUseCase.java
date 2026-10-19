package com.synapse.crm.relatorios.application.cancelamentos;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Leitura local protegida por RLS no pool geral; nunca reserva o pool do chat. */
@Service
public class ListarCancelamentosUseCase {
    private final LeituraDeCancelamentos fonte;
    private final UsuarioContext usuarios;
    private final PoliticaConsultaCancelamentos politica;
    public ListarCancelamentosUseCase(LeituraDeCancelamentos fonte, UsuarioContext usuarios, PoliticaConsultaCancelamentos politica) {
        this.fonte = fonte; this.usuarios = usuarios; this.politica = politica;
    }
    @PreAuthorize("hasAnyRole('ATENDENTE','OPERADOR','SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('cancelamentos.ver')")
    @Transactional(readOnly=true, timeoutString="${synapse.cancelamentos.timeout-segundos}")
    public LeituraDeCancelamentos.Pagina executar(FiltroCancelamentos.BaseTemporal base, LocalDate inicio,
            LocalDate fim, String profissional, String tipo, FiltroCancelamentos.Descricao descricao,
            String nome, int pagina, Integer tamanho) {
        return fonte.listar(politica.filtro(base,inicio,fim,profissional,tipo,descricao,nome,pagina,tamanho), usuarios.atual());
    }
}
