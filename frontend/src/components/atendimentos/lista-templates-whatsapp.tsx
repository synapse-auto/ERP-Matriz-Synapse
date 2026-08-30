"use client";

import Link from "next/link";

import { Button } from "@/components/ui/button";
import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import type { Textos } from "@/lib/config/schema";

type TextosComposer = Textos["atendimentos"]["composer"];

type Props = {
  textos: TextosComposer;
  templates: {
    data?: TemplateWhatsApp[];
    isError: boolean;
    isLoading: boolean;
  };
  parametros: Record<string, string[]>;
  onParametros: (chave: string, valores: string[]) => void;
  enviando: boolean;
  onEnviar: (template: TemplateWhatsApp, valores: string[]) => void;
};

export function ListaTemplatesWhatsApp({
  textos,
  templates,
  parametros,
  onParametros,
  enviando,
  onEnviar,
}: Props) {
  const aprovados = (templates.data ?? []).filter((item) => item.status === "APROVADO");

  return (
    <>
      {templates.isError ? (
        <p className="text-xs text-destructive">{textos.templatesErro}</p>
      ) : templates.isLoading ? (
        <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
      ) : aprovados.length === 0 ? (
        <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
      ) : (
        <ul className="max-h-48 space-y-2 overflow-y-auto">
          {aprovados.map((template) => {
            const chave = `${template.nome}:${template.idioma}`;
            const valores = parametros[chave] ?? Array(template.quantidadeDeParametros).fill("");
            return (
              <li key={chave} className="rounded-lg border border-border p-2">
                <p className="text-sm font-medium">{template.nome}</p>
                <p className="mt-1 text-xs text-muted-foreground">{template.corpo}</p>
                {template.quantidadeDeParametros > 0 && (
                  <div className="mt-2 space-y-1">
                    {valores.map((valor, indice) => (
                      <input
                        key={`${chave}-${indice}`}
                        className="w-full rounded-md border border-input bg-background px-2 py-1 text-sm"
                        value={valor}
                        placeholder={textos.parametroTemplate.replace(
                          "{indice}",
                          String(indice + 1),
                        )}
                        onChange={(evento) => {
                          const proximo = [...valores];
                          proximo[indice] = evento.target.value;
                          onParametros(chave, proximo);
                        }}
                      />
                    ))}
                  </div>
                )}
                <Button
                  type="button"
                  size="sm"
                  className="mt-2"
                  disabled={
                    enviando
                    || (template.quantidadeDeParametros > 0
                      && valores.some((valor) => valor.trim() === ""))
                  }
                  onClick={() => onEnviar(template, valores)}
                >
                  {textos.enviarTemplate}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
      <Link
        href="/templates-whatsapp"
        className="inline-flex text-xs font-medium text-primary underline-offset-4 hover:underline"
      >
        {textos.criarTemplate}
      </Link>
    </>
  );
}
