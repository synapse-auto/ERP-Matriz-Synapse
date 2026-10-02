"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { CircleAlert, FileText, Search } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Skeleton } from "@/components/ui/skeleton";
import type { EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { useTemplatesParaCampanha } from "@/lib/campanhas/hooks";
import type { TemplateParaCampanha } from "@/lib/campanhas/types";
import { variaveisParaOTemplate } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

import { EstadoVazio } from "./estados";

export function chaveDoTemplate(template: { nome: string; idioma: string }): string {
  return `${template.nome}|${template.idioma}`;
}

function CartaoDeTemplate({
  template,
  selecionado,
  aoEscolher,
}: {
  template: TemplateParaCampanha;
  selecionado: boolean;
  aoEscolher: () => void;
}) {
  const textos = useTextos();
  const passo = textos.campanhas.passoTemplate;
  const categorias = textos.templatesWhatsApp.categorias as Record<string, string>;
  const restricoes = passo.restricoes as Record<string, string>;
  return (
    <li>
      <button
        type="button"
        aria-pressed={selecionado}
        disabled={!template.elegivel}
        onClick={aoEscolher}
        className={cn(
          "w-full rounded-lg border p-3 text-left transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          selecionado ? "border-primary bg-accent" : "border-border bg-card hover:bg-muted/50",
          !template.elegivel && "cursor-not-allowed opacity-70 hover:bg-card",
        )}
      >
        <span className="flex flex-wrap items-center gap-2">
          <FileText className="size-4 text-muted-foreground" aria-hidden />
          <span className="font-medium">{template.nome}</span>
          <PillDeStatus tom="neutro">{categorias[template.categoria] ?? template.categoria}</PillDeStatus>
          <span className="text-xs text-muted-foreground">
            {passo.idioma}: {template.idioma}
          </span>
        </span>
        <span className="mt-1 line-clamp-2 block text-xs text-muted-foreground">{template.corpo}</span>
        {!template.elegivel && (
          <span className="mt-2 flex flex-wrap items-center gap-2">
            <PillDeStatus tom="atencao" icone={<CircleAlert className="size-3" aria-hidden />}>
              {passo.naoSuportado}
            </PillDeStatus>
            {template.restricoes.map((restricao) => (
              <span key={restricao} className="text-xs text-muted-foreground">
                {restricoes[restricao] ?? passo.restricaoGenerica}
              </span>
            ))}
          </span>
        )}
      </button>
    </li>
  );
}

interface Props {
  estado: EstadoDoAssistente;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
}

export function ListaDeTemplates({ estado, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoTemplate;
  const consulta = useTemplatesParaCampanha();
  const [busca, setBusca] = useState("");

  const aprovados = useMemo(
    () => (consulta.data ?? []).filter((template) => template.status === "APROVADO"),
    [consulta.data],
  );
  const termo = busca.trim().toLowerCase();
  const visiveis = aprovados.filter((template) => template.nome.toLowerCase().includes(termo));

  if (consulta.isPending) {
    return (
      <div role="status" aria-label={textos.carregando} className="space-y-2">
        {Array.from({ length: 3 }, (_, indice) => (
          <Skeleton key={indice} className="h-20 w-full" />
        ))}
      </div>
    );
  }
  if (consulta.isError) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => consulta.refetch()} />;
  }
  if (aprovados.length === 0) {
    return (
      <EstadoVazio
        titulo={textos.vazio}
        descricao={textos.vazioDescricao}
        acao={
          <Link href="/templates-whatsapp" className={buttonVariants({ variant: "outline" })}>
            {textos.irParaTemplates}
          </Link>
        }
      />
    );
  }

  function escolher(template: TemplateParaCampanha) {
    aoMudar({
      template: { nome: template.nome, idioma: template.idioma, parametros: template.parametros },
      variaveis: variaveisParaOTemplate(estado.variaveis, template.parametros),
    });
  }

  return (
    <div className="space-y-3">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" aria-hidden />
        <Input
          aria-label={textos.busca}
          placeholder={textos.busca}
          value={busca}
          className="pl-8"
          onChange={(evento) => setBusca(evento.target.value)}
        />
      </div>
      {visiveis.length === 0 ? (
        <p className="text-sm text-muted-foreground">{textos.semResultados}</p>
      ) : (
        <ul className="max-h-80 space-y-2 overflow-y-auto pr-1">
          {visiveis.map((template) => (
            <CartaoDeTemplate
              key={chaveDoTemplate(template)}
              template={template}
              selecionado={estado.template !== null && chaveDoTemplate(estado.template) === chaveDoTemplate(template)}
              aoEscolher={() => escolher(template)}
            />
          ))}
        </ul>
      )}
    </div>
  );
}
