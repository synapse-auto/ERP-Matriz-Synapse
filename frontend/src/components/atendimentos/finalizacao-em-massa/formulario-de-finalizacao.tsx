"use client";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SeletorData } from "@/components/ui/seletor-data";
import { SeletorMultiplo } from "@/components/ui/seletor-multiplo";
import { Skeleton } from "@/components/ui/skeleton";
import { usePreviaDeFinalizacao } from "@/lib/finalizacao-em-massa/hooks";
import type { FiltroDeFinalizacao } from "@/lib/finalizacao-em-massa/types";
import { problemaDoFiltro } from "@/lib/finalizacao-em-massa/validacao";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { useTextos } from "@/lib/config/textos-provider";

export interface OpcaoDeAtendenteDaFinalizacao {
  id: string;
  nome: string;
}

interface Props {
  filtro: FiltroDeFinalizacao;
  onFiltroAlterado: (filtro: FiltroDeFinalizacao) => void;
  atendentes: OpcaoDeAtendenteDaFinalizacao[];
  carregandoAtendentes: boolean;
  erroDeAtendentes: boolean;
  onRecarregarAtendentes: () => void;
  onRevisar: (total: number) => void;
  onFechar: () => void;
}

/** Filtros + previa. A previa e do servidor (mesma regra que a execucao usa); aqui so se mostra e se bloqueia. */
export function FormularioDeFinalizacao({
  filtro,
  onFiltroAlterado,
  atendentes,
  carregandoAtendentes,
  erroDeAtendentes,
  onRecarregarAtendentes,
  onRevisar,
  onFechar,
}: Props) {
  const textos = useTextos().atendimentos.finalizacaoEmMassa;
  const problema = problemaDoFiltro(filtro);
  const previa = usePreviaDeFinalizacao(filtro, true);
  const dados = previa.data;
  const podeRevisar = problema === null && !!dados && dados.total > 0 && !dados.excedeLimite;

  function alterar(parcial: Partial<FiltroDeFinalizacao>) {
    onFiltroAlterado({ ...filtro, ...parcial });
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-1.5">
        <Label>{textos.atendentes.rotulo}</Label>
        {carregandoAtendentes ? (
          <Skeleton className="h-10 w-full" />
        ) : erroDeAtendentes ? (
          <ErroDeCarregamento mensagem={textos.atendentes.erro} onTentarNovamente={onRecarregarAtendentes} />
        ) : atendentes.length === 0 ? (
          <p className="text-sm text-muted-foreground">{textos.atendentes.indisponivel}</p>
        ) : (
          <SeletorMultiplo
            ariaLabel={textos.atendentes.rotulo}
            placeholder={textos.atendentes.placeholder}
            valores={filtro.atendenteIds}
            opcoes={atendentes.map((atendente) => ({ valor: atendente.id, rotulo: atendente.nome }))}
            onChange={(atendenteIds) => alterar({ atendenteIds })}
          />
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="finalizacao-de">{textos.periodo.de}</Label>
          <SeletorData
            id="finalizacao-de"
            valor={filtro.de}
            placeholder={textos.periodo.selecionarData}
            onChange={(de) => alterar({ de })}
            obrigatorio
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="finalizacao-ate">{textos.periodo.ate}</Label>
          <SeletorData
            id="finalizacao-ate"
            valor={filtro.ate}
            placeholder={textos.periodo.selecionarData}
            onChange={(ate) => alterar({ ate })}
            obrigatorio
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="finalizacao-hora-inicio">{textos.periodo.horaInicio}</Label>
          <Input
            id="finalizacao-hora-inicio"
            type="time"
            value={filtro.horaInicio ?? ""}
            onChange={(evento) => alterar({ horaInicio: evento.target.value || null })}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="finalizacao-hora-fim">{textos.periodo.horaFim}</Label>
          <Input
            id="finalizacao-hora-fim"
            type="time"
            value={filtro.horaFim ?? ""}
            onChange={(evento) => alterar({ horaFim: evento.target.value || null })}
          />
        </div>
      </div>
      <p className="text-xs text-muted-foreground">{textos.periodo.dica}</p>

      <section aria-live="polite" className="grid gap-2 rounded-lg border border-border bg-muted/40 p-3">
        {problema ? (
          <p className="text-sm text-muted-foreground">{textos.problemas[problema]}</p>
        ) : previa.isLoading ? (
          <Skeleton className="h-10 w-full" />
        ) : previa.isError ? (
          <ErroDeCarregamento
            mensagem={previa.error instanceof Error ? previa.error.message : textos.previa.erro}
            onTentarNovamente={() => previa.refetch()}
          />
        ) : dados ? (
          <>
            <p className="text-sm font-medium text-foreground">
              {dados.total === 0
                ? textos.previa.nenhum
                : interpolarCatalogo(textos.previa.total, { total: String(dados.total) })}
            </p>
            {dados.excedeLimite && (
              <p role="alert" className="text-sm text-destructive">
                {interpolarCatalogo(textos.previa.excedeLimite, { limite: String(dados.limite) })}
              </p>
            )}
            <ul aria-label={textos.previa.porAtendente} className="grid gap-1 text-sm">
              {dados.porAtendente.map((item) => (
                <li key={item.atendenteId} className="flex items-center justify-between gap-2">
                  <span className="truncate">{item.nome}</span>
                  <span className="text-muted-foreground">{item.quantidade}</span>
                </li>
              ))}
            </ul>
            <p className="text-xs text-muted-foreground">
              {interpolarCatalogo(textos.previa.fuso, { fuso: dados.fuso })}
            </p>
          </>
        ) : null}
      </section>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onFechar}>
          {textos.fechar}
        </Button>
        <Button type="button" disabled={!podeRevisar} onClick={() => dados && onRevisar(dados.total)}>
          {textos.confirmacao.revisar}
        </Button>
      </div>
    </div>
  );
}
