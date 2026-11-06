import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DadosClienteLocais } from "./dados-cliente-locais";
import type { LeadFicha } from "@/lib/lead/types";

vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({ dadosCliente: {
  titulo: "Dados do cliente", cpf: "CPF", nascimento: "Data de nascimento", naoInformado: "Não informado",
} }) }));

const ficha = (cpf: string | null, data?: unknown) => ({ id: "sintetico", cpf, dadosCustomizados: data === undefined ? {} : { data_nascimento: data } }) as LeadFicha;
describe("dados locais do cliente", () => {
  it.each(["1990-05-21", "1990-05-21T03:00:00Z", "1990-05-21T00:00:00.123456789Z"])("formata CPF e data civil %s sem consultar Darwin", (data) => {
    render(<DadosClienteLocais lead={ficha("52998224725", data)} />);
    expect(screen.getByText("529.982.247-25")).toBeInTheDocument();
    expect(screen.getByText("21/05/1990")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
  it("dados parciais e data invalida nao inventam valores", () => {
    render(<DadosClienteLocais lead={ficha(null, "2025-02-31")} />);
    expect(screen.getAllByText("Não informado")).toHaveLength(2);
  });
  it("troca de lead remove imediatamente os dados do anterior", () => {
    const tela = render(<DadosClienteLocais lead={ficha("52998224725", "1990-05-21T00:00:00Z")} />);
    tela.rerender(<DadosClienteLocais lead={ficha(null)} />);
    expect(screen.queryByText("529.982.247-25")).not.toBeInTheDocument();
    expect(screen.queryByText("21/05/1990")).not.toBeInTheDocument();
    expect(screen.getAllByText("Não informado")).toHaveLength(2);
  });
});
