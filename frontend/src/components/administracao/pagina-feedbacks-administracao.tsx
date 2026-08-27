"use client";

import { useState } from "react";
import { Bug, Lightbulb } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { useTextos } from "@/lib/config/textos-provider";
import { useFeedbacksAdministrativos } from "@/lib/feedbacks/use-feedbacks";
import type { AreaFeedback, TipoFeedback } from "@/lib/feedbacks/types";

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
      <header>
        <h2 className="text-xl font-bold">{t.titulo}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t.descricao}</p>
      </header>

      <div className="inline-flex rounded-lg border bg-card p-1" role="group" aria-label={t.filtro}>
        {filtros.map((filtro) => (
          <Button
            key={filtro.valor ?? "TODOS"}
            type="button"
            size="sm"
            variant={tipo === filtro.valor ? "secondary" : "ghost"}
            aria-pressed={tipo === filtro.valor}
            onClick={() => setTipo(filtro.valor)}
          >
            {filtro.rotulo}
          </Button>
        ))}
      </div>

      {consulta.isLoading ? (
        <p className="text-sm text-muted-foreground">{t.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />
      ) : itens.length === 0 ? (
        <div className="rounded-xl border bg-card p-8 text-center text-sm text-muted-foreground">
          {t.vazio}
        </div>
      ) : (
        <div className="space-y-3">
          {itens.map((feedback) => {
            const sugestao = feedback.tipo === "SUGESTAO";
            const tipoTexto = sugestao ? textos.feedbacks.tipos.sugestao : textos.feedbacks.tipos.erro;
            const areaTexto = textos.feedbacks.areas[AREA_PARA_TEXTO[feedback.areaChave]];
            return (
              <article key={feedback.id} className="rounded-xl border bg-card p-5 shadow-sm">
                <div className="flex items-start gap-3">
                  <AvatarIniciais
                    id={feedback.autorId}
                    nome={feedback.autorNome}
                    fotoUrl={feedback.autorFotoUrl}
                    className="flex size-10 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
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
                        {t.tipoArea.replace("{tipo}", tipoTexto).replace("{area}", areaTexto)}
                      </PillDeStatus>
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm text-foreground">
                      {feedback.descricao}
                    </p>
                    <p className="mt-3 text-xs text-muted-foreground">
                      {t.data.replace(
                        "{data}",
                        new Intl.DateTimeFormat("pt-BR", {
                          dateStyle: "short",
                          timeStyle: "short",
                        }).format(new Date(feedback.criadoEm)),
                      )}
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
