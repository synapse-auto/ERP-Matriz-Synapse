import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const copiarTexto = vi.fn<(texto: string) => Promise<boolean>>();
vi.mock("@/lib/mensagens/copiar-texto", () => ({
  copiarTexto: (texto: string) => copiarTexto(texto),
}));

import { BolhaContato, type TextosDaBolhaContato } from "./bolha-contato";

const TEXTOS: TextosDaBolhaContato = {
  contato: "Contato compartilhado",
  contatoSemNome: "Contato sem nome",
  contatoSemTelefone: "Sem telefone no cartão",
  copiarTelefone: "Copiar número",
  ligarPara: "Ligar para {numero}",
  copiar: "Copiar",
  copiada: "Copiado",
  copiarErro: "Falhou",
};

function metadados(contatos: unknown[]): string {
  return JSON.stringify({ contatos });
}

describe("BolhaContato", () => {
  it("preserva vários contatos e todos os números, com ligar e copiar em cada número válido", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([
          {
            nome: "Arquiteta Exemplo",
            telefones: [
              { numero: "+55 61 3333-0000", tipo: "WORK" },
              { numero: "+55 61 98888-0000", waId: "5561988880000", tipo: "CELL" },
            ],
          },
          { nome: "Bruno Exemplo", telefones: [{ numero: "(61) 3000-0000" }] },
        ])}
        textos={TEXTOS}
      />,
    );

    expect(screen.getByText("Arquiteta Exemplo")).toBeInTheDocument();
    expect(screen.getByText("Bruno Exemplo")).toBeInTheDocument();
    expect(screen.getByText("+55 61 3333-0000")).toBeInTheDocument();
    expect(screen.getByText("+55 61 98888-0000")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ligar para +55 61 98888-0000" }))
      .toHaveAttribute("href", "tel:+5561988880000");
    expect(screen.getByRole("link", { name: "Ligar para (61) 3000-0000" }))
      .toHaveAttribute("href", "tel:6130000000");
    expect(screen.getAllByRole("link")).toHaveLength(3);
    expect(screen.getAllByRole("button", { name: "Copiar número" })).toHaveLength(3);
  });

  it("mostra contato sem telefone sem nenhuma ação de ligar ou copiar", () => {
    render(
      <BolhaContato midiaMetadados={metadados([{ nome: "Sem Telefone", telefones: [] }])} textos={TEXTOS} />,
    );

    expect(screen.getByText("Sem Telefone")).toBeInTheDocument();
    expect(screen.getByText("Sem telefone no cartão")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("não oferece ação para número que não é discável", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "Ramal", telefones: [{ numero: "ramal 12" }, { numero: "123" }] }])}
        textos={TEXTOS}
      />,
    );

    expect(screen.getByText("ramal 12")).toBeInTheDocument();
    expect(screen.getByText("123")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("copia o número exibido e anuncia o resultado", async () => {
    copiarTexto.mockResolvedValueOnce(true);
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={TEXTOS}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Copiar número" }));

    await waitFor(() => expect(screen.getByText("Copiado")).toBeInTheDocument());
    expect(copiarTexto).toHaveBeenCalledWith("+55 61 98888-0000");
  });

  it("anuncia falha de cópia em vez de silenciar", async () => {
    copiarTexto.mockResolvedValueOnce(false);
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={TEXTOS}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Copiar número" }));

    await waitFor(() => expect(screen.getByText("Falhou")).toBeInTheDocument());
  });

  it("com catálogo antigo, sem as chaves de contato, mostra o dado e usa a ação genérica", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([{ telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={{ copiar: "Copiar", copiada: "Copiado", copiarErro: "Falhou" }}
      />,
    );

    // Sem nome e sem "contatoSemNome": o próprio número identifica o cartão.
    expect(screen.getAllByText("+55 61 98888-0000")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "Copiar" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+55 61 98888-0000" })).toBeInTheDocument();
  });

  it("não inventa contato quando os metadados são ilegíveis", () => {
    render(<BolhaContato midiaMetadados="{nao e json" textos={TEXTOS} />);

    expect(screen.getByText("Contato compartilhado")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
