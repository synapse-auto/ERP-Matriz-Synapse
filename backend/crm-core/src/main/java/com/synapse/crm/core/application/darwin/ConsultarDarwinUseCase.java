package com.synapse.crm.core.application.darwin;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.lead.Cpf;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ConsultarDarwinUseCase {
    private final LerLeadParaDarwinUseCase ler;
    private final DarwinGateway darwin;
    private final UsuarioContext usuarios;
    public ConsultarDarwinUseCase(LerLeadParaDarwinUseCase ler, DarwinGateway darwin, UsuarioContext usuarios) {
        this.ler = ler; this.darwin = darwin; this.usuarios = usuarios;
    }
    @PreAuthorize("isAuthenticated() and @capacidades.permite('clientes.acessar') and @capacidades.permite('contatos.editar')")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DadosDarwin.Previa executar(UUID id, String cpfInformado) {
        darwin.exigirDisponivel();
        var lead = ler.executar(id);
        String documento = cpfInformado == null ? lead.cpf() : cpfInformado.trim();
        if (documento == null || documento.isBlank()) {
            return new DadosDarwin.Previa(DadosDarwin.Estado.SEM_IDENTIDADE, List.of(), false, lead.nome(), lead.cpf(), null);
        }
        String cpf = Cpf.validoOuNulo(documento);
        if (cpf == null) throw new FalhaDarwin(FalhaDarwin.Codigo.CPF_INVALIDO);
        var resultado = darwin.consultar(cpf);
        // O médico devolve no máximo um cadastro; campos legados da prévia ficam compatíveis.
        var pares = resultado.candidatos().stream().map(c -> java.util.Arrays.asList(c.nome(), c.cpf(), c.cpfInvalido())).distinct().toList();
        var estado = pares.isEmpty() ? DadosDarwin.Estado.SEM_RESULTADO
                : pares.size() > 1 ? DadosDarwin.Estado.AMBIGUO : DadosDarwin.Estado.ENCONTRADO;
        var confirmacao = estado == DadosDarwin.Estado.ENCONTRADO
                ? darwin.comprovar(usuarios.atual().id(), lead, resultado.candidatos().getFirst()) : null;
        return new DadosDarwin.Previa(estado, resultado.candidatos(), resultado.limiteAtingido(), lead.nome(), lead.cpf(), confirmacao);
    }
}
