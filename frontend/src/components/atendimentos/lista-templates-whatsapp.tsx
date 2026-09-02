"use client";

import { useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import type { CategoriaTemplateWhatsApp, TemplateWhatsApp } from "@/lib/atendimento/types";
import {
  interpolarCatalogo,
  interpolarCorpoDoTemplate,
  parametrosDoTemplatePreenchidos,
  trechoDaVariavel,
} from "@/lib/atendimento/variaveis-do-template";
import type { Textos } from "@/lib/config/schema";

type TextosComposer = Textos["atendimentos"]["composer"];
type RotulosDeCategoria = Textos["templatesWhatsApp"]["categorias"];

type Props = {
  textos: TextosComposer;
  rotulosDeCategoria?: RotulosDeCategoria;
  templates: {
    data?: TemplateWhatsApp[];
    isError: boolean;
    isLoading: boolean;
  };
  parametros: Record<string, string[]>;
  onParametros: (chave: string, valores: string[]) => void;
  enviando: boolean;
  onEnviar: (template: TemplateWhatsApp, valores: string[]) => void;
  /** No diálogo de novo contato, o botão escolhe o template; no composer continua enviando. */
  modoSelecao?: boolean;
  templateSelecionado?: string | null;
  rotuloAcao?: string;
};

const ORDEM_DAS_CATEGORIAS: CategoriaTemplateWhatsApp[] = [
  "UTILIDADE",
  "MARKETING",
  "AUTENTICACAO",
];

export function ListaTemplatesWhatsApp({
  textos,
  rotulosDeCategoria,
  templates,
  parametros,
  onParametros,
  enviando,
  onEnviar,
  modoSelecao = false,
  templateSelecionado = null,
  rotuloAcao,
}: Props) {
  const [busca, setBusca] = useState("");
  const [tocados, setTocados] = useState<Record<string, boolean>>({});
  const aprovados = (templates.data ?? []).filter((item) => item.status === "APROVADO");
  const filtrados = filtrarTemplates(aprovados, busca, rotulosDeCategoria);
  const grupos = agruparPorCategoria(filtrados);

  return (
    <div className="space-y-3">
      {templates.isError ? (
        <p className="text-sm text-destructive">{textos.templatesErro}</p>
      ) : templates.isLoading ? (
        <p className="text-sm text-muted-foreground">{textos.templatesCarregando}</p>
      ) : aprovados.length === 0 ? (
        <p className="text-sm text-muted-foreground">{textos.semTemplates}</p>
      ) : (
        <>
          {aprovados.length > 1 && (
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" />
              <Input
                value={busca}
                onChange={(evento) => setBusca(evento.target.value)}
                placeholder={textos.buscaTemplate}
                className="h-9 bg-muted/40 pl-9"
                aria-label={textos.buscaTemplate}
              />
            </div>
          )}
          {filtrados.length === 0 ? (
            <p className="text-sm text-muted-foreground">{textos.semResultadosTemplate}</p>
          ) : (
            <ul className="max-h-72 space-y-3 overflow-y-auto">
              {grupos.map((grupo) => (
                <li key={grupo.categoria} className="space-y-2">
                  {grupos.length > 1 && rotulosDeCategoria && (
                    <p className="px-0.5 text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
                      {rotulosDeCategoria[grupo.categoria]}
                    </p>
                  )}
                  <ul className="space-y-2">
                    {grupo.itens.map((template) => {
                      const chave = `${template.nome}:${template.idioma}`;
                      const valores =
                        parametros[chave] ?? Array(template.quantidadeDeParametros).fill("");
                      return (
                        <CartaoDeTemplate
                          key={chave}
                          template={template}
                          textos={textos}
                          rotuloDeCategoria={
                            grupos.length === 1 ? rotulosDeCategoria?.[template.categoria] : undefined
                          }
                          valores={valores}
                          selecionado={templateSelecionado === chave}
                          tocado={Boolean(tocados[chave])}
                          enviando={enviando}
                          rotuloAcao={modoSelecao ? rotuloAcao : undefined}
                          onTocar={() => setTocados((atual) => ({ ...atual, [chave]: true }))}
                          onParametros={(proximo) => onParametros(chave, proximo)}
                          onEnviar={() => onEnviar(template, valores)}
                        />
                      );
                    })}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <Link
        href="/templates-whatsapp"
        className="inline-flex text-xs font-medium text-primary underline-offset-4 hover:underline"
      >
        {textos.criarTemplate}
      </Link>
    </div>
  );
}

function CartaoDeTemplate({
  template,
  textos,
  rotuloDeCategoria,
  valores,
  selecionado,
  tocado,
  enviando,
  rotuloAcao,
  onTocar,
  onParametros,
  onEnviar,
}: {
  template: TemplateWhatsApp;
  textos: TextosComposer;
  rotuloDeCategoria?: string;
  valores: string[];
  selecionado: boolean;
  tocado: boolean;
  enviando: boolean;
  rotuloAcao?: string;
  onTocar: () => void;
  onParametros: (valores: string[]) => void;
  onEnviar: () => void;
}) {
  const preenchido = parametrosDoTemplatePreenchidos(valores);
  const previa = interpolarCorpoDoTemplate(template.corpo, valores);

  return (
    <article
      className={`rounded-xl border bg-muted/30 p-3 ${selecionado ? "border-primary ring-1 ring-primary/30" : "border-border"}`}
      data-selecionado={selecionado ? "true" : undefined}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="text-sm font-medium text-foreground">{template.nome}</p>
        {rotuloDeCategoria && (
          <PillDeStatus tom="neutro">{rotuloDeCategoria}</PillDeStatus>
        )}
      </div>
      <p className="mt-2 text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
        {textos.previaTemplate}
      </p>
      <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">{previa}</p>
      {template.quantidadeDeParametros > 0 && (
        <div className="mt-3 space-y-2">
          {valores.map((valor, indice) => {
            const numero = indice + 1;
            const vazio = valor.trim() === "";
            const invalido = tocado && vazio;
            const trecho = trechoDaVariavel(template.corpo, numero);
            const id = `${template.nome}-${template.idioma}-${numero}`;
            return (
              <div key={id}>
                <label htmlFor={id} className="block text-xs font-medium text-foreground">
                  {interpolarCatalogo(textos.parametroTemplate, { indice: String(numero) })}
                </label>
                <p className="mt-0.5 font-mono text-[0.7rem] text-muted-foreground">{trecho}</p>
                <Input
                  id={id}
                  className="mt-1"
                  value={valor}
                  aria-invalid={invalido}
                  aria-describedby={invalido ? `${id}-erro` : undefined}
                  placeholder={interpolarCatalogo(textos.parametroTemplate, {
                    indice: String(numero),
                  })}
                  onChange={(evento) => {
                    onTocar();
                    const proximo = [...valores];
                    proximo[indice] = evento.target.value;
                    onParametros(proximo);
                  }}
                />
                {invalido && (
                  <p id={`${id}-erro`} className="mt-1 text-xs text-destructive" role="alert">
                    {textos.parametroObrigatorio}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}
      <Button
        type="button"
        size="sm"
        className="mt-3"
        disabled={enviando || !preenchido}
        onClick={() => {
          if (!preenchido) return;
          onEnviar();
        }}
      >
        {rotuloAcao ?? textos.enviarTemplate}
      </Button>
    </article>
  );
}

function filtrarTemplates(
  templates: TemplateWhatsApp[],
  busca: string,
  rotulos?: RotulosDeCategoria,
): TemplateWhatsApp[] {
  const termo = busca.trim().toLowerCase();
  if (!termo) {
    return templates;
  }
  return templates.filter((template) => {
    const categoria = rotulos?.[template.categoria] ?? template.categoria;
    return (
      template.nome.toLowerCase().includes(termo)
      || template.corpo.toLowerCase().includes(termo)
      || template.idioma.toLowerCase().includes(termo)
      || categoria.toLowerCase().includes(termo)
    );
  });
}

function agruparPorCategoria(
  templates: TemplateWhatsApp[],
): { categoria: CategoriaTemplateWhatsApp; itens: TemplateWhatsApp[] }[] {
  const porCategoria = new Map<CategoriaTemplateWhatsApp, TemplateWhatsApp[]>();
  for (const template of templates) {
    const itens = porCategoria.get(template.categoria) ?? [];
    itens.push(template);
    porCategoria.set(template.categoria, itens);
  }
  return ORDEM_DAS_CATEGORIAS.filter((categoria) => porCategoria.has(categoria)).map(
    (categoria) => ({ categoria, itens: porCategoria.get(categoria) ?? [] }),
  );
}
