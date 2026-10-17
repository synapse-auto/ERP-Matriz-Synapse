package com.synapse.crm.equipe.application.usuario;

import java.util.List;
import java.util.UUID;

/** Porta exclusiva da classificacao administrativa; nao fornece identidade nem historico comercial. */
public interface ClassificacaoDeTesteRepositorio {
    boolean administradorVigente(UUID ator);
    boolean contaDeTeste(UUID usuario);
    List<Vinculo> vinculos(UUID usuario);
    void classificar(UUID usuario, boolean teste);
    void registrarBloqueio(UUID usuario, String operacao);
    java.util.Set<UUID> usuariosAtivos();

    record Vinculo(String recurso, String registroId) {}
}
