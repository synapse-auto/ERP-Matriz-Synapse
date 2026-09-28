package com.synapse.crm.atendimento.application.template;

import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.RegraDeTemplateRestrito;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Ponto unico onde o papel do usuario encontra a {@link RegraDeTemplateRestrito}. Listagem,
 * criacao, edicao, exclusao, envio e novo contato passam por aqui — nenhum deles decide sozinho.
 *
 * <p>Decisoes por nome ({@link #exigirUso}) sao locais: nao consultam o provedor, por isso cabem
 * no caminho de envio de mensagem. So {@link #exigirVarianteVisivel}, usada por editar e excluir
 * (fora do caminho de mensagem), consulta a listagem do provedor — e apenas para quem nao e
 * administrador, porque o ID opaco da Meta nao diz o nome.
 */
@Service
public class AutorizacaoDeTemplates {

    private final RegraDeTemplateRestrito regra;
    private final UsuarioContext usuarioContext;
    private final CanalGateway canal;

    public AutorizacaoDeTemplates(
            RegraDeTemplateRestrito regra, UsuarioContext usuarioContext, CanalGateway canal) {
        this.regra = regra;
        this.usuarioContext = usuarioContext;
        this.canal = canal;
    }

    /** Remove da lista o que o papel atual nao pode ver. */
    public List<TemplateDoCanal> visiveis(List<TemplateDoCanal> templates) {
        PapelUsuario papel = papelAtual();
        return templates.stream()
                .filter(template -> regra.permite(papel, template.nome()))
                .toList();
    }

    /** Criar, enviar ou abrir contato com este nome exige que o papel atual alcance o template. */
    public void exigirUso(String nome) {
        if (regra.restringe(nome) && !regra.permite(papelAtual(), nome)) {
            throw new TemplateRestritoException();
        }
    }

    /**
     * Editar e excluir so alcancam variantes que a listagem autorizada mostraria ao usuario. Para
     * nao administradores, o ID precisa existir entre os templates visiveis e, quando informado, o
     * nome precisa ser o dessa variante — um nome comum nao acoberta um ID restrito.
     */
    public void exigirVarianteVisivel(String id, String nomeInformado) {
        if (regra.restringe(nomeInformado)) {
            exigirUso(nomeInformado);
        }
        if (papelAtual() == PapelUsuario.ADMINISTRADOR) {
            return;
        }
        boolean alcancada = visiveis(canal.listarTemplates()).stream()
                .anyMatch(template -> template.id().equals(id)
                        && (nomeInformado == null || template.nome().equals(nomeInformado)));
        if (!alcancada) {
            throw new TemplateForaDoAlcanceException(id);
        }
    }

    private PapelUsuario papelAtual() {
        return usuarioContext.atual().papel();
    }
}
