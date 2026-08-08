"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAtualizarParametroAutomacao,
  useConfiguracaoAutomacao,
} from "@/lib/automacao/use-automacao";
import type { ParametroAutomacao } from "@/lib/automacao/types";

export function PaginaAutomacao() {
  const t = useTextos().automacao;
  const parametros = useConfiguracaoAutomacao();

  return (
    <div className="space-y-5 p-6">
      <header>
        <h1 className="text-xl font-semibold">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descricao}</p>
      </header>

      {parametros.isLoading ? (
        <p>{t.carregando}</p>
      ) : parametros.isError ? (
        <p role="alert" className="text-destructive">
          {t.erro}
        </p>
      ) : !parametros.data?.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="space-y-3">
          {parametros.data.map((parametro) => (
            <LinhaParametro key={parametro.chave} parametro={parametro} />
          ))}
        </div>
      )}
    </div>
  );
}

function LinhaParametro({ parametro }: { parametro: ParametroAutomacao }) {
  const t = useTextos().automacao;
  const atualizar = useAtualizarParametroAutomacao();
  const [valor, setValor] = useState(parametro.valor);

  const alterado = valor !== parametro.valor;
  const foraDaFaixa = valorForaDaFaixa(parametro, valor);
  const podeSalvar = alterado && !foraDaFaixa && !atualizar.isPending;
  const temFaixa = parametro.valorMin != null || parametro.valorMax != null;

  function salvar() {
    atualizar.mutate({ chave: parametro.chave, valor });
  }

  return (
    <div className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium">{parametro.descricao ?? parametro.chave}</p>
          <p className="font-mono text-xs text-muted-foreground">{parametro.chave}</p>
        </div>
        {parametro.tipo !== "BOOLEAN" && temFaixa && (
          <p className="shrink-0 text-xs text-muted-foreground">
            {t.faixaLabel}: {parametro.valorMin ?? "—"}–{parametro.valorMax ?? "—"}
            {parametro.unidade ? ` ${parametro.unidade}` : ""}
          </p>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <CampoValor parametro={parametro} valor={valor} onChange={setValor} />
        {foraDaFaixa && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroFaixa}
          </p>
        )}
        {atualizar.isError && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroSalvar}
          </p>
        )}
        <Button size="sm" disabled={!podeSalvar} onClick={salvar} className="ml-auto">
          {atualizar.isPending ? t.salvando : t.salvar}
        </Button>
      </div>
    </div>
  );
}

function CampoValor({
  parametro,
  valor,
  onChange,
}: {
  parametro: ParametroAutomacao;
  valor: string;
  onChange: (valor: string) => void;
}) {
  const t = useTextos().automacao;

  if (parametro.tipo === "BOOLEAN") {
    return (
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={valor === "true"}
          onChange={(e) => onChange(e.target.checked ? "true" : "false")}
        />
        {valor === "true" ? t.ativado : t.desativado}
      </label>
    );
  }

  if (parametro.tipo === "TEXT") {
    return (
      <Textarea
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        rows={2}
        className="min-w-64 flex-1"
      />
    );
  }

  return (
    <Input
      type="number"
      inputMode="decimal"
      step={parametro.tipo === "DECIMAL" ? "any" : "1"}
      min={parametro.valorMin ?? undefined}
      max={parametro.valorMax ?? undefined}
      value={valor}
      onChange={(e) => onChange(e.target.value)}
      className="w-32"
    />
  );
}

function valorForaDaFaixa(parametro: ParametroAutomacao, valor: string): boolean {
  if (parametro.tipo !== "INT" && parametro.tipo !== "DECIMAL") return false;
  const numero = Number(valor);
  if (Number.isNaN(numero)) return true;
  if (parametro.valorMin != null && numero < parametro.valorMin) return true;
  if (parametro.valorMax != null && numero > parametro.valorMax) return true;
  return false;
}
