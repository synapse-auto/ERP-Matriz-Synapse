import { describe, expect, it } from "vitest";

import {
  aplicarResponsavelNaLista,
  ehMaisRecenteQue,
  mesclarCartaoComLista,
  mudancaDevolucaoParaIa,
  mudancaTransferencia,
  registrarMudanca,
  type RegistroDeMudancas,
} from "./sincronizar-responsavel";
import type { CartaoAtendimento } from "./types";

const cartao = (parcial: Partial<CartaoAtendimento> = {}): CartaoAtendimento => ({
  atendimentoId: "at-1",
  leadId: "lead-1",
  leadNome: "Lead",
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "ana",
  atendenteNome: "Ana",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
  ...parcial,
});

describe("sincronizar-responsavel", () => {
  it("ignora evento mais antigo que o já aplicado", () => {
    const registro: RegistroDeMudancas = new Map();
    const recente = mudancaDevolucaoParaIa({
      atendimentoId: "at-1",
      leadId: "lead-1",
      ocorridoEm: "2026-09-04T12:00:01Z",
    });
    expect(registrarMudanca(registro, recente.leadId, recente.mudanca)).toBe(true);
    const atrasado = mudancaDevolucaoParaIa({
      atendimentoId: "at-1",
      leadId: "lead-1",
      ocorridoEm: "2026-09-04T12:00:00Z",
    });
    expect(registrarMudanca(registro, atrasado.leadId, atrasado.mudanca)).toBe(false);
    expect(ehMaisRecenteQue(registro, "lead-1", "2026-09-04T12:00:02Z")).toBe(true);
  });

  it("remove o atendente da lista ao devolver para a IA", () => {
    const { mudanca } = mudancaDevolucaoParaIa({
      atendimentoId: "at-1",
      leadId: "lead-1",
      ocorridoEm: "2026-09-04T12:00:00Z",
    });
    const lista = aplicarResponsavelNaLista([cartao()], "lead-1", mudanca);
    expect(lista[0]).toMatchObject({
      atendenteId: null,
      atendenteNome: null,
      status: "EM_IA",
    });
  });

  it("mantém o snapshot sem atendente quando o cartão some da visão filtrada", () => {
    const registro: RegistroDeMudancas = new Map();
    const { leadId, mudanca } = mudancaDevolucaoParaIa({
      atendimentoId: "at-1",
      leadId: "lead-1",
      ocorridoEm: "2026-09-04T12:00:00Z",
    });
    registrarMudanca(registro, leadId, mudanca);
    const mesclado = mesclarCartaoComLista(cartao(), [], registro);
    expect(mesclado).toMatchObject({
      atendenteId: null,
      atendenteNome: null,
      status: "EM_IA",
    });
  });

  it("não deixa a lista atrasada ressuscitar o atendente antigo", () => {
    const registro: RegistroDeMudancas = new Map();
    const { leadId, mudanca } = mudancaDevolucaoParaIa({
      atendimentoId: "at-1",
      leadId: "lead-1",
      ocorridoEm: "2026-09-04T12:00:00Z",
    });
    registrarMudanca(registro, leadId, mudanca);
    const mesclado = mesclarCartaoComLista(cartao({ status: "EM_IA" }), [cartao()], registro);
    expect(mesclado).toMatchObject({
      atendenteId: null,
      atendenteNome: null,
      status: "EM_IA",
    });
  });

  it("aceita transferência humana no evento", () => {
    const { mudanca } = mudancaTransferencia({
      atendimentoId: "at-1",
      leadId: "lead-1",
      paraAtendenteId: "bruno",
      ocorridoEm: "2026-09-04T12:00:00Z",
      atendenteNome: "Bruno",
    });
    expect(mudanca).toMatchObject({
      atendenteId: "bruno",
      atendenteNome: "Bruno",
      status: "EM_ATENDIMENTO",
    });
  });
});
