package com.synapse.crm.automacaoconfig.application.regras;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.automacaoconfig.domain.regras.*;

@Service
public class SalvarRegraFidelizacaoUseCase {
    private final RegraFidelizacaoRepositorio repositorio;
    public SalvarRegraFidelizacaoUseCase(RegraFidelizacaoRepositorio repositorio) { this.repositorio = repositorio; }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFidelizacao criar(int dias, String mensagem, boolean ativo) { validar(dias); return repositorio.salvar(new RegraFidelizacao(UUID.randomUUID(), dias, ValidadorDeMensagemDeAutomacao.validar(mensagem), ativo)); }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFidelizacao atualizar(UUID id, int dias, String mensagem, boolean ativo) { validar(dias); repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id)); return repositorio.salvar(new RegraFidelizacao(id, dias, ValidadorDeMensagemDeAutomacao.validar(mensagem), ativo)); }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void excluir(UUID id) { repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id)); repositorio.excluir(id); }
    private static void validar(int dias) { if (dias <= 0) throw new RegraAutomacaoInvalidaException("Os dias sem contato devem ser maiores que zero"); }
}
