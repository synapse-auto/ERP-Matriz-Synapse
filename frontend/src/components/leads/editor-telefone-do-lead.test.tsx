import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TextosSchema } from "@/lib/config/schema";
import { TextosProvider } from "@/lib/config/textos-provider";
import { ErroDeApi } from "@/lib/api/errors";
import { EditorTelefoneDoLead } from "./editor-telefone-do-lead";

const textos = TextosSchema.parse(JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8")));
const dados = textos.painelLead.dados;
const salvar = { mutate: vi.fn(), isPending: false };
vi.mock("@/lib/lead/use-painel-lead", () => ({ useSalvarTelefoneLead: () => salvar }));

function abrir() {
  render(<TextosProvider textos={textos}><EditorTelefoneDoLead leadId="lead-sintetico" telefone="5561987651234" permitido /></TextosProvider>);
  fireEvent.click(screen.getByRole("button", { name: dados.editarTelefone }));
  return screen.getByRole("textbox", { name: dados.novoTelefone });
}

beforeEach(() => { vi.clearAllMocks(); salvar.isPending = false; });

describe("edicao segura do telefone", () => {
  it("envia o numero digitado somente ao confirmar e fecha apos sucesso", () => {
    const campo = abrir();
    fireEvent.change(campo, { target: { value: "+55 (61) 98765-4321" } });
    expect(salvar.mutate).not.toHaveBeenCalled();
    salvar.mutate.mockImplementation((_valor, callbacks) => callbacks.onSuccess());
    fireEvent.click(screen.getByRole("button", { name: dados.confirmarAlteracaoTelefone }));
    expect(salvar.mutate).toHaveBeenCalledWith("+55 (61) 98765-4321", expect.any(Object));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it.each([
    [409, "TELEFONE_INDISPONIVEL", "telefoneIndisponivel"],
    [403, undefined, "telefoneSemPermissao"],
    [404, undefined, "telefoneNaoEncontrado"],
    [400, "TELEFONE_INVALIDO", "telefoneInvalido"],
    [500, undefined, "erroAlteracaoTelefone"],
    [409, "OUTRO_CONFLITO", "erroAlteracaoTelefone"],
  ] as const)("traduz %s/%s sem expor erro tecnico", (status, codigo, chave) => {
    const campo = abrir();
    fireEvent.change(campo, { target: { value: "5561987654321" } });
    salvar.mutate.mockImplementation((_valor, callbacks) => callbacks.onError(new ErroDeApi(status, { codigo, detail: "SQL segredo interno" }, "erro")));
    fireEvent.click(screen.getByRole("button", { name: dados.confirmarAlteracaoTelefone }));
    expect(screen.getByRole("alert")).toHaveTextContent(dados[chave]);
    expect(screen.getByRole("dialog")).not.toHaveTextContent("SQL segredo interno");
    expect(campo).toHaveValue("5561987654321");
  });

  it("Enter seguido de clique nao envia duas alteracoes; falha permite tentar novamente", () => {
    const campo = abrir();
    salvar.mutate.mockImplementation(() => undefined);
    fireEvent.keyDown(campo, { key: "Enter" });
    fireEvent.click(screen.getByRole("button", { name: dados.confirmarAlteracaoTelefone }));
    expect(salvar.mutate).toHaveBeenCalledTimes(1);
    const callbacks = salvar.mutate.mock.calls[0][1];
    fireEvent.change(campo, { target: { value: "5561987654321" } });
    act(() => callbacks.onError(new ErroDeApi(503, null, "erro")));
    fireEvent.click(screen.getByRole("button", { name: dados.confirmarAlteracaoTelefone }));
    expect(salvar.mutate).toHaveBeenCalledTimes(2);
  });

  it("numero curto e cancelamento nao fazem requisicao", () => {
    const campo = abrir();
    fireEvent.change(campo, { target: { value: "123" } });
    fireEvent.click(screen.getByRole("button", { name: dados.confirmarAlteracaoTelefone }));
    expect(screen.getByRole("alert")).toHaveTextContent(dados.telefoneInvalido);
    fireEvent.click(screen.getByRole("button", { name: dados.cancelarAlteracaoTelefone }));
    expect(salvar.mutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
