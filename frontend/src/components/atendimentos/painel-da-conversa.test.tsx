import { fireEvent, render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { EtapaAtendimento } from "@/lib/lead/types";
import type { Lembrete, MensagemProgramada } from "@/lib/suporte/types";

type LeadTeste = {
  id: string;
  nome: string;
  fotoUrl: string | null;
  empresa: string | null;
  codigo: string | null;
  telefone: string | null;
  email: string | null;
  localizacao: string | null;
  etapaAtendimentoId: string | null;
  numAtendimentos: number;
  numMensagens: number;
  resumoIa: string | null;
  resumoIaAtualizadoEm: string | null;
  notas: string | null;
};

const leadState = vi.hoisted(() => ({
  data: {
    id: "lead-1",
    nome: "Marcos Vinícius",
    fotoUrl: null,
    empresa: "Vidraçaria Cristal",
    codigo: null,
    telefone: "(61) 99999-0000",
    email: "marcos@cliente.com",
    localizacao: "Taguatinga · DF",
    etapaAtendimentoId: "etapa-1",
    numAtendimentos: 3,
    numMensagens: 20,
    resumoIa: "Cliente pediu orçamento de box.",
    resumoIaAtualizadoEm: "2030-01-02T12:00:00Z",
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
const salvarFichaState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
}));
const solicitarResumoState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
}));
const authState = vi.hoisted(() => ({ papel: "ADMINISTRADOR" as string | null }));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: typeof authState) => unknown) => seletor(authState),
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
        notas: {
          placeholder: "Observações compartilhadas",
          salvar: "Salvar nota",
          salvando: "Salvando nota...",
          salvo: "Nota salva.",
          erro: "Erro ao salvar nota",
        },
        resumoIa: {
          vazio: "Nenhum resumo gerado ainda.",
          ultimaGeracao: "Última geração: {data}",
          gerar: "Gerar",
          regerar: "Regerar",
          processando: "Gerando resumo...",
          pendente: "Resumo aguardando processamento.",
          erro: "Não foi possível gerar o resumo.",
          indisponivel: "Resumo indisponível",
        },
        adicionar: "Adicionar",
        editar: "Editar",
        remover: "Remover",
        confirmarRemocao: "Remover {item}?",
        cancelarRemocao: "Cancelar",
        erroOperacao: "Erro",
        secoes: {
          resumo: "Resumo por IA",
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
    lembretes: {
      status: { pendente: "Pendente", concluido: "Concluído" },
    },
    mensagensProgramadas: {
      status: { agendada: "Agendada", enviada: "Enviada", cancelada: "Cancelada" },
    },
    painelLead: {
      dados: {
        nome: "Nome",
        nomeInvalido: "Informe o nome do cliente.",
        telefone: "Telefone",
        email: "E-mail",
        codigo: "Código",
        codigoPlaceholder: "Somente números",
        codigoInvalido: "Informe apenas números, com no máximo 20 dígitos.",
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
  useEstadoResumoIa: () => ({ data: null, isLoading: false }),
  useSolicitarResumoIa: () => solicitarResumoState,
  useMidiasDoLead: () => ({ data: { pages: [[]] }, isLoading: false, isError: false, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() }),
  useTagsDoLead: () => ({
    data: [{ id: "tag-1", nome: "Prioridade", cor: "#dc2626", icone: null }],
  }),
  useTodasAsTags: () => ({
    data: [{ id: "tag-1", nome: "Prioridade", cor: "#dc2626", icone: null }],
  }),
  useVincularTag: () => ({ mutate: vi.fn() }),
  useDesvincularTag: () => ({ mutate: vi.fn() }),
  useSalvarFicha: () => salvarFichaState,
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
  beforeEach(() => {
    authState.papel = "ADMINISTRADOR";
    salvarFichaState.mutate.mockClear();
    solicitarResumoState.mutate.mockClear();
    solicitarResumoState.isPending = false;
    suporteState.mensagens = [];
    suporteState.lembretes = [];
    leadState.data = {
      id: "lead-1",
      nome: "Marcos Vinícius",
      fotoUrl: null,
      empresa: "Vidraçaria Cristal",
      codigo: null,
      telefone: "(61) 99999-0000",
      email: "marcos@cliente.com",
      localizacao: "Taguatinga · DF",
      etapaAtendimentoId: "etapa-1",
      numAtendimentos: 3,
      numMensagens: 20,
      resumoIa: "Cliente pediu orçamento de box.",
      resumoIaAtualizadoEm: "2030-01-02T12:00:00Z",
      notas: "",
    };
    etapasState.data = [
      { id: "etapa-0", nome: "Novo contato", ordem: 1, corVisual: "var(--muted)" },
      { id: "etapa-1", nome: "Orçamento", ordem: 2, corVisual: "var(--primary)" },
      { id: "etapa-2", nome: "Negociação", ordem: 3, corVisual: "var(--accent)" },
    ];
  });
  it("mostra contadores, etapa e resumo por IA recolhido — sem seção de arquivos", () => {
    const onRetrair = vi.fn();
    renderizarPainel("lead-1", "Jardel Lima", onRetrair);

    expect(screen.getByLabelText("Nome")).toHaveValue("Marcos Vinícius");
    expect(screen.getByText("Informações gerais")).toBeInTheDocument();
    expect(screen.getByText("Jardel Lima")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("2 de 3")).toBeInTheDocument();
    expect(screen.getByText("Prioridade")).toBeInTheDocument();
    expect(screen.getByText("Tag")).toBeInTheDocument();
    const resumo = screen.getByRole("button", { name: /Resumo por IA/ });
    const acaoResumo = screen.getByRole("button", { name: "Regerar" });
    expect(resumo.parentElement).toContainElement(acaoResumo);
    expect(resumo).not.toContainElement(acaoResumo);
    expect(resumo).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(acaoResumo);
    expect(resumo).toHaveAttribute("aria-expanded", "false");
    expect(solicitarResumoState.mutate).toHaveBeenCalledOnce();
    fireEvent.click(resumo);
    expect(screen.getByText("Cliente pediu orçamento de box.")).toBeInTheDocument();
    expect(screen.getByText(/Última geração:/)).toBeInTheDocument();
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

  it("mantém a ação no cabeçalho e desabilita enquanto o resumo é gerado", () => {
    solicitarResumoState.isPending = true;
    renderizarPainel("lead-1", "Jardel Lima");

    const resumo = screen.getByRole("button", { name: /Resumo por IA/ });
    const acaoResumo = screen.getByRole("button", { name: "Gerando resumo..." });
    expect(acaoResumo).toBeDisabled();
    expect(resumo.parentElement).toContainElement(acaoResumo);
    expect(resumo).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(resumo);
    expect(screen.getByLabelText("Gerando resumo...")).toBeInTheDocument();
  });

  it("mantém um card único, com a ação antes do chevron e sem botões aninhados", () => {
    renderizarPainel("lead-1", "Jardel Lima");

    const resumo = screen.getByRole("button", { name: /Resumo por IA/ });
    const acaoResumo = screen.getByRole("button", { name: "Regerar" });
    const card = resumo.closest('[data-slot="secao-colapsavel"]');
    const cabecalho = card?.querySelector('[data-slot="secao-colapsavel-cabecalho"]');
    const chevron = card?.querySelector('[data-slot="secao-colapsavel-chevron"]');

    expect(card).toHaveClass("rounded-lg", "border-border", "bg-background");
    expect(resumo.querySelector("button")).not.toBeInTheDocument();
    expect(cabecalho).toBeInTheDocument();
    expect(chevron).toBeInTheDocument();
    expect(Array.from(cabecalho?.children ?? []).indexOf(acaoResumo)).toBeLessThan(
      Array.from(cabecalho?.children ?? []).indexOf(chevron as Element),
    );

    fireEvent.click(chevron as Element);
    expect(resumo).toHaveAttribute("aria-expanded", "true");
    expect(card).toHaveClass("border-primary/40");
  });

  it.each(["ATENDENTE", "SUBGESTOR", "GESTOR"])(
    "esconde etapa para %s, mas preserva resumo e o restante da ficha",
    (papel) => {
      authState.papel = papel;
      renderizarPainel("lead-1", "Jardel Lima");

      expect(screen.getByText("Informações gerais")).toBeInTheDocument();
      expect(screen.getByText("(61) 99999-0000")).toBeInTheDocument();
      expect(screen.getByText("Notas internas")).toBeInTheDocument();
      expect(screen.queryByText("Etapa")).not.toBeInTheDocument();
      expect(screen.queryByText("Orçamento")).not.toBeInTheDocument();
      expect(screen.queryByText("2 de 3")).not.toBeInTheDocument();
      const resumo = screen.getByRole("button", { name: /Resumo por IA/ });
      fireEvent.click(resumo);
      expect(screen.getByText("Cliente pediu orçamento de box.")).toBeInTheDocument();
    },
  );

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
      resumoIaAtualizadoEm: null,
      notas: null,
    };
    etapasState.data = [];

    renderizarPainel("lead-2", null);

    expect(screen.getByLabelText("Nome")).toHaveValue("Lead sem dados opcionais");
    expect(screen.queryByText("E-mail")).not.toBeInTheDocument();
    expect(screen.queryByText("Localização")).not.toBeInTheDocument();
    expect(screen.queryByText("Etapa")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Código")).toBeInTheDocument();
  });

  it("destaca lembretes concluidos e preserva o visual dos pendentes", () => {
    suporteState.lembretes = [
      {
        id: "lembrete-pendente",
        leadId: "lead-1",
        leadNome: "Marcos",
        atendenteId: "atendente-1",
        atendenteNome: "Jardel Lima",
        texto: "Ligar",
        dataHora: "2030-01-01T12:00:00Z",
        origemAutomatica: false,
        status: "PENDENTE",
      },
      {
        id: "lembrete-concluido",
        leadId: "lead-1",
        leadNome: "Marcos",
        atendenteId: "atendente-1",
        atendenteNome: "Jardel Lima",
        texto: "Enviar contrato",
        dataHora: "2030-01-02T12:00:00Z",
        origemAutomatica: false,
        status: "CONCLUIDO",
      },
    ];

    renderizarPainel("lead-1", "Jardel Lima");
    fireEvent.click(screen.getByRole("button", { name: /Lembretes/ }));

    const pendente = screen.getByText("Ligar").closest('[data-slot="lembrete"]');
    const concluido = screen.getByText("Enviar contrato").closest('[data-slot="lembrete"]');
    expect(pendente).not.toBeNull();
    expect(concluido).not.toBeNull();
    expect(pendente).toHaveClass("border-border", "bg-muted/30");
    expect(screen.getByText("Ligar")).toHaveClass("text-foreground");
    expect(screen.getByText("Ligar")).not.toHaveClass("line-through");
    expect(concluido).toHaveClass("border-2", "border-cor-sucesso", "bg-cor-sucesso/10");
    expect(screen.getByText("Enviar contrato")).toHaveClass(
      "text-muted-foreground",
      "line-through",
    );
    expect(within(concluido as HTMLElement).getByText(/Conclu/)).toBeInTheDocument();
    expect(
      (concluido as HTMLElement).querySelector('[data-slot="indicador-concluido"]'),
    ).not.toBeNull();
  });

  it("mostra mensagens enviadas como concluidas e canceladas sem acoes", () => {
    suporteState.mensagens = [
      {
        id: "mensagem-agendada",
        leadId: "lead-1",
        leadNome: "Marcos",
        atendenteId: "atendente-1",
        atendenteNome: "Jardel Lima",
        conteudo: "Mensagem agendada",
        dataEnvio: "2030-01-01T12:00:00Z",
        status: "AGENDADA",
      },
      {
        id: "mensagem-enviada",
        leadId: "lead-1",
        leadNome: "Marcos",
        atendenteId: "atendente-1",
        atendenteNome: "Jardel Lima",
        conteudo: "Mensagem enviada",
        dataEnvio: "2030-01-02T12:00:00Z",
        status: "ENVIADA",
      },
      {
        id: "mensagem-cancelada",
        leadId: "lead-1",
        leadNome: "Marcos",
        atendenteId: "atendente-1",
        atendenteNome: "Jardel Lima",
        conteudo: "Mensagem cancelada",
        dataEnvio: "2030-01-03T12:00:00Z",
        status: "CANCELADA",
      },
    ];

    renderizarPainel("lead-1", "Jardel Lima");
    const secao = screen.getByRole("button", { name: /Mensagens programadas/ });
    fireEvent.click(secao);

    expect(secao).toHaveTextContent("3");
    expect(document.querySelectorAll('[data-slot="mensagem-programada"]')).toHaveLength(3);

    const agendada = screen.getByText("Mensagem agendada").closest('[data-slot="mensagem-programada"]');
    const enviada = screen.getByText("Mensagem enviada").closest('[data-slot="mensagem-programada"]');
    const cancelada = screen.getByText("Mensagem cancelada").closest('[data-slot="mensagem-programada"]');
    expect(agendada).toHaveClass("border-2", "border-primary", "bg-primary/10", "shadow-sm");
    expect(
      within(agendada as HTMLElement).getByRole("button", { name: "Editar Mensagem agendada" }),
    ).toBeInTheDocument();
    expect(enviada).toHaveClass("border-2", "border-cor-sucesso", "bg-cor-sucesso/10", "shadow-sm");
    expect(screen.getByText("Mensagem enviada")).toHaveClass(
      "text-muted-foreground",
      "line-through",
    );
    expect(within(enviada as HTMLElement).getByText("Enviada")).toBeInTheDocument();
    expect(
      (enviada as HTMLElement).querySelector('[data-slot="indicador-concluido"]'),
    ).not.toBeNull();
    expect(within(enviada as HTMLElement).queryByRole("button")).not.toBeInTheDocument();
    expect(cancelada).toHaveClass("border-border", "bg-muted/30");
    expect(within(cancelada as HTMLElement).getByText("Cancelada")).toBeInTheDocument();
    expect(within(cancelada as HTMLElement).queryByRole("button")).not.toBeInTheDocument();
  });

  it("grava o nome ao sair do campo", () => {
    renderizarPainel("lead-1", "Jardel Lima");
    const campo = screen.getByLabelText("Nome");
    fireEvent.change(campo, { target: { value: "  Maria Silva  " } });
    fireEvent.blur(campo);
    expect(salvarFichaState.mutate).toHaveBeenCalledWith(
      { nome: "Maria Silva" },
      expect.any(Object),
    );
  });

  it("nao grava nome vazio e restaura o anterior", () => {
    renderizarPainel("lead-1", "Jardel Lima");
    const campo = screen.getByLabelText("Nome");
    fireEvent.change(campo, { target: { value: "   " } });
    fireEvent.blur(campo);
    expect(salvarFichaState.mutate).not.toHaveBeenCalled();
    expect(campo).toHaveValue("Marcos Vinícius");
    expect(screen.getByRole("alert")).toHaveTextContent("Informe o nome do cliente.");
  });

  it("grava o código numérico ao sair do campo e descarta letras coladas", () => {
    renderizarPainel("lead-1", "Jardel Lima");
    const campo = screen.getByLabelText("Código");
    fireEvent.change(campo, { target: { value: "00a421" } });
    expect(campo).toHaveValue("00421");
    fireEvent.blur(campo);
    expect(salvarFichaState.mutate).toHaveBeenCalledWith(
      { codigo: "00421" },
      expect.any(Object),
    );
  });

  it("edita e salva notas internas pela atualização parcial da ficha", () => {
    salvarFichaState.mutate.mockImplementation((dados: { notas: string }, opcoes: { onSuccess: (ficha: unknown) => void }) => {
      opcoes.onSuccess({ ...leadState.data, notas: dados.notas });
    });
    renderizarPainel("lead-1", "Jardel Lima");
    fireEvent.click(screen.getByRole("button", { name: /Notas internas/ }));
    const campo = screen.getByLabelText("Notas internas");
    fireEvent.change(campo, { target: { value: "Retornar com medidas" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar nota" }));

    expect(salvarFichaState.mutate).toHaveBeenCalledWith(
      { notas: "Retornar com medidas" },
      expect.any(Object),
    );
    expect(screen.getByRole("status")).toHaveTextContent("Nota salva.");
    expect(campo).toHaveValue("Retornar com medidas");
  });

  it("restaura a nota confirmada quando o salvamento falha", () => {
    salvarFichaState.mutate.mockImplementation((_dados: unknown, opcoes: { onError: () => void }) => {
      opcoes.onError();
    });
    leadState.data = { ...leadState.data, notas: "Nota confirmada" };
    renderizarPainel("lead-1", "Jardel Lima");
    fireEvent.click(screen.getByRole("button", { name: /Notas internas/ }));
    const campo = screen.getByLabelText("Notas internas");
    fireEvent.change(campo, { target: { value: "Alteração recusada" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar nota" }));

    expect(screen.getByRole("status")).toHaveTextContent("Erro ao salvar nota");
    expect(campo).toHaveValue("Nota confirmada");
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
        atendimentoId="atendimento-1"
        responsavelNome={responsavelNome}
        onRetrair={onRetrair}
      />
    </QueryClientProvider>,
  );
}
