"use client";

import { createContext, useContext, type ReactNode } from "react";

import type { CartaoAtendimento } from "./types";

/**
 * E211 — o que a tela de Atendimentos oferece ao card de contato compartilhado.
 *
 * O card resolve o número pela busca autorizada do backend e só então chama uma destas ações;
 * nenhuma delas cria lead, abre atendimento ou envia mensagem por conta própria:
 * - `abrirCartao` usa a mesma seleção da lista (cartão já autorizado pelo servidor);
 * - `iniciarNovoContato` apenas abre o diálogo de novo contato preenchido — quem confirma é o usuário.
 */
export interface AberturaDeConversaDoContato {
  abrirCartao: (cartao: CartaoAtendimento) => void;
  iniciarNovoContato: (dados: { nome: string; telefone: string }) => void;
}

const Contexto = createContext<AberturaDeConversaDoContato | null>(null);

export function ProvedorDeAberturaDeConversa({
  valor,
  children,
}: {
  valor: AberturaDeConversaDoContato;
  children: ReactNode;
}) {
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

/** `null` fora da tela de Atendimentos: o card não mostra a ação (nada de botão sem efeito). */
export function useAberturaDeConversaDoContato(): AberturaDeConversaDoContato | null {
  return useContext(Contexto);
}

/**
 * Telefone para o formulário de novo contato, que usa a máscara nacional (DDD + número). Tira o
 * DDI 55 quando presente; número que não cabe no formato nacional volta vazio para o usuário digitar
 * — preencher errado seria pior que não preencher.
 */
export function telefoneParaNovoContato(numero: string): string {
  const texto = numero.trim();
  // DDI explícito e diferente do Brasil: a máscara nacional não o representa.
  if (texto.startsWith("+") && !texto.replace(/[^\d+]/g, "").startsWith("+55")) return "";
  const digitos = texto.replace(/\D/g, "");
  const nacional = digitos.length >= 12 && digitos.startsWith("55") ? digitos.slice(2) : digitos;
  return nacional.length === 10 || nacional.length === 11 ? nacional : "";
}
