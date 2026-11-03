import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TextosSchema } from "@/lib/config/schema";
import { ErroDeApi } from "@/lib/api/errors";
import { ConsultaDarwin } from "./consulta-darwin";

const api = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/http-client", () => ({ apiFetch: api }));
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (s: (v: unknown) => unknown) => s({ usuarioId: "usuario-sintetico" }) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => TextosSchema.parse(JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8"))) }));

const previa = { estado: "ENCONTRADO", nomeAtual: "Telefone cadastrado", cpfAtual: null, limiteAtingido: false,
  candidatos: [{ sessaoId: null, nome: "Pessoa Sintética", cpf: "52998224725", cpfInvalido: false }], confirmacao: "comprovante-sintetico" };
function montar(permitido = true, cpfAtual?: string) {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return { cache, ...render(<QueryClientProvider client={cache}><ConsultaDarwin leadId="lead-sintetico" permitido={permitido} cpfAtual={cpfAtual} /></QueryClientProvider>) };
}
async function abrirEConsultar() {
  fireEvent.click(await screen.findByRole("button", { name: "Consultar na Darwin" }));
  fireEvent.change(screen.getByRole("textbox", { name: "CPF para consulta" }), { target: { value: "529.982.247-25" } });
  fireEvent.click(screen.getByRole("button", { name: "Buscar por CPF" }));
}
beforeEach(() => {
  api.mockReset();
  api.mockImplementation((url: string) => Promise.resolve(url.endsWith("/darwin") ? { habilitada: true } : url.endsWith("/consultas") ? previa : { leadId: "lead-sintetico", camposAlterados: ["nome", "cpf"] }));
});
describe("consulta Darwin pela ficha", () => {
  it("não oferece controle nem consulta sem permissão; desligada não chama a Darwin", async () => {
    const tela = montar(false);
    expect(api).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Consultar na Darwin" })).not.toBeInTheDocument();
    tela.unmount();
    api.mockResolvedValue({ habilitada: false });
    montar();
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    expect(screen.queryByRole("button", { name: "Consultar na Darwin" })).not.toBeInTheDocument();
    expect(api.mock.calls[0][0]).toBe("/api/v1/leads/integracoes/darwin");
  });
  it("seleciona CPF vazio, exige confirmação explícita do nome e envia só o comprovante e escolhas", async () => {
    montar();
    await abrirEConsultar();
    expect(await screen.findByText("Pessoa Sintética")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Aplicar nome" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Aplicar CPF" })).toBeChecked();
    expect(screen.getByText("Telefone cadastrado")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "Aplicar nome" }));
    fireEvent.click(screen.getByRole("button", { name: "Aplicar seleção" }));
    await screen.findByText("Dados aplicados à ficha.");
    const pedido = api.mock.calls.find(([url]) => url.endsWith("/aplicacoes"));
    expect(JSON.parse(pedido?.[1].body)).toEqual({ confirmacao: "comprovante-sintetico", nome: true, cpf: true });
    expect(pedido?.[1].body).not.toContain("52998224725");
    expect(JSON.parse(api.mock.calls.find(([url]) => url.endsWith("/consultas"))?.[1].body)).toEqual({ cpf: "529.982.247-25" });
  });
  it("CPF inválido não pode ser selecionado", async () => {
    api.mockImplementation((url: string) => Promise.resolve(url.endsWith("/darwin") ? { habilitada: true }
      : { ...previa, candidatos: [{ ...previa.candidatos[0], cpf: null, cpfInvalido: true }] }));
    montar(); await abrirEConsultar();
    expect(await screen.findByRole("checkbox", { name: "Aplicar CPF" })).toBeDisabled();
    expect(screen.getByText("CPF inválido na origem; não será aplicado.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
  });
  it("edição concorrente mantém a prévia e bloqueia novo envio até consultar novamente", async () => {
    api.mockImplementation((url: string) => url.endsWith("/aplicacoes")
      ? Promise.reject(new ErroDeApi(409, { codigo: "DARWIN_CONCORRENCIA" }, ""))
      : Promise.resolve(url.endsWith("/darwin") ? { habilitada: true } : previa));
    montar(); await abrirEConsultar();
    await screen.findByText("Pessoa Sintética");
    fireEvent.click(screen.getByRole("button", { name: "Aplicar seleção" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("A ficha mudou. Consulte novamente antes de aplicar.");
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
  });
  it.each(["SEM_RESULTADO", "SEM_IDENTIDADE", "AMBIGUO"])("%s não libera aplicação", async estado => {
    api.mockImplementation((url: string) => Promise.resolve(url.endsWith("/darwin") ? { habilitada: true }
      : { ...previa, estado, candidatos: estado === "AMBIGUO" ? [...previa.candidatos, { ...previa.candidatos[0], sessaoId: "122", nome: "Outra pessoa" }] : [], confirmacao: null }));
    montar(); await abrirEConsultar();
    await screen.findByRole("dialog");
    await waitFor(() => expect(screen.queryByText("Consultando dados…")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.queryByText("Outra pessoa")).not.toBeInTheDocument();
    expect(api.mock.calls.some(([url]) => url.endsWith("/aplicacoes"))).toBe(false);
  });
  it("loading impede consulta duplicada e cancelar aborta sem aplicar", async () => {
    let sinal: AbortSignal | undefined;
    api.mockImplementation((url: string, opcoes?: { signal?: AbortSignal }) => {
      if (url.endsWith("/darwin")) return Promise.resolve({ habilitada: true });
      sinal = opcoes?.signal;
      return new Promise((_resolve, rejeitar) => sinal?.addEventListener("abort", () => rejeitar(new DOMException("Consulta cancelada", "AbortError")), { once: true }));
    });
    montar(); const botao = await screen.findByRole("button", { name: "Consultar na Darwin" });
    fireEvent.click(botao);
    fireEvent.change(screen.getByRole("textbox", { name: "CPF para consulta" }), { target: { value: "52998224725" } });
    const buscar = screen.getByRole("button", { name: "Buscar por CPF" });
    fireEvent.click(buscar); fireEvent.click(buscar);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(botao).toBeDisabled();
    expect(api.mock.calls.filter(([url]) => url.endsWith("/consultas"))).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(sinal?.aborted).toBe(true);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("falha do provedor é legível, não aplica e permite consulta manual posterior", async () => {
    api.mockImplementation((url: string) => url.endsWith("/consultas") ? Promise.reject(new ErroDeApi(502, { codigo: "DARWIN_AUTENTICACAO" }, "")) : Promise.resolve({ habilitada: true }));
    montar(); await abrirEConsultar();
    expect(await screen.findByRole("alert")).toHaveTextContent("A Darwin recusou a credencial configurada.");
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
    api.mockImplementation((url: string) => Promise.resolve(url.endsWith("/darwin") ? { habilitada: true } : previa));
    fireEvent.click(screen.getByRole("button", { name: "Buscar por CPF" }));
    expect(await screen.findByText("Pessoa Sintética")).toBeInTheDocument();
  });
  it("aplicação não duplica clique e invalida apenas ficha e listas após sucesso", async () => {
    let concluir!: (valor: unknown) => void;
    api.mockImplementation((url: string) => url.endsWith("/aplicacoes") ? new Promise(resolve => { concluir = resolve; }) : Promise.resolve(url.endsWith("/darwin") ? { habilitada: true } : previa));
    const { cache } = montar(); const invalidar = vi.spyOn(cache, "invalidateQueries");
    await abrirEConsultar();
    await screen.findByText("Pessoa Sintética");
    const botao = screen.getByRole("button", { name: "Aplicar seleção" });
    fireEvent.click(botao); fireEvent.click(botao);
    await waitFor(() => expect(api.mock.calls.filter(([url]) => url.endsWith("/aplicacoes"))).toHaveLength(1));
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    concluir({ leadId: "lead-sintetico", origem: "DARWIN_MEDICO", camposAlterados: ["cpf"] });
    await screen.findByText("Dados aplicados à ficha.");
    expect(invalidar.mock.calls.map(([opcoes]) => opcoes?.queryKey)).toEqual([["lead", "lead-sintetico"], ["atendimentos"], ["agenda"]]);
    expect(botao).toBeDisabled();
  });
  it("catálogos antigos recebem textos padrão sem alterar outras chaves", () => {
    const antigos = JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8"));
    delete antigos.darwin;
    expect(TextosSchema.parse(antigos).darwin.consultar).toBe("Consultar na Darwin");
  });
  it("abrir sem CPF não consulta e informa que não existe descoberta por telefone", async () => {
    montar();
    fireEvent.click(await screen.findByRole("button", { name: "Consultar na Darwin" }));
    expect(screen.getByRole("button", { name: "Buscar por CPF" })).toBeDisabled();
    expect(screen.getByText(/Esta consulta não descobre CPF pelo telefone/)).toBeInTheDocument();
    expect(api.mock.calls.filter(([url]) => url.endsWith("/consultas"))).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
  });
  it("CPF atual pré-preenche o formulário e submit envia somente o documento conhecido", async () => {
    montar(true, "52998224725");
    fireEvent.click(await screen.findByRole("button", { name: "Consultar na Darwin" }));
    expect(screen.getByRole("textbox", { name: "CPF para consulta" })).toHaveValue("52998224725");
    fireEvent.submit(screen.getByRole("form", { name: "Buscar por CPF" }));
    await screen.findByText("Pessoa Sintética");
    expect(JSON.parse(api.mock.calls.find(([url]) => url.endsWith("/consultas"))?.[1].body)).toEqual({ cpf: "52998224725" });
  });
  it("alterar documento remove prévia e comprovante antes de qualquer nova consulta", async () => {
    montar(); await abrirEConsultar(); await screen.findByText("Pessoa Sintética");
    fireEvent.change(screen.getByRole("textbox", { name: "CPF para consulta" }), { target: { value: "39053344705" } });
    expect(screen.queryByText("Pessoa Sintética")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Aplicar seleção" })).toBeDisabled();
    expect(api.mock.calls.filter(([url]) => url.endsWith("/consultas"))).toHaveLength(1);
  });
  it("catálogo anterior não mantém instruções de telefone e sessão do produto incorreto", () => {
    const antigos = JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8"));
    delete antigos.darwin.versaoContrato;
    antigos.darwin.descricao = "Confira os valores da mesma sessão";
    antigos.darwin.SEM_IDENTIDADE = "Telefone compatível necessário";
    antigos.darwin.consultar = "Consultar cadastro";
    const t = TextosSchema.parse(antigos).darwin;
    expect(t.descricao).toContain("CPF conhecido");
    expect(t.SEM_IDENTIDADE).toContain("CPF conhecido");
    expect(t.consultar).toBe("Consultar cadastro");
  });
});
