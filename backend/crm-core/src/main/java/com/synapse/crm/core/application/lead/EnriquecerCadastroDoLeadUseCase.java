package com.synapse.crm.core.application.lead;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.darwin.ColetaDarwinRepositorio;
import com.synapse.crm.core.application.darwin.DadosDarwin;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.ValidadorDeDadosCustomizados;
import com.synapse.crm.core.domain.evento.ColetaCadastralDoLeadAtualizada;
import com.synapse.crm.core.domain.lead.Cpf;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** CAS sob bloqueio: o snapshot foi lido antes do HTTP; nenhuma escrita externa nesta transação. */
@Service
public class EnriquecerCadastroDoLeadUseCase {
    private final LeadRepositorio leads;
    private final CampoCustomizadoRepositorio campos;
    private final TelefoneCanonico telefones;
    private final ColetaDarwinRepositorio cache;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    public EnriquecerCadastroDoLeadUseCase(LeadRepositorio leads, CampoCustomizadoRepositorio campos, TelefoneCanonico telefones,
            ColetaDarwinRepositorio cache, org.springframework.context.ApplicationEventPublisher eventos) {
        this.leads=leads; this.campos=campos; this.telefones=telefones; this.cache=cache; this.eventos=eventos;
    }
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    @Auditable(acao="ENRIQUECER_CADASTRO_DARWIN", entidadeTipo="LEAD", capturarDados=false, atorTipo="AUTOMACAO")
    public String executar(java.util.UUID id, java.util.UUID lease, Lead antes, DadosDarwin.Candidato paciente,
            java.time.Instant proxima, java.time.Instant manual) {
        // Lease e dados comitam juntos; a auditoria ocorre depois, sem reter a conexão do worker.
        if (!cache.concluir(id,lease,"PROCESSANDO",proxima,manual)) return "AGUARDANDO";
        String resultado=aplicar(id,antes,paciente);
        cache.concluir(id,lease,resultado,proxima,manual);
        eventos.publishEvent(new ColetaCadastralDoLeadAtualizada(id));
        return resultado;
    }
    private String aplicar(java.util.UUID id, Lead antes, DadosDarwin.Candidato paciente) {
        if (!id.equals(antes.id())) return "REVISAO_NECESSARIA";
        var atual=leads.porIdParaAtualizacao(id).orElse(null);
        if (atual==null) return "IDENTIFICACAO_PENDENTE";
        if (paciente.cpfInvalido() || Cpf.validoOuNulo(paciente.cpf())==null
                || !Objects.equals(Cpf.validoOuNulo(antes.cpf()), paciente.cpf())) return "REVISAO_NECESSARIA";
        if (!Objects.equals(antes.cpf(), atual.cpf()) || !Objects.equals(antes.nome(), atual.nome())
                || !Objects.equals(antes.telefone(), atual.telefone())
                || !Objects.equals(antes.dadosCustomizados().get("data_nascimento"), atual.dadosCustomizados().get("data_nascimento")))
            return "REVISAO_NECESSARIA";
        boolean nomeAusente=atual.nome().isBlank() || Objects.equals(atual.nome(), atual.telefone());
        boolean revisao=!nomeAusente && paciente.nome()!=null && !atual.nome().equals(paciente.nome());
        String nome=nomeAusente ? paciente.nome() : null;
        var novo=new DadosDeAtualizacaoLead(nome,null,null,null,null,null,null,null,null,null,null,null,null).aplicarEm(atual,telefones);
        Object nascimento=atual.dadosCustomizados().get("data_nascimento");
        if (paciente.nascimento()!=null) {
            if (nascimento==null || nascimento.toString().isBlank()) {
                var campo=campos.porChave("data_nascimento").filter(c->c.tipo()==TipoCampoCustomizado.DATA).orElse(null);
                if (campo==null) return "CONFIGURACAO_INCOMPLETA";
                var dados=new LinkedHashMap<>(atual.dadosCustomizados());
                dados.putAll(ValidadorDeDadosCustomizados.validar(Map.of("data_nascimento",paciente.nascimento().toString()),List.of(campo)));
                novo=novo.comDadosCustomizados(dados);
            } else if (!nascimento.toString().startsWith(paciente.nascimento().toString())) revisao=true;
        }
        if (!novo.equals(atual)) leads.salvar(novo).orElseThrow();
        return revisao ? "REVISAO_NECESSARIA" : paciente.nascimento()==null || paciente.nome()==null ? "DADOS_PARCIAIS" : "ATUALIZADO";
    }
}
