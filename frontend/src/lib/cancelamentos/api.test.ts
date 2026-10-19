import { beforeEach, expect, it, vi } from "vitest";
import { listarCancelamentos, obterCancelamento } from "./api";
import { apiFetch } from "@/lib/api/http-client";
vi.mock("@/lib/api/http-client",()=>({apiFetch:vi.fn()}));
beforeEach(()=>vi.clearAllMocks());
it("codifica filtros combinados e propagacao de cancelamento; sem buscar todos os registros",()=>{
  const signal=new AbortController().signal;
  listarCancelamentos({inicio:"2026-10-01",fim:"2026-10-31",base:"REGISTRO",descricao:"TODAS",nome:"A&B",tipo:"Retorno",profissional:"P"},2,25,signal);
  const url=vi.mocked(apiFetch).mock.calls[0][0];
  const filtros=new URLSearchParams(url.split("?")[1]);
  expect(filtros.get("nome")).toBe("A&B"); expect(filtros.get("pagina")).toBe("2"); expect(filtros.get("tamanho")).toBe("25");
  expect(vi.mocked(apiFetch).mock.calls[0][1]).toEqual({signal});
  obterCancelamento("id/seguro",signal); expect(apiFetch).toHaveBeenLastCalledWith("/api/v1/cancelamentos/id%2Fseguro",{signal});
});
