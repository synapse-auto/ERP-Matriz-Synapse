package com.synapse.crm.app.equipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.springframework.boot.SpringApplication;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;

/** Aplicacao real para inspecao headed: banco/Redis descartaveis e somente seed sintetico. */
public final class UsuariosTesteLocal extends PostgresIT {
    private UsuariosTesteLocal() {}

    public static void main(String[] args) throws InterruptedException {
        List<String> propriedades=new ArrayList<>(List.of(
                "--spring.profiles.active=dev", "--server.port=8097",
                "--synapse.seguranca.jwt-segredo="+SEGREDO_JWT_DE_TESTE,
                "--spring.data.redis.host="+REDIS.getHost(),
                "--spring.data.redis.port="+REDIS.getMappedPort(6379),
                "--synapse.agendamento.habilitado=false",
                "--synapse.saude.critica.monitoramento-habilitado=false",
                "--synapse.canal.whatsapp.provedor=fake",
                "--synapse.seguranca.frontend-origem=http://localhost:3017"));
        for(String pool:List.of("general","chat")) {
            String prefixo="--synapse.datasource."+pool+".";
            propriedades.add(prefixo+"url="+POSTGRES.getJdbcUrl());
            propriedades.add(prefixo+"username="+POSTGRES.getUsername());
            propriedades.add(prefixo+"password="+POSTGRES.getPassword());
        }
        try(var contexto=SpringApplication.run(SynapseCrmApplication.class,propriedades.toArray(String[]::new))) {
            System.out.println("Preview sintetico pronto: http://localhost:8097 — sem acesso a producao.");
            new CountDownLatch(1).await();
        }
    }
}
