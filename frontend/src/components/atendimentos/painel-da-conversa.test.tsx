import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import type { EtapaAtendimento } from "@/lib/lead/types";
import type { Lembrete, MensagemProgramada } from "@/lib/suporte/types";

type LeadTeste = {
  id: string;
  nome: string;
  fotoUrl: string | null;
  empresa: string | null;
  telefone: string | null;
  email: string | null;
  localizacao: string | null;
  etapaAtendimentoId: string | null;
  numAtendimentos: number;
  numMensagens: number;
  resumoIa: string | null;
  notas: string | null;
};

const leadState = vi.hoisted(() => ({
  data: {
    id: "lead-1",
    nome: "Marcos Vinícius",
    fotoUrl: null,
    empresa: "Vidraçaria Cristal",
    telefone: "(61) 99999-0000",
    email: "marcos@cliente.com",
    localizacao: "Taguatinga · DF",
    etapaAtendimentoId: "etapa-1",
    numAtendimentos: 3,
    numMensagens: 20,
    resumoIa: "Cliente pediu orçamento de box.",
    notas: "",
  } as LeadTeste,
}));
const etapasState = vi.hoisted(() => ({
  data: [
    { id: "etapa-0", nome: "Novo contato", ordem: 1, corVisual: "var(--muted)" },
    { id: "etapa-1", nome: "Orçamento", ordem: 2, corVisual: "var(--primary)" },
    { id: "etapa-2", nome: "Negociação", ordem: 3, corVisual: "var(--accent)" },
  ] as EtapaAtendimento[],
}));
const suporteState = vi.hoisted(() => ({
  mensagens: [] as MensagemProgramada[],
  lembretes: [] as Lembrete[],
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      painel: {
        titulo: "Detalhes do lead",
        retrair: "Retrair detalhes do lead",
        reabrir: "Reabrir detalhes do lead",
        informacoesGerais: "Informações gerais",
        notasInternas: "Notas internas",
        adicionar: "Adicionar",
        editar: "Editar",
        remover: "Remover",
        confirmarRemocao: "Remover {item}?",
        cancelarRemocao: "Cancelar",
        erroOperacao: "Erro",
        secoes: {
          resumo: "Resumo por IA e notas",
          programadas: "Mensagens programadas",
          lembretes: "Lembretes",
          midias: "Mídias e documentos",
        },
        vazioProgramadas: "Nenhuma mensagem programada",
        vazioLembretes: "Nenhum lembrete",
        vazioMidias: "Nenhuma mídia ou documento",
        erroMidias: "Não foi possível carregar as mídias.",
        carregandoMidias: "Carregando mídias...",
        carregarMaisMidias: "Carregar mais",
        salvarImagem: "Salvar imagem",
        origemMidia: "Origem",
      },
    },
    painelLead: {
      dados: {
        telefone: "Telefone",
        email: "E-mail",
        localizacao: "Localização",
        responsavel: "Responsável",
      },
      etapa: { titulo: "Etapa", posicao: "{atual} de {total}" },
      contadores: { atendimentos: "Atendimentos", mensagens: "Mensagens" },
      tags: {
        titulo: "Etiquetas",
        botao: "Tag",
        adicionar: "Adicionar tag",
        remover: "Remover tag {nome}",
        erroReversao: "Estado anterior restaurado",
      },
      resumoIa: { vazio: "Sem notas." },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => leadState,
  useEtapas: () => etapasState,
  useMidiasDoLead: () => ({ data: { pages: [[]] }, isLoading: false, isError: false, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() }),
  useTagsDoLead: () => ({
    data: [{ id: "tag-1", nome: "Prioridade", cor: "#dc2626", icone: null }],
  }),
  useTodasAsTags: () => ({
    data: [{ id: "tag-1", nome: "Prioridade", cor: "#dc2626", icone: null }],
  }),
  useVincularTag: () => ({ mutate: vi.fn() }),
  useDesvincularTag: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/lib/suporte/use-suporte", () => ({
  useMensagensProgramadasDoLead: () => ({
    data: { mensagens: suporteState.mensagens, pagina: 0, temMais: false },
  }),
  useLembretesDoLead: () => ({
    data: { lembretes: suporteState.lembretes, pagina: 0, temMais: false },
  }),
}));

vi.mock("../lembretes/formulario-lembrete", () => ({
  FormularioLembrete: ({ aberto }: { aberto: boolean }) =>
    aberto ? <div data-testid="formulario-lembrete" /> : null,
}));

vi.mock("../mensagens-programadas/formulario-mensagem-programada", () => ({
  FormularioMensagemProgramada: ({ aberto }: { aberto: boolean }) =>
    aberto ? <div data-testid="formulario-mensagem-programada" /> : null,
}));

import { PainelDaConversa } from "./painel-da-conversa";

describe("painel da conversa", () => {
  it("mostra contadores, etapa e resumo por IA aberto por padrão — sem seção de arquivos", () => {
    const onRetrair = vi.fn();
    renderizarPainel("lead-1", "Jardel Lima", onRetrair);

    expect(screen.getByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Informações gerais")).toBeInTheDocument();
    expect(screen.getByText("Jardel Lima")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("2 de 3")).toBeInTheDocument();
    expect(screen.getByText("Prioridade")).toBeInTheDocument();
    expect(screen.getByText("Tag")).toBeInTheDocument();
    expect(
      screen.getByText("Cliente pediu orçamento de box."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/arquivos/i)).not.toBeInTheDocument();
    const controle = screen.getByRole("button", { name: "Retrair detalhes do lead" });
    expect(controle).toHaveAttribute("aria-expanded", "true");
    expect(controle).toHaveAttribute("aria-controls", "painel-detalhes-lead");
    expect(screen.getByRole("complementary")).toHaveClass(
      "min-h-0",
      "overflow-hidden",
    );
    fireEvent.click(controle);
    expect(onRetrair).toHaveBeenCalledOnce();
  });

  it("mensagens programadas e lembretes começam fechados e abrem com o estado vazio real", () => {
    renderizarPainel("lead-1", "Jardel Lima");

    expect(
      screen.queryByText("Nenhuma mensagem programada"),
    ).not.toBeInTheDocument();
    const programadas = screen.getByRole("button", { name: /Mensagens programadas/ });
    const lembretes = screen.getByRole("button", { name: /Lembretes/ });
    expect(programadas).toHaveAttribute("aria-expanded", "false");
    expect(lembretes).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(programadas);
    expect(programadas).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Nenhuma mensagem programada")).toBeInTheDocument();

    fireEvent.click(lembretes);
    expect(lembretes).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Nenhum lembrete")).toBeInTheDocument();
    expect(screen.getByRole("complementary")).toHaveClass("min-h-0", "overflow-hidden");
  });

  it("mantém o painel estável quando a etapa, o e-mail e a localidade estão ausentes", () => {
    leadState.data = {
      ...leadState.data,
      id: "lead-2",
      nome: "Lead sem dados opcionais",
      empresa: null,
      email: null,
      localizacao: null,
      etapaAtendimentoId: null,
      resumoIa: null,
      notas: null,
    };
    etapasState.data = [];

    renderizarPainel("lead-2", null);

    expect(screen.getByText("Lead sem dados opcionais")).toBeInTheDocument();
    expect(screen.queryByText("E-mail")).not.toBeInTheDocument();
    expect(screen.queryByText("Localização")).not.toBeInTheDocument();
    expect(screen.queryByText("Etapa")).not.toBeInTheDocument();
  });

  it("oferece criar, editar e remover itens nas duas seções", () => {
    suporteState.mensagens = [{
      id: "mensagem-1",
      leadId: "lead-1",
      leadNome: "Marcos Vinícius",
      atendenteId: "atendente-1",
      atendenteNome: "Jardel Lima",
      conteudo: "Follow-up",
      dataEnvio: "2030-01-01T12:00:00Z",
      status: "AGENDADA",
    }];
    suporteState.lembretes = [{
      id: "lembrete-1",
      leadId: "lead-1",
      leadNome: "Marcos Vinícius",
      atendenteId: "atendente-1",
      atendenteNome: "Jardel Lima",
      texto: "Ligar",
      dataHora: "2030-01-01T12:00:00Z",
      origemAutomatica: false,
      status: "PENDENTE",
    }];

    renderizarPainel("lead-1", "Jardel Lima");
    fireEvent.click(screen.getByText("Mensagens programadas"));
    const cartaoProgramada = screen.getByText("Follow-up").closest('[data-slot="mensagem-programada"]');
    expect(cartaoProgramada).toHaveClass(
      "border-2",
      "border-primary",
      "bg-primary/10",
      "shadow-sm",
    );
    expect(screen.getByRole("button", { name: "Adicionar" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Adicionar" }));
    expect(screen.getByTestId("formulario-mensagem-programada")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Editar Follow-up" }));
    expect(screen.getByTestId("formulario-mensagem-programada")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remover Follow-up" }));
    expect(screen.getByText("Remover Follow-up?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    fireEvent.click(screen.getByText("Lembretes"));
    expect(screen.getByText("Ligar").closest("div.rounded-lg")).toHaveClass(
      "border-border",
      "bg-muted/30",
    );
    expect(document.querySelectorAll('[data-slot="mensagem-programada"]')).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "Adicionar" })).toHaveLength(2);
    fireEvent.click(screen.getByRole("button", { name: "Editar Ligar" }));
    expect(screen.getByTestId("formulario-lembrete")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remover Ligar" }));
    expect(screen.getByText("Remover Ligar?")).toBeInTheDocument();
  });
});

function renderizarPainel(
  leadId: string,
  responsavelNome: string | null,
  onRetrair = vi.fn(),
) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PainelDaConversa
        leadId={leadId}
        responsavelNome={responsavelNome}
        onRetrair={onRetrair}
      />
    </QueryClientProvider>,
  );
}
