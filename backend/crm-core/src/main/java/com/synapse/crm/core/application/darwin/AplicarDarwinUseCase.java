package com.synapse.crm.core.application.darwin;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lead.AtualizarLeadUseCase;
import com.synapse.crm.core.application.lead.DadosDeAtualizacaoLead;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.Cpf;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class AplicarDarwinUseCase {
    private final LeadRepositorio leads;
    private final AtualizarLeadUseCase atualizar;
    private final DarwinGateway darwin;
    private final UsuarioContext usuarios;
    public AplicarDarwinUseCase(LeadRepositorio leads, AtualizarLeadUseCase atualizar, DarwinGateway darwin, UsuarioContext usuarios) {
        this.leads = leads; this.atualizar = atualizar; this.darwin = darwin; this.usuarios = usuarios;
    }
    @PreAuthorize("isAuthenticated() and @capacidades.permite('clientes.acessar') and @capacidades.permite('contatos.editar')")
    @Auditable(acao = "APLICAR_DADOS_DARWIN", entidadeTipo = "LEAD_DARWIN")
    @Transactional
    public DadosDarwin.Aplicacao executar(UUID id, String confirmacao, boolean nome, boolean cpf, boolean email) {
        darwin.exigirDisponivel();
        // Verifica acesso antes mesmo de interpretar o comprovante; colega/inexistente seguem 404.
        var atual = leads.porIdParaAtualizacao(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var recibo = darwin.verificar(confirmacao);
        if (!id.equals(recibo.leadId()) || !usuarios.atual().id().equals(recibo.usuarioId()) || (!nome && !cpf && !email)) invalido();
        if (!Objects.equals(atual.telefone(), recibo.telefone())) conflito();
        var encontrado = recibo.candidato();
        // Troca concorrente de CPF invalida também a aplicação só do nome.
        // Reaplicar o mesmo resultado continua idempotente.
        if (!Objects.equals(atual.cpf(), recibo.cpfAnterior()) && !Objects.equals(atual.cpf(), encontrado.cpf())) conflito();
        if ((nome && encontrado.nome() == null) || (cpf && (encontrado.cpfInvalido() || Cpf.validoOuNulo(encontrado.cpf()) == null))) invalido();
        if (email && (encontrado.emailInvalido() || encontrado.email() == null)) invalido();
        var campos = new ArrayList<String>();
        if (nome && !Objects.equals(atual.nome(), encontrado.nome())) {
            if (!Objects.equals(atual.nome(), recibo.nomeAnterior())) conflito();
            campos.add("nome");
        }
        if (cpf && !Objects.equals(atual.cpf(), encontrado.cpf())) {
            if (!Objects.equals(atual.cpf(), recibo.cpfAnterior())) conflito();
            campos.add("cpf");
        }
        if (email && !Objects.equals(atual.email(), encontrado.email())) {
            if (!Objects.equals(atual.email(), recibo.emailAnterior())) conflito();
            campos.add("email");
        }
        if (!campos.isEmpty()) atualizar.executar(id, new DadosDeAtualizacaoLead(
                campos.contains("nome") ? encontrado.nome() : null, null, null,
                campos.contains("email") ? encontrado.email() : null,
                campos.contains("cpf") ? encontrado.cpf() : null, null, null, null, null, null, null, null, null))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new DadosDarwin.Aplicacao(id, "DARWIN_MEDICO", java.util.List.copyOf(campos));
    }
    private static void invalido() { throw new FalhaDarwin(FalhaDarwin.Codigo.CONSULTA_INVALIDA); }
    private static void conflito() { throw new FalhaDarwin(FalhaDarwin.Codigo.CONCORRENCIA); }
}
