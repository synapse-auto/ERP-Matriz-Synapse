"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import { useAlterarLimite, useEhAdministradorDeCampanhas } from "@/lib/campanhas/hooks";
import type { DetalheDaCampanha } from "@/lib/campanhas/types";
import { validarLimiteDiario } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { CampoDeLimite } from "./campo-de-limite";

/** Ajuste do limite diário com o efeito explicado: vale no próximo ciclo e nunca passa do teto da instância. */
export function AjusteDeLimite({ detalhe }: { detalhe: DetalheDaCampanha }) {
  const textos = useTextos().campanhas.detalhe.limite;
  const ehAdministrador = useEhAdministradorDeCampanhas();
  const { campanha } = detalhe;
  const alterar = useAlterarLimite(campanha.id);
  const [valor, setValor] = useState<number | null>(campanha.limiteDiario);
  const teto = detalhe.tetoDaInstancia;
  const mudou = valor !== campanha.limiteDiario;
  const podeSalvar = ehAdministrador && mudou && validarLimiteDiario(valor, teto) === "ok" && !alterar.isPending;

  return (
    <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm" aria-labelledby="ajuste-limite">
      <div>
        <h2 id="ajuste-limite" className="text-sm font-bold">
          {textos.titulo}
        </h2>
        <p className="text-sm tabular-nums">{interpolarCatalogo(textos.hoje, { limite: formatarNumero(detalhe.limiteEfetivoHoje) })}</p>
        <p className="text-xs text-muted-foreground">
          {interpolarCatalogo(textos.instanciaHoje, {
            enviadas: formatarNumero(detalhe.enfileiradasHojeNaInstancia),
            teto: formatarNumero(teto),
          })}
        </p>
      </div>
      <CampoDeLimite
        id="ajuste-limite-valor"
        rotulo={textos.campo}
        valor={valor}
        teto={teto}
        limiteMeta={detalhe.limiteMetaInformado}
        aoMudar={setValor}
        textoDeAjuda={interpolarCatalogo(textos.efeito, { teto: formatarNumero(teto) })}
      />
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          disabled={!podeSalvar}
          onClick={() => alterar.mutate({ limiteDiario: valor as number, ritmoPorMinuto: null })}
        >
          {alterar.isPending ? textos.salvando : textos.salvar}
        </Button>
        {!ehAdministrador && <p className="text-xs text-muted-foreground">{textos.somenteAdministrador}</p>}
        {alterar.isSuccess && !mudou && (
          <p role="status" className="text-xs text-cor-sucesso">
            {textos.salvo}
          </p>
        )}
        {alterar.isError && (
          <p role="alert" className="text-xs text-destructive">
            {alterar.error instanceof Error && alterar.error.message ? alterar.error.message : textos.erro}
          </p>
        )}
      </div>
    </section>
  );
}
