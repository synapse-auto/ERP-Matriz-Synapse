/**
 * Shared kernel: value objects e tipos base compartilhados entre os modulos.
 *
 * <p>Java puro por contrato. Este modulo nao tem nenhuma dependencia de producao e o
 * maven-enforcer-plugin (ver pom.xml) falha o build se alguma for adicionada. Tudo o
 * que mora aqui e importavel pela camada domain de qualquer modulo, entao qualquer
 * acoplamento a framework introduzido aqui vazaria para todos os dominios de uma vez.
 */
package com.synapse.crm.sharedkernel;
