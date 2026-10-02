"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { useTemplatesParaCampanha } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

import { BalaoDeTemplate } from "./balao-de-template";
import { chaveDoTemplate, ListaDeTemplates } from "./lista-de-templates";
import { MapeamentoDeVariaveis } from "./mapeamento-de-variaveis";

interface Props {
  estado: EstadoDoAssistente;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
  mostrarErros: boolean;
}

export function PassoTemplate({ estado, aoMudar, mostrarErros }: Props) {
  const textos = useTextos().campanhas.passoTemplate;
  const consulta = useTemplatesParaCampanha();
  const escolhido = consulta.data?.find(
    (template) => estado.template && chaveDoTemplate(template) === chaveDoTemplate(estado.template),
  );
  const nomeVazio = mostrarErros && estado.nome.trim().length === 0;
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
      <div className="space-y-5">
        <div className="space-y-1.5">
          <Label htmlFor="nome-da-campanha">{textos.nome}</Label>
          <Input
            id="nome-da-campanha"
            value={estado.nome}
            maxLength={120}
            placeholder={textos.nomePlaceholder}
            aria-invalid={nomeVazio}
            aria-describedby="nome-da-campanha-ajuda"
            onChange={(evento) => aoMudar({ nome: evento.target.value })}
          />
          <p
            id="nome-da-campanha-ajuda"
            className={cn("text-xs", nomeVazio ? "text-destructive" : "text-muted-foreground")}
          >
            {nomeVazio ? textos.erroNome : textos.nomeAjuda}
          </p>
        </div>
        <section className="space-y-2" aria-labelledby="escolha-do-template">
          <h2 id="escolha-do-template" className="text-sm font-bold">
            {textos.escolha}
          </h2>
          <ListaDeTemplates estado={estado} aoMudar={aoMudar} />
          {mostrarErros && !estado.template && <p className="text-xs text-destructive">{textos.erroTemplate}</p>}
        </section>
        {estado.template && (
          <section className="space-y-2" aria-labelledby="variaveis-do-template">
            <h2 id="variaveis-do-template" className="text-sm font-bold">
              {textos.variaveis}
            </h2>
            <MapeamentoDeVariaveis variaveis={estado.variaveis} aoMudar={(variaveis) => aoMudar({ variaveis })} />
          </section>
        )}
        <p className="text-xs text-muted-foreground">{textos.aviso}</p>
      </div>
      <aside className="lg:sticky lg:top-6 lg:self-start">
        <h2 className="mb-2 text-sm font-bold">{textos.previa}</h2>
        {escolhido ? (
          <BalaoDeTemplate corpo={escolhido.corpo} variaveis={estado.variaveis} />
        ) : (
          <p className="rounded-xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            {textos.escolha}
          </p>
        )}
      </aside>
    </div>
  );
}
