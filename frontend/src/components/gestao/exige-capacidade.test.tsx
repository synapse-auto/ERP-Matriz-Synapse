import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { erroGenerico: "Erro.", tentarNovamente: "Tentar novamente" },
    gestao: {
      acesso: {
        verificando: "Verificando suas permissões…",
        erro: "Não foi possível confirmar suas permissões.",
        semAcessoTitulo: "Sem acesso a esta área",
        semAcessoDescricao: "Seu perfil não inclui esta área.",
        voltar: "Voltar para Atendimentos",
      },
    },
  }),
}));

// Cada página marca que foi montada: com a capacidade negada, nada dela pode renderizar.
vi.mock("@/components/templates-whatsapp/pagina-templates-whatsapp", () => ({ PaginaTemplatesWhatsApp: () => <p>pagina montada</p> }));
vi.mock("@/components/mensagens-rapidas/pagina-mensagens-rapidas", () => ({ PaginaMensagensRapidas: () => <p>pagina montada</p> }));
vi.mock("@/components/mensagens-programadas/pagina-mensagens-programadas", () => ({ PaginaMensagensProgramadas: () => <p>pagina montada</p> }));
vi.mock("@/components/lembretes/pagina-lembretes", () => ({ PaginaLembretes: () => <p>pagina montada</p> }));
vi.mock("@/components/dashboard/pagina-dashboard", () => ({ PaginaDashboard: () => <p>pagina montada</p> }));
vi.mock("@/components/automacao/pagina-automacao", () => ({ PaginaAutomacao: () => <p>pagina montada</p> }));

import Automacao from "@/app/(shell)/automacao/page";
import Dashboard from "@/app/(shell)/dashboard/page";
import Lembretes from "@/app/(shell)/lembretes/page";
import MensagensProgramadas from "@/app/(shell)/mensagens-programadas/page";
import MensagensRapidas from "@/app/(shell)/mensagens-rapidas/page";
import TemplatesWhatsApp from "@/app/(shell)/templates-whatsapp/page";

const ROTAS = [
  { nome: "templates", Pagina: TemplatesWhatsApp, capacidade: "templates.ver" },
  { nome: "mensagens rápidas", Pagina: MensagensRapidas, capacidade: "mensagens_rapidas.usar" },
  { nome: "mensagens programadas", Pagina: MensagensProgramadas, capacidade: "mensagens_programadas.ver" },
  { nome: "lembretes", Pagina: Lembretes, capacidade: "lembretes.ver" },
  { nome: "dashboard", Pagina: Dashboard, capacidade: "dashboard.ver" },
  { nome: "automação", Pagina: Automacao, capacidade: "automacao.ver" },
];

describe("ExigeCapacidade nas rotas (URL direta)", () => {
  it.each(ROTAS)("$nome: sem $capacidade, mostra Sem acesso e não monta a página", ({ Pagina, capacidade }) => {
    definirCapacidadesDeTeste({ negadas: [capacidade] });
    render(<Pagina />);

    expect(screen.getByRole("heading", { name: "Sem acesso a esta área" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar para Atendimentos" })).toHaveAttribute("href", "/atendimentos");
    expect(screen.queryByText("pagina montada")).not.toBeInTheDocument();
  });

  it.each(ROTAS)("$nome: com a capacidade, monta a página", ({ Pagina }) => {
    render(<Pagina />);
    expect(screen.getByText("pagina montada")).toBeInTheDocument();
  });

  it("enquanto as permissões carregam, mostra só o aviso de verificação", () => {
    definirCapacidadesDeTeste({ estado: "carregando" });
    render(<TemplatesWhatsApp />);

    expect(screen.getByRole("status")).toHaveTextContent("Verificando suas permissões…");
    expect(screen.queryByText("pagina montada")).not.toBeInTheDocument();
  });

  it("falha na consulta mostra erro recuperável e tenta de novo", () => {
    const recarregar = vi.fn();
    definirCapacidadesDeTeste({ estado: "erro", onRecarregar: recarregar });
    render(<TemplatesWhatsApp />);

    expect(screen.getByRole("alert")).toHaveTextContent("Não foi possível confirmar suas permissões.");
    expect(screen.queryByText("pagina montada")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(recarregar).toHaveBeenCalled();
  });
});
