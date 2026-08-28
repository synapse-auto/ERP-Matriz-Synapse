"use client";

import { useState } from "react";
import { Bug, Clock, Lightbulb } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { useTextos } from "@/lib/config/textos-provider";
import { useFeedbacksAdministrativos } from "@/lib/feedbacks/use-feedbacks";
import type { AreaFeedback, TipoFeedback } from "@/lib/feedbacks/types";
import { cn } from "@/lib/utils";

const AREA_PARA_TEXTO: Record<
  AreaFeedback,
  keyof ReturnType<typeof useTextos>["feedbacks"]["areas"]
> = {
  GERAL: "geral",
  ATENDIMENTOS: "atendimentos",
  AGENDA: "agenda",
  DASHBOARD: "dashboard",
  EQUIPE: "equipe",
  AUTOMACAO: "automacao",
  MENSAGENS_PROGRAMADAS: "mensagensProgramadas",
  LEMBRETES: "lembretes",
  TAGS: "tags",
  CONFIGURACOES: "configuracoes",
};

export function PaginaFeedbacksAdministracao() {
  const textos = useTextos();
  const t = textos.administracao.feedbacks;
  const [tipo, setTipo] = useState<TipoFeedback | null>(null);
  const consulta = useFeedbacksAdministrativos(tipo);
  const itens = consulta.data?.pages.flatMap((pagina) => pagina.itens) ?? [];
  const filtros: Array<{ valor: TipoFeedback | null; rotulo: string }> = [
    { valor: null, rotulo: t.todos },
    { valor: "SUGESTAO", rotulo: t.sugestoes },
    { valor: "ERRO", rotulo: t.erros },
  ];

  return (
    <section className="space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-extrabold tracking-tight">{t.titulo}</h2>
          <p className="mt-1 text-[13px] text-muted-foreground">{t.descricao}</p>
        </div>
        <div className="inline-flex rounded-xl bg-muted p-1" role="group" aria-label={t.filtro}>
          {filtros.map((filtro) => (
            <Button
              key={filtro.valor ?? "TODOS"}
              type="button"
              size="sm"
              variant="ghost"
              aria-pressed={tipo === filtro.valor}
              className={cn(
                "rounded-lg px-4",
                tipo === filtro.valor && "bg-card text-cor-ia shadow-sm hover:bg-card hover:text-cor-ia",
              )}
              onClick={() => setTipo(filtro.valor)}
            >
              {filtro.rotulo}
            </Button>
          ))}
        </div>
      </header>

      {consulta.isLoading ? (
        <p className="text-sm text-muted-foreground">{t.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />
      ) : itens.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted-foreground shadow-sm">
          {t.vazio}
        </div>
      ) : (
        <div className="space-y-3.5">
          {itens.map((feedback) => {
            const sugestao = feedback.tipo === "SUGESTAO";
            const tipoTexto = sugestao ? textos.feedbacks.tipos.sugestao : textos.feedbacks.tipos.erro;
            const areaTexto = textos.feedbacks.areas[AREA_PARA_TEXTO[feedback.areaChave]];
            return (
              <article
                key={feedback.id}
                className="rounded-xl border border-border bg-card p-5 shadow-sm"
              >
                <div className="flex items-start gap-4">
                  <AvatarIniciais
                    id={feedback.autorId}
                    nome={feedback.autorNome}
                    fotoUrl={feedback.autorFotoUrl}
                    className="flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2.5">
                      <div className="min-w-0">
                        <p className="font-bold text-foreground">{feedback.autorNome}</p>
                        <p className="text-xs text-muted-foreground">
                          {t.autorPapel.replace("{papel}", feedback.autorPapel)}
                        </p>
                      </div>
                      <PillDeStatus
                        tom={sugestao ? "info" : "erro"}
                        icone={
                          sugestao ? (
                            <Lightbulb className="size-3" aria-hidden />
                          ) : (
                            <Bug className="size-3" aria-hidden />
                          )
                        }
                      >
                        {tipoTexto}
                      </PillDeStatus>
                      <PillDeStatus tom="neutro">{areaTexto}</PillDeStatus>
                      <p className="ml-auto flex items-center gap-1 text-xs font-semibold text-muted-foreground">
                        <Clock className="size-3.5" aria-hidden />
                        {t.data.replace(
                          "{data}",
                          new Intl.DateTimeFormat("pt-BR", {
                            dateStyle: "short",
                            timeStyle: "short",
                          }).format(new Date(feedback.criadoEm)),
                        )}
                      </p>
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-foreground">
                      {feedback.descricao}
                    </p>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {consulta.hasNextPage && (
        <Button
          type="button"
          variant="outline"
          disabled={consulta.isFetchingNextPage}
          onClick={() => consulta.fetchNextPage()}
        >
          {consulta.isFetchingNextPage ? t.carregandoMais : t.carregarMais}
        </Button>
      )}
    </section>
  );
}
