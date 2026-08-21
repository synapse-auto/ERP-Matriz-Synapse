"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PainelLateralLead } from "@/components/leads/painel-lateral-lead";
import { useEquipe } from "@/lib/equipe/use-equipe";
import { useAuthStore } from "@/lib/auth/auth-store";
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
import type { VisaoAtendimento } from "@/lib/atendimento/types";

import { BarraDeFiltros } from "./barra-de-filtros";
import { TabelaDeLeads } from "./tabela-de-leads";

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
  const router = useRouter();
  const papel = useAuthStore((estado) => estado.papel);

  const [filtrosAtivos, setFiltrosAtivos] = useState<FiltroAtivo[]>([]);
  const [filtrosRapidos, setFiltrosRapidos] = useState<FiltrosRapidosAgenda>({
    ...FILTROS_RAPIDOS_VAZIOS,
  });
  const [pagina, setPagina] = useState(0);
  const [leadNoPainel, setLeadNoPainel] = useState<string | null>(null);

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
    setLeadNoPainel(lead.id);
  }

  function abrirAtendimento(lead: LeadDaAgenda) {
    const papelAmplo = papel && papel !== "ATENDENTE";
    const visao: VisaoAtendimento =
      lead.status === "IA" ? "POTENCIAIS" : papelAmplo ? "TODOS" : "ATIVOS";
    router.push(
      `/atendimentos?leadId=${encodeURIComponent(lead.id)}&visao=${visao}`,
    );
  }

  const leads = paginaDeLeads.data?.leads ?? [];
  const totalExibido = contagem.data ?? leads.length;
  const textoContador = t.contador
    .replace("{exibindo}", String(leads.length))
    .replace("{total}", String(totalExibido));

  return (
    <div className="flex h-full flex-col overflow-hidden bg-background p-6">
      <header className="-mx-6 -mt-6 mb-4 flex-none border-b border-border bg-card px-6 py-4">
        <h1 className="text-xl font-semibold text-foreground">{t.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t.descricao}</p>
      </header>

      <div className="flex-none">
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
        ) : (
          <TabelaDeLeads
            leads={leads}
            etapas={etapas.data ?? []}
            equipe={equipe.data ?? []}
            textos={t}
            onAbrirFicha={abrirFicha}
            onAbrirAtendimento={abrirAtendimento}
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
          onFechar={() => setLeadNoPainel(null)}
        />
      )}
    </div>
  );
}
