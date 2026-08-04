package com.synapse.crm.core.application.mensagemprogramada;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarMensagensProgramadasUseCase {
    private final MensagemProgramadaRepositorio mensagens;
    public ListarMensagensProgramadasUseCase(MensagemProgramadaRepositorio mensagens) { this.mensagens = mensagens; }
    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public PaginaMensagensProgramadas executar(FiltroMensagensProgramadas filtro) { return mensagens.listar(filtro); }
}
