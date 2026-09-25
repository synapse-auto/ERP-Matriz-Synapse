import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { partesDeTextoComLinks, TextoComLinks } from "./texto-com-links";

describe("TextoComLinks", () => {
  it("torna URLs http(s) explícitas clicáveis e preserva texto, pontuação e quebras de linha", () => {
    render(
      <p className="whitespace-pre-wrap">
        <TextoComLinks
          texto={"Veja https://example.test/a_(b),\nou https://example.org/ajuda."}
          rotuloAbrir="Abrir {nome}"
        />
      </p>,
    );

    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", "https://example.test/a_(b)");
    expect(links[1]).toHaveAttribute("href", "https://example.org/ajuda");
    expect(links[0]).toHaveAttribute("target", "_blank");
    expect(links[0]).toHaveAttribute("rel", "noopener noreferrer");
    expect(links[0]).toHaveAccessibleName("Abrir https://example.test/a_(b)");
    expect(screen.getByRole("paragraph").textContent).toBe(
      "Veja https://example.test/a_(b),\nou https://example.org/ajuda.",
    );
  });

  it("permite foco de teclado nativo no link", () => {
    render(
      <TextoComLinks texto="https://example.test/ajuda" rotuloAbrir="Abrir {nome}" />,
    );

    const link = screen.getByRole("link");
    link.focus();
    expect(document.activeElement).toBe(link);
  });

  it("não cria links para esquemas perigosos, texto parecido ou URLs inválidas", () => {
    const partes = partesDeTextoComLinks(
      "javascript:alert(1) data:text/plain,x www.example.test https://[host inválido] ftp://example.test https://",
    );

    expect(partes.every((parte) => parte.tipo === "texto")).toBe(true);
    expect(partes.map((parte) => parte.texto).join("")).toContain("javascript:alert(1)");
  });

  it("não reconhece um https embutido em outro identificador ou esquema", () => {
    const texto = "prefixohttps://example.test javascript:https://example.test";
    expect(partesDeTextoComLinks(texto)).toEqual([{ tipo: "texto", texto }]);
  });

  it("renderiza HTML recebido como texto e não cria elementos executáveis", () => {
    const texto = '<img src=x onerror="alert(1)"> https://example.test/seguro';
    const { container } = render(
      <TextoComLinks texto={texto} rotuloAbrir="Abrir {nome}" />,
    );

    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByRole("link")).toHaveAttribute("href", "https://example.test/seguro");
    expect(container.textContent).toContain('<img src=x onerror="alert(1)">');
  });
});
