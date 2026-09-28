"use client";

import { useEffect, useRef, useState, type ComponentType } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  BookUser,
  Bot,
  Clock,
  FileText,
  Headset,
  Lock,
  ShieldCheck,
  Sparkles,
  Tag,
  TrendingUp,
  UserCog,
  Users,
  Zap,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useTextos } from "@/lib/config/textos-provider";
import type { CapacidadeDoCatalogo, LinhaDeCapacidade, MinhasPermissoes, Nivel, Papel } from "@/lib/gestao/types";
import { NIVEIS } from "@/lib/gestao/types";
import { ordem } from "@/lib/gestao/rascunho";
import { cn } from "@/lib/utils";

export type TextosGestao = ReturnType<typeof useTextos>["gestao"];

/** "{n} usuários" → "3 usuários". Textos sempre do catálogo; aqui só a interpolação. */
export function preencher(texto: string, valores: Record<string, string | number>): string {
  return Object.entries(valores).reduce((acc, [chave, valor]) => acc.replaceAll(`{${chave}}`, String(valor)), texto);
}

export const ICONE_DO_MODULO: Record<string, ComponentType<{ className?: string }>> = {
  atendimentos: Headset,
  contatos: BookUser,
  tags: Tag,
  mensagens_rapidas: Zap,
  templates: FileText,
  resumo_ia: Sparkles,
  dashboard: TrendingUp,
  mensagens_programadas: Clock,
  lembretes: Bell,
  automacao: Bot,
  equipe: Users,
};

export const ICONE_DO_PAPEL: Record<Papel, ComponentType<{ className?: string }>> = {
  GESTOR: ShieldCheck,
  ADMINISTRADOR: ShieldCheck,
  SUBGESTOR: UserCog,
  ATENDENTE: Headset,
};

export function rotuloDaCapacidade(t: TextosGestao, id: string): string {
  return t.capacidades[id] ?? id;
}

export function rotuloDoModulo(t: TextosGestao, id: string): { rotulo: string; descricao: string } {
  return t.modulos[id] ?? { rotulo: id, descricao: "" };
}

/** A ação está liberada para quem está logado? Decisão do backend, apenas lida aqui. */
export function pode(minhas: MinhasPermissoes | undefined, capacidade: string): boolean {
  return minhas?.capacidades[capacidade]?.permitido === true;
}

export function perfilFixoDoAtor(minhas: MinhasPermissoes | undefined): boolean {
  return minhas?.papel === "GESTOR" || minhas?.papel === "ADMINISTRADOR";
}

/**
 * Quem está logado pode virar este interruptor? Espelho de PoliticaDeConcessao.podeAlterar:
 * GESTOR/ADMINISTRADOR, sempre; SUBGESTOR delegado, só no conjunto delegável e só ligando o que
 * ele mesmo tem. Vale para exceções e para o perfil ATENDENTE; o backend revalida ao salvar.
 */
export function podeAlterarAcao(minhas: MinhasPermissoes, capacidade: CapacidadeDoCatalogo, paraLigado: boolean): boolean {
  if (perfilFixoDoAtor(minhas)) return true;
  return capacidade.delegavel && (!paraLigado || pode(minhas, capacidade.id));
}

/** Por que {@link podeAlterarAcao} recusou: fora do delegado, ou acima do que o ator tem. */
export function motivoForaDaAlcada(t: TextosGestao, capacidade: CapacidadeDoCatalogo): string {
  return capacidade.delegavel ? t.excecoes.semPermissaoPropria : t.excecoes.naoDelegavel;
}

/** Motivo legível de um bloqueio — nunca "ligado mas não funciona" sem explicação. */
export function motivoDoBloqueio(
  t: TextosGestao,
  linha: Pick<LinhaDeCapacidade, "motivo" | "alcance">,
  nivelMinimo: Nivel,
  dependencias: string[],
): string | null {
  switch (linha.motivo) {
    case "TETO_DO_PAPEL":
      return t.permissoes.motivos.TETO_DO_PAPEL;
    case "FLAG_DESLIGADA":
      return t.permissoes.motivos.FLAG_DESLIGADA;
    case "NIVEL_DO_MODULO":
      return preencher(t.permissoes.motivos.NIVEL_DO_MODULO, { nivel: t.permissoes.niveis[nivelMinimo] });
    case "DEPENDENCIA":
      return preencher(t.permissoes.motivos.DEPENDENCIA, {
        dependencias: dependencias.map((d) => rotuloDaCapacidade(t, d)).join(", "),
      });
    default:
      return null;
  }
}

/**
 * Nível do módulo como grupo de rádio segmentado (teclado: setas). Níveis fora do limite do papel
 * ficam desabilitados — não somem, para a régua continuar legível.
 */
export function SeletorDeNivel({
  valor,
  minimo,
  maximo,
  desabilitado,
  rotulo,
  onChange,
  textos,
}: {
  valor: Nivel;
  minimo: Nivel;
  maximo: Nivel;
  desabilitado?: boolean;
  rotulo: string;
  onChange: (nivel: Nivel) => void;
  textos: TextosGestao;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const habilitados = NIVEIS.filter((n) => ordem(n) >= ordem(minimo) && ordem(n) <= ordem(maximo));

  function mover(delta: number) {
    const atual = habilitados.indexOf(valor);
    const proximo = habilitados[Math.min(Math.max(atual + delta, 0), habilitados.length - 1)];
    if (proximo && proximo !== valor) {
      onChange(proximo);
      refs.current[NIVEIS.indexOf(proximo)]?.focus();
    }
  }

  return (
    <div
      role="radiogroup"
      aria-label={rotulo}
      aria-disabled={desabilitado || undefined}
      className="grid w-full grid-cols-4 gap-0.5 rounded-lg bg-muted p-0.5"
      onKeyDown={(e) => {
        if (desabilitado) return;
        if (e.key === "ArrowRight" || e.key === "ArrowDown") {
          e.preventDefault();
          mover(1);
        } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
          e.preventDefault();
          mover(-1);
        }
      }}
    >
      {NIVEIS.map((nivel, i) => {
        const ativo = nivel === valor;
        const indisponivel = desabilitado || !habilitados.includes(nivel);
        return (
          <button
            key={nivel}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={ativo}
            tabIndex={ativo ? 0 : -1}
            disabled={indisponivel}
            onClick={() => onChange(nivel)}
            className={cn(
              "min-w-0 truncate rounded-md px-1.5 py-1 text-[11px] font-semibold transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
              ativo ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:bg-background/70",
              indisponivel && !ativo && "cursor-not-allowed opacity-45 hover:bg-transparent",
              indisponivel && ativo && "opacity-80",
            )}
          >
            {textos.permissoes.niveis[nivel]}
          </button>
        );
      })}
    </div>
  );
}

/** Recorte estrutural (Meus/Todos): só exibido, nunca configurável. */
export function AlcanceFixo({ alcance, textos }: { alcance: "MEUS" | "TODOS"; textos: TextosGestao }) {
  return (
    <div className="inline-flex items-center gap-0.5 rounded-lg bg-muted p-0.5" aria-label={textos.permissoes.alcance[alcance]}>
      {(["MEUS", "TODOS"] as const).map((valor) => (
        <span
          key={valor}
          aria-hidden={valor !== alcance}
          className={cn(
            "rounded-md px-2 py-0.5 text-[11px] font-semibold",
            valor === alcance ? "bg-background text-primary shadow-sm" : "text-muted-foreground/60",
          )}
        >
          {textos.permissoes.alcance[valor]}
        </span>
      ))}
      <Lock className="mx-1 size-3 text-muted-foreground" aria-hidden />
    </div>
  );
}

export function SeloSensivel({ textos }: { textos: TextosGestao }) {
  return (
    <span className="shrink-0 rounded-sm bg-cor-atencao/15 px-1.5 py-px text-[9px] font-extrabold tracking-wide text-cor-atencao uppercase">
      {textos.permissoes.sensivel}
    </span>
  );
}

export function Legenda({ textos }: { textos: TextosGestao }) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
      <span className="inline-flex items-center gap-1.5">
        <span className="rounded-sm bg-muted px-1.5 py-px text-[9px] font-extrabold tracking-wide text-foreground uppercase">
          {textos.permissoes.niveis.EDITAR}
        </span>
        {textos.permissoes.legendaNivel}
      </span>
      <span className="inline-flex items-center gap-1.5">
        <SeloSensivel textos={textos} />
        {textos.permissoes.legendaSensivel}
      </span>
      <span className="inline-flex items-center gap-1.5">
        <Lock className="size-3" aria-hidden />
        {textos.permissoes.legendaBloqueado}
      </span>
    </div>
  );
}

/**
 * Protege rascunho: fechar/recarregar a aba (beforeunload) e clicar em link interno para outra
 * rota. A troca de contexto dentro da página (perfil, usuário, aba) usa {@link useConfirmacaoDeDescarte}.
 */
export function useProtecaoDeSaida(sujo: boolean, textos: TextosGestao) {
  const router = useRouter();
  const [destino, setDestino] = useState<string | null>(null);

  useEffect(() => {
    if (!sujo) return;
    const antesDeSair = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    const aoClicar = (e: MouseEvent) => {
      const link = (e.target as HTMLElement | null)?.closest?.("a[href]") as HTMLAnchorElement | null;
      if (!link || link.target === "_blank" || e.defaultPrevented) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin || url.pathname === window.location.pathname) return;
      e.preventDefault();
      e.stopPropagation();
      setDestino(url.pathname + url.search);
    };
    window.addEventListener("beforeunload", antesDeSair);
    document.addEventListener("click", aoClicar, true);
    return () => {
      window.removeEventListener("beforeunload", antesDeSair);
      document.removeEventListener("click", aoClicar, true);
    };
  }, [sujo]);

  const dialogo = destino ? (
    <ConfirmarDescarte
      textos={textos}
      onCancelar={() => setDestino(null)}
      onConfirmar={() => {
        const alvo = destino;
        setDestino(null);
        router.push(alvo);
      }}
    />
  ) : null;
  return dialogo;
}

/** Troca de contexto dentro da tela com rascunho sujo: pergunta antes de descartar. */
export function useConfirmacaoDeDescarte(sujo: boolean, textos: TextosGestao) {
  const [pendente, setPendente] = useState<(() => void) | null>(null);
  function executar(acao: () => void) {
    if (sujo) setPendente(() => acao);
    else acao();
  }
  const dialogo = pendente ? (
    <ConfirmarDescarte
      textos={textos}
      onCancelar={() => setPendente(null)}
      onConfirmar={() => {
        const acao = pendente;
        setPendente(null);
        acao();
      }}
    />
  ) : null;
  return { executar, dialogo };
}

function ConfirmarDescarte({
  textos,
  onCancelar,
  onConfirmar,
}: {
  textos: TextosGestao;
  onCancelar: () => void;
  onConfirmar: () => void;
}) {
  return (
    <Dialog open onOpenChange={(aberto) => !aberto && onCancelar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.barra.sairTitulo}</DialogTitle>
          <DialogDescription>{textos.barra.sairDescricao}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancelar}>
            {textos.barra.sairCancelar}
          </Button>
          <Button type="button" variant="destructive" onClick={onConfirmar}>
            {textos.barra.sairConfirmar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
