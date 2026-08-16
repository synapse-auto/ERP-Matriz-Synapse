"use client";

import { useState } from "react";

import { Check, Plus, Tag, X } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useDesvincularTag,
  useTagsDoLead,
  useTodasAsTags,
  useVincularTag,
} from "@/lib/lead/use-painel-lead";

export function AtalhoTags({
  leadId,
  modo = "cabecalho",
}: {
  leadId: string;
  modo?: "cabecalho" | "painel";
}) {
  const textos = useTextos().painelLead.tags;
  const [erro, setErro] = useState(false);
  const tags = useTagsDoLead(leadId);
  const todas = useTodasAsTags();
  const vincular = useVincularTag(leadId);
  const desvincular = useDesvincularTag(leadId);
  const vinculadas = new Set(tags.data?.map((tag) => tag.id));

  return (
    <div
      className={
        modo === "painel"
          ? "flex flex-wrap items-center gap-1.5"
          : "flex min-w-0 items-center gap-1"
      }
    >
      <div
        className={
          modo === "painel"
            ? "flex flex-wrap gap-1.5"
            : "hidden max-w-48 gap-1 overflow-hidden xl:flex"
        }
      >
        {tags.data?.map((tag) => (
          <Badge
            key={tag.id}
            variant="outline"
            style={{ borderColor: tag.cor, color: tag.cor }}
            className="max-w-24 truncate"
          >
            {tag.nome}
          </Badge>
        ))}
      </div>
      <Popover>
        <PopoverTrigger
          className={buttonVariants({
            variant: modo === "painel" ? "outline" : "ghost",
            size: modo === "painel" ? "sm" : "icon",
          })}
          aria-label={textos.titulo}
        >
          {modo === "painel" ? (
            <>
              <Plus className="size-3.5" />
              {textos.botao}
            </>
          ) : (
            <Tag className="size-4" />
          )}
        </PopoverTrigger>
        <PopoverContent align="end" className="w-72 space-y-2">
          <p className="text-sm font-medium text-foreground">{textos.titulo}</p>
          {erro && (
            <p className="text-xs text-destructive">{textos.erroReversao}</p>
          )}
          <div className="max-h-64 space-y-1 overflow-y-auto">
            {todas.data?.map((tag) => {
              const ativa = vinculadas.has(tag.id);
              return (
                <Button
                  key={tag.id}
                  type="button"
                  variant="ghost"
                  className="w-full justify-start gap-2"
                  aria-label={
                    ativa
                      ? textos.remover.replace("{nome}", tag.nome)
                      : `${textos.adicionar} ${tag.nome}`
                  }
                  onClick={() => {
                    setErro(false);
                    const mutacao = ativa ? desvincular : vincular;
                    mutacao.mutate({ tag }, { onError: () => setErro(true) });
                  }}
                >
                  {ativa ? (
                    <Check className="size-4" />
                  ) : (
                    <Plus className="size-4" />
                  )}
                  <span
                    className="size-2.5 rounded-full border"
                    style={{ borderColor: tag.cor, backgroundColor: tag.cor }}
                  />
                  <span className="flex-1 truncate text-left">{tag.nome}</span>
                  {ativa && <X className="size-3.5" />}
                </Button>
              );
            })}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}
