import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import type { Textos } from "@/lib/config/schema";

import { ListaTemplatesWhatsApp } from "./lista-templates-whatsapp";

const textos = {
  semTemplates: "Nenhum template aprovado ainda.",
  templatesErro: "Erro templates",
  parametroTemplate: "Parâmetro {indice}",
  enviarTemplate: "Enviar este template",
  criarTemplate: "Criar template",
} as Textos["atendimentos"]["composer"];

const aprovado: TemplateWhatsApp = {
  nome: "boas_vindas",
  idioma: "pt_BR",
  categoria: "UTILIDADE",
  status: "APROVADO",
  corpo: "Olá",
  quantidadeDeParametros: 0,
};

const pendente: TemplateWhatsApp = {
  ...aprovado,
  nome: "ainda_nao",
  status: "PENDENTE",
};

describe("ListaTemplatesWhatsApp", () => {
  it("só oferece templates aprovados e envia o escolhido", () => {
    const onEnviar = vi.fn();
    render(
      <ListaTemplatesWhatsApp
        textos={textos}
        templates={{ data: [aprovado, pendente], isError: false, isLoading: false }}
        parametros={{}}
        onParametros={vi.fn()}
        enviando={false}
        onEnviar={onEnviar}
      />,
    );

    expect(screen.getByText("boas_vindas")).toBeInTheDocument();
    expect(screen.queryByText("ainda_nao")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).toHaveBeenCalledWith(aprovado, []);
  });
});
