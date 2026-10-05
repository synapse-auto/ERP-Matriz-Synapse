"use client";

import { CheckCheck, X } from "lucide-react";

import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { useTextos } from "@/lib/config/textos-provider";
import { listaDeNomes, type AvisoDeFinalizacaoEmMassa } from "@/lib/finalizacao-em-massa/aviso";

interface Props {
  aviso: AvisoDeFinalizacaoEmMassa;
  onFechar: () => void;
}

/**
 * Cartao do aviso de conclusao: o total, quem foi afetado e, se houve, o parcial (ignorados e falhas). Quem recebe
 * ja foi filtrado pelo servidor; aqui so se apresenta o que veio.
 */
export function AvisoDeFinalizacaoEmMassaCard({ aviso, onFechar }: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.finalizacaoEmMassa.aviso;
  const { totalFinalizados, afetados, ignorados, falhas, parcial } = aviso.dados;
  const modelo = totalFinalizados === 1 ? textos.resumoUm : textos.resumoVarios;

  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-auto relative overflow-hidden rounded-xl border border-border bg-background p-2.5 shadow-lg"
    >
      <button
        type="button"
        className="absolute right-2 top-2 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={catalogo.notificacoes.fechar}
        title={catalogo.notificacoes.fechar}
        onClick={onFechar}
      >
        <X className="size-(--tamanho-icone-interface)" aria-hidden />
      </button>
      <div className="flex items-start gap-3 pr-5">
        <span
          className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
          aria-hidden
        >
          <CheckCheck className="size-(--tamanho-icone-interface)" />
        </span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-foreground">{textos.titulo}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {interpolarCatalogo(modelo, {
              total: String(totalFinalizados),
              usuarios: listaDeNomes(afetados.map((afetado) => afetado.nome)),
            })}
          </p>
          <ul className="mt-1 grid gap-0.5 text-xs text-foreground">
            {afetados.map((afetado) => (
              <li key={afetado.nome}>
                {interpolarCatalogo(textos.linha, { nome: afetado.nome, finalizados: String(afetado.finalizados) })}
              </li>
            ))}
          </ul>
          {parcial && (
            <p className="mt-1 text-xs text-muted-foreground">
              {interpolarCatalogo(textos.parcial, { ignorados: String(ignorados), falhas: String(falhas) })}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
