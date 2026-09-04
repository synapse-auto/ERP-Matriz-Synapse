"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PainelLateralLead } from "@/components/leads/painel-lateral-lead";
import { useEquipe } from "@/lib/equipe/use-equipe";
import { useTextos } from "@/lib/config/textos-provider";
import { useCanais, useEtapas } from "@/lib/lead/use-painel-lead";
import {
  useCamposFiltraveis,
  useCatalogosDeFiltro,
  useContagemDeLeads,
  useLeadsDaAgenda,
} from "@/lib/agenda/use-agenda";
import {
  FILTROS_RAPIDOS_VAZIOS,
  type FiltroAtivo,
  type FiltrosRapidosAgenda,
  type LeadDaAgenda,
} from "@/lib/agenda/types";
import { abrirAtendimentoParaLead } from "@/lib/atendimento/api";

import { BarraDeFiltros } from "./barra-de-filtros";
import { ListaDeLeadsMobile } from "./lista-de-leads-mobile";
import { TabelaDeLeads } from "./tabela-de-leads";
import { useBuscaLeadsParaEntrada } from "@/lib/agenda/use-busca-entrada";
import { apiFetch } from "@/lib/api/http-client";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";

/**
 * Agenda como tabela sobre o filtro modular (E16 §Bloco 1) — substitui a lista de cards que
 * reaproveitava `CartaoConversa` e só mostrava atendimentos abertos. Toda linha vem de {@code
 * POST /api/v1/leads/filtrar}, já recortada por RN-CRM-01; nenhum dado é mockado.
 *
 * <p>Kanban e import/export CSV ficam de fora de propósito: não existe endpoint de agrupamento
 * por etapa nem de CSV dos dois lados, e construir a casca sem o motor por trás seria controle
 * fantasma.
 */
export function PaginaAgenda() {
  const textosGerais = useTextos();
  const t = textosGerais.agenda;
  const entrada = t.entrada ?? {
    placeholder: t.filtros.titulo,
    pedir: t.filtros.adicionar,
    responsavel: `${t.semResponsavel}: {nome}`,
  };
  const router = useRouter();

  const [filtrosAtivos, setFiltrosAtivos] = useState<FiltroAtivo[]>([]);
  const [filtrosRapidos, setFiltrosRapidos] = useState<FiltrosRapidosAgenda>({
    ...FILTROS_RAPIDOS_VAZIOS,
  });
  const [pagina, setPagina] = useState(0);
  const [leadNoPainel, setLeadNoPainel] = useState<string | null>(null);
  const [buscaEntrada, setBuscaEntrada] = useState("");
  const [filtrosMobileAbertos, setFiltrosMobileAbertos] = useState(false);
  const telaEstreita = useTelaEstreita();
  const buscaColega = useBuscaLeadsParaEntrada(buscaEntrada);
  const [pedidoEmAndamento, setPedidoEmAndamento] = useState<string | null>(null);
  async function pedirEntrada(id: string) {
    setPedidoEmAndamento(id);
    try {
      await apiFetch(`/api/v1/atendimentos/pedir-entrada?leadId=${encodeURIComponent(id)}`, {
        method: "POST",
      });
    } finally {
      setPedidoEmAndamento(null);
    }
  }

  const abrirAtendimento = useMutation({
    mutationFn: (leadId: string) => abrirAtendimentoParaLead(leadId),
    onSuccess: (resposta) => {
      setLeadNoPainel(null);
      router.push(
        `/atendimentos?leadId=${encodeURIComponent(resposta.leadId)}&visao=ATIVOS`,
      );
    },
  });

  const campos = useCamposFiltraveis();
  const etapas = useEtapas();
  const canais = useCanais();
  const catalogos = useCatalogosDeFiltro();
  const equipe = useEquipe();
  const tagsDisponiveis = catalogos.data?.tags ?? [];
  const paginaDeLeads = useLeadsDaAgenda(
    filtrosRapidos,
    filtrosAtivos,
    tagsDisponiveis,
    pagina,
  );
  const contagem = useContagemDeLeads(
    filtrosRapidos,
    filtrosAtivos,
    tagsDisponiveis,
  );

  function adicionarFiltro(filtro: FiltroAtivo) {
    setFiltrosAtivos((atuais) => [...atuais, filtro]);
    setPagina(0);
  }

  function removerFiltro(id: string) {
    setFiltrosAtivos((atuais) => atuais.filter((filtro) => filtro.id !== id));
    setPagina(0);
  }

  function atualizarFiltrosRapidos(novosFiltros: FiltrosRapidosAgenda) {
    setFiltrosRapidos(novosFiltros);
    setPagina(0);
  }

  function limparFiltros() {
    setFiltrosAtivos([]);
    setFiltrosRapidos({ ...FILTROS_RAPIDOS_VAZIOS });
    setPagina(0);
  }

  function abrirFicha(lead: LeadDaAgenda) {
    abrirAtendimento.reset();
    setLeadNoPainel(lead.id);
  }

  function solicitarAbrirAtendimento(lead: LeadDaAgenda) {
    if (abrirAtendimento.isPending) return;
    abrirAtendimento.mutate(lead.id);
  }

  const leads = paginaDeLeads.data?.leads ?? [];
  const totalExibido = contagem.data ?? leads.length;
  const textoContador = t.contador
    .replace("{exibindo}", String(leads.length))
    .replace("{total}", String(totalExibido));
  const erroAbrir =
    abrirAtendimento.isError
      ? abrirAtendimento.error instanceof Error
        ? abrirAtendimento.error.message
        : t.erroAbrirAtendimento
      : null;

  return (
    <div className="flex h-full flex-col overflow-hidden bg-background p-6 max-sm:p-4">
      <header className={cn(
        "flex-none",
        telaEstreita
          ? "mb-3 flex items-center justify-between gap-3"
          : "-mx-6 -mt-6 mb-4 border-b border-border bg-card px-6 py-4",
      )}>
        <div>
          <h1 className="text-xl font-bold text-foreground max-sm:text-[1.375rem]">{t.titulo}</h1>
          {!telaEstreita && <p className="mt-1 text-sm text-muted-foreground">{t.descricao}</p>}
        </div>
        {telaEstreita && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="rounded-full"
            aria-pressed={filtrosMobileAbertos}
            onClick={() => setFiltrosMobileAbertos((abertos) => !abertos)}
          >
            {t.filtros.titulo}
          </Button>
        )}
      </header>

      <div className="flex-none">
        {!telaEstreita && (
          <div className="mb-3 flex items-center gap-2">
            <Input value={buscaEntrada} onChange={(evento) => setBuscaEntrada(evento.target.value)} placeholder={entrada.placeholder} aria-label={entrada.placeholder} />
            {buscaColega.data?.map((lead) => <div key={lead.id} className="flex items-center gap-2 rounded-md border border-border px-2 py-1 text-xs"><span>{lead.nome}{lead.empresa ? ` · ${lead.empresa}` : ""}</span><span className="text-muted-foreground">{entrada.responsavel.replace("{nome}", lead.responsavelNome)}</span><Button size="sm" variant="outline" onClick={() => pedirEntrada(lead.id)} disabled={pedidoEmAndamento === lead.id}>{entrada.pedir}</Button></div>)}
          </div>
        )}
        {(!telaEstreita || filtrosMobileAbertos) && (
          <BarraDeFiltros
            campos={campos.data ?? []}
            carregandoCampos={campos.isLoading}
            erroCampos={campos.isError}
            onTentarCamposNovamente={() => campos.refetch()}
            filtrosAtivos={filtrosAtivos}
            filtrosRapidos={filtrosRapidos}
            cidades={catalogos.data?.cidades ?? []}
            referencias={{
              etapas: etapas.data ?? [],
              canais: canais.data ?? [],
              equipe: equipe.data ?? [],
              tags: tagsDisponiveis,
            }}
            textos={t.filtros}
            textosStatus={t.status}
            textoSemResponsavel={t.semResponsavel}
            contador={
              <p className="text-sm whitespace-nowrap text-muted-foreground">
                {textoContador}
              </p>
            }
            onAdicionar={adicionarFiltro}
            onFiltrosRapidosChange={atualizarFiltrosRapidos}
            onRemover={removerFiltro}
            onLimparTudo={limparFiltros}
          />
        )}
        {telaEstreita && !filtrosMobileAbertos && (
          <div className="mb-3">
            <Input
              value={filtrosRapidos.busca}
              onChange={(evento) => atualizarFiltrosRapidos({ ...filtrosRapidos, busca: evento.target.value })}
              placeholder={t.filtros.buscaPlaceholder}
              aria-label={t.filtros.busca}
            />
            <p className="mt-2 text-sm text-muted-foreground">{textoContador}</p>
          </div>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {paginaDeLeads.isLoading ? (
          <p className="text-sm text-muted-foreground">{t.carregando}</p>
        ) : paginaDeLeads.isError ? (
          <ErroDeCarregamento
            mensagem={t.erro}
            onTentarNovamente={() => paginaDeLeads.refetch()}
          />
        ) : leads.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.vazia}</p>
        ) : telaEstreita ? (
          <ListaDeLeadsMobile
            leads={leads}
            etapas={etapas.data ?? []}
            equipe={equipe.data ?? []}
            textos={t}
            onAbrirFicha={abrirFicha}
          />
        ) : (
          <TabelaDeLeads
            leads={leads}
            etapas={etapas.data ?? []}
            equipe={equipe.data ?? []}
            textos={t}
            onAbrirFicha={abrirFicha}
            onAbrirAtendimento={solicitarAbrirAtendimento}
            abrindoLeadId={abrirAtendimento.isPending ? (abrirAtendimento.variables ?? null) : null}
          />
        )}
      </div>

      <div className="mt-3 flex flex-none justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          disabled={pagina === 0}
          onClick={() => setPagina((atual) => atual - 1)}
        >
          {t.paginacao.anterior}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={!paginaDeLeads.data?.temMais}
          onClick={() => setPagina((atual) => atual + 1)}
        >
          {t.paginacao.proxima}
        </Button>
      </div>

      {leadNoPainel && (
        <PainelLateralLead
          leadId={leadNoPainel}
          onFechar={() => {
            if (abrirAtendimento.isPending) return;
            setLeadNoPainel(null);
            abrirAtendimento.reset();
          }}
          onAbrirAtendimento={() => {
            const lead = leads.find((item) => item.id === leadNoPainel);
            if (lead) solicitarAbrirAtendimento(lead);
          }}
          abrindoAtendimento={abrirAtendimento.isPending}
          erroAbrirAtendimento={erroAbrir}
        />
      )}
    </div>
  );
}
