import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import type { LeadFicha, TagDoLead } from "./types";

vi.mock("./api", () => ({
  atualizarLead: vi.fn(),
  desvincularTagDoLead: vi.fn(),
  listarCanais: vi.fn(),
  listarCamposCustomizados: vi.fn(),
  listarEtapas: vi.fn(),
  listarTagsDoLead: vi.fn(),
  listarTimeline: vi.fn(),
  listarTodasAsTags: vi.fn(),
  obterLead: vi.fn(),
  vincularTagAoLead: vi.fn(),
}));

import * as api from "./api";
import { useSalvarFicha, useVincularTag } from "./use-painel-lead";

function wrapper(cache: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={cache}>{children}</QueryClientProvider>;
  };
}

const ficha: LeadFicha = {
  id: "lead-1",
  nome: "Cliente",
  fotoUrl: null,
  telefone: null,
  email: null,
  cpf: null,
  empresa: null,
  codigo: null,
  localizacao: null,
  canalOrigemId: null,
  status: "EM_ATENDIMENTO",
  etapaAtendimentoId: null,
  atendenteResponsavelId: "usuario-1",
  notas: "anterior",
  resumoIa: "resumo",
  numAtendimentos: 2,
  numMensagens: 3,
  criadoEm: "2026-08-03T00:00:00Z",
  dadosCustomizados: { codigo: "A" },
};

describe("mutações otimistas da ficha", () => {
  it("restaura notas e campos anteriores quando o servidor recusa", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.atualizarLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ notas: "nova", dadosCustomizados: { codigo: "B" } });
    await waitFor(() => expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])?.notas).toBe("nova"));

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])).toEqual(ficha);
  });

  it("aplica codigo otimista e restaura quando o servidor recusa", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.atualizarLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    cache.setQueryData(["atendimentos"], [{ leadId: "lead-1", leadCodigo: null }]);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ codigo: "00421" });
    await waitFor(() =>
      expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])?.codigo).toBe("00421"),
    );

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])).toEqual(ficha);
    expect(cache.getQueryData(["atendimentos"])).toEqual([{ leadId: "lead-1", leadCodigo: null }]);
  });

  it("espelha o codigo salvo no card da inbox", async () => {
    vi.mocked(api.atualizarLead).mockResolvedValue({ ...ficha, codigo: "00421" });
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    cache.setQueryData(["atendimentos"], [{ leadId: "lead-1", leadCodigo: null }]);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ codigo: "00421" });
    await waitFor(() =>
      expect(cache.getQueryData(["atendimentos"])).toEqual([{ leadId: "lead-1", leadCodigo: "00421" }]),
    );
  });

  it("adiciona a tag otimista e restaura a lista quando o vínculo falha", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.vincularTagAoLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const anterior: TagDoLead[] = [{ id: "tag-1", nome: "Atual", cor: "var(--primary)", icone: null }];
    const nova: TagDoLead = { id: "tag-2", nome: "Nova", cor: "var(--primary)", icone: null };
    cache.setQueryData(["lead", "lead-1", "tags"], anterior);
    const { result } = renderHook(() => useVincularTag("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ tag: nova });
    await waitFor(() =>
      expect(cache.getQueryData<TagDoLead[]>(["lead", "lead-1", "tags"])).toHaveLength(2),
    );

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<TagDoLead[]>(["lead", "lead-1", "tags"])).toEqual(anterior);
  });
});
