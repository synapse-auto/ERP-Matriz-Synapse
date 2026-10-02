"use client";

import { FilterX } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { SeletorMultiplo } from "@/components/ui/seletor-multiplo";
import { Switch } from "@/components/ui/switch";
import { FILTRO_VAZIO, temFiltro, type FiltroEditavel } from "@/lib/campanhas/estado-do-assistente";
import { useEtapasParaFiltro } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";
import { useTags } from "@/lib/tags/use-tags";

interface Props {
  filtro: FiltroEditavel;
  aoMudar: (filtro: FiltroEditavel) => void;
}

/** Filtros opcionais do público. Sem nenhum filtro, o público é a Agenda inteira. */
export function FiltrosDoPublico({ filtro, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoPublico;
  const tags = useTags();
  const etapas = useEtapasParaFiltro();
  const mudar = (parte: Partial<FiltroEditavel>) => aoMudar({ ...filtro, ...parte });

  return (
    <fieldset className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
      <legend className="px-1 text-sm font-bold">{textos.filtrosOpcionais}</legend>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label>{textos.tags}</Label>
          <SeletorMultiplo
            ariaLabel={textos.tags}
            placeholder={textos.tagsPlaceholder}
            valores={filtro.tagIds}
            opcoes={(tags.data ?? []).map((tag) => ({ valor: tag.id, rotulo: tag.nome }))}
            onChange={(tagIds) => mudar({ tagIds })}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="filtro-etapa">{textos.etapa}</Label>
          <Seletor
            id="filtro-etapa"
            valor={filtro.etapaId}
            placeholder={textos.etapaPlaceholder}
            opcoes={[
              { valor: "", rotulo: textos.etapaPlaceholder },
              ...(etapas.data ?? []).map((etapa) => ({ valor: etapa.id, rotulo: etapa.nome })),
            ]}
            onChange={(etapaId) => mudar({ etapaId })}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="filtro-desde">{textos.cadastroDesde}</Label>
          <SeletorData id="filtro-desde" valor={filtro.cadastroDesde} placeholder={textos.cadastroDesde} onChange={(cadastroDesde) => mudar({ cadastroDesde })} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="filtro-ate">{textos.cadastroAte}</Label>
          <SeletorData id="filtro-ate" valor={filtro.cadastroAte} placeholder={textos.cadastroAte} onChange={(cadastroAte) => mudar({ cadastroAte })} />
        </div>
        <div className="space-y-1.5 sm:col-span-2">
          <Label htmlFor="filtro-busca">{textos.busca}</Label>
          <Input
            id="filtro-busca"
            value={filtro.busca}
            maxLength={100}
            placeholder={textos.buscaPlaceholder}
            onChange={(evento) => mudar({ busca: evento.target.value })}
          />
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-sm">
          <Switch checked={filtro.nuncaConversou} onCheckedChange={(nuncaConversou) => mudar({ nuncaConversou })} />
          {textos.nuncaConversou}
        </label>
        {temFiltro(filtro) && (
          <Button type="button" variant="ghost" size="sm" onClick={() => aoMudar(FILTRO_VAZIO)}>
            <FilterX aria-hidden />
            {textos.limparFiltros}
          </Button>
        )}
      </div>
    </fieldset>
  );
}
