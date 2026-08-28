"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  BarChart3,
  BookUser,
  Bot,
  CalendarClock,
  Check,
  Clock,
  Folder,
  Headset,
  KeyRound,
  LogOut,
  Megaphone,
  Menu,
  MessageSquarePlus,
  MessageSquareText,
  FileText,
  Settings,
  Sparkles,
  ShieldCheck,
  Tag,
  TrendingUp,
  Users,
} from "lucide-react";

import { apiFetch } from "@/lib/api/http-client";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useContagemDeAtendimentos } from "@/lib/atendimento/use-atendimentos";
import { atualizarPresenca } from "@/lib/equipe/api";
import type { StatusPresenca } from "@/lib/equipe/types";
import { useMeuUsuario } from "@/lib/equipe/use-equipe";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import {
  ITENS_GESTAO,
  ITENS_MENU,
  itemEstaNaAbaInferior,
  type ItemDeMenuBase,
} from "@/lib/navegacao/itens-do-menu";
import { itemDeMenuVisivel } from "@/lib/navegacao/visibilidade-do-menu";
import { cn } from "@/lib/utils";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { NovidadesDialog } from "./novidades-dialog";

const ICONES: Record<string, React.ComponentType<{ className?: string }>> = {
  atendimentos: Headset,
  dashboard: TrendingUp,
  agenda: BookUser,
  tags: Tag,
  mensagensRapidas: MessageSquareText,
  templatesWhatsApp: FileText,
  bancoArquivos: Folder,
  mensagensProgramadas: Clock,
  lembretes: Bell,
  feedbacks: MessageSquarePlus,
  equipe: Users,
  campanhas: Megaphone,
  automacao: Bot,
  horarios: CalendarClock,
  relatorios: BarChart3,
  administracao: ShieldCheck,
};

const OPCOES_PRESENCA: StatusPresenca[] = ["ONLINE", "AUSENTE", "OFFLINE"];

const COR_PRESENCA: Record<StatusPresenca, string> = {
  ONLINE: "var(--cor-sucesso)",
  AUSENTE: "var(--cor-atencao)",
  OFFLINE: "var(--texto-fraco)",
};

async function encerrarSessao() {
  await fetch("/api/auth/logout", { method: "POST" }).catch(() => null);
  useAuthStore.getState().limparSessao();
  window.location.href = "/login";
}

function useFeaturesHabilitadas() {
  return useQuery({
    queryKey: ["config", "features"],
    queryFn: () => apiFetch<string[]>("/api/v1/config/features"),
  });
}

export function NavegacaoInferior() {
  const textos = useTextos();
  const pathname = usePathname();
  const papel = useAuthStore((estado) => estado.papel);
  const { data: flags } = useFeaturesHabilitadas();
  const { data: contagens } = useContagemDeAtendimentos();
  const meuUsuario = useMeuUsuario();
  const cache = useQueryClient();
  const [maisAberto, setMaisAberto] = useState(false);
  const [novidadesAberto, setNovidadesAberto] = useState(false);
  const mudarPresenca = useMutation({
    mutationFn: atualizarPresenca,
    onSuccess: (dados) =>
      cache.setQueryData(["me"], (atual: typeof meuUsuario.data) =>
        atual ? { ...atual, presenca: dados.status } : atual,
      ),
  });

  function visivel(item: ItemDeMenuBase): boolean {
    return itemDeMenuVisivel(item.chave, papel, flags, item.flag);
  }

  const abas = ITENS_MENU.filter(
    (item) => itemEstaNaAbaInferior(item.chave) && visivel(item),
  );
  const extras = [...ITENS_MENU, ...ITENS_GESTAO].filter(
    (item) => visivel(item) && !itemEstaNaAbaInferior(item.chave),
  );
  const maisAtivo = extras.some((item) => pathname.startsWith(item.rota))
    || pathname.startsWith("/configuracoes")
    || pathname.startsWith("/trocar-senha");
  const statusAtual = meuUsuario.data?.presenca ?? "OFFLINE";
  const rotuloDaPresenca = (status: StatusPresenca) =>
    ({
      ONLINE: textos.rodape.presenca.online,
      AUSENTE: textos.rodape.presenca.ausente,
      OFFLINE: textos.rodape.presenca.offline,
    })[status];

  return (
    <>
      <nav
        data-testid="navegacao-inferior"
        aria-label={textos.menu.grupoMenu}
        className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background pb-[env(safe-area-inset-bottom,0px)] shadow-[0_-8px_24px_-22px_rgb(15_23_42/0.45)]"
      >
        <ul className="flex items-stretch">
          {abas.map((item) => {
            const Icone = ICONES[item.chave];
            const ativo = pathname.startsWith(item.rota);
            const rotulo = textos.menu.itens[item.chave] ?? item.chave;
            return (
              <li key={item.chave} className="min-w-0 flex-1">
                <Link
                  href={item.rota}
                  aria-current={ativo ? "page" : undefined}
                  className={cn(
                    "flex flex-col items-center gap-0.5 px-1 py-2 text-[11px] font-medium",
                    ativo ? "text-primary" : "text-muted-foreground",
                  )}
                >
                  <span className="relative">
                    {Icone && <Icone className="size-6" aria-hidden />}
                    {item.chave === "atendimentos" && contagens?.PENDENTES !== undefined && (
                      <Badge className="absolute -right-2.5 -top-1 h-4 min-w-4 rounded-full px-1 text-[0.55rem]">
                        {contagens.PENDENTES}
                      </Badge>
                    )}
                  </span>
                  {rotulo}
                </Link>
              </li>
            );
          })}
          <li className="min-w-0 flex-1">
            <button
              type="button"
              aria-expanded={maisAberto}
              aria-label={textos.menu.mais}
              onClick={() => setMaisAberto(true)}
              className={cn(
                "flex w-full flex-col items-center gap-0.5 px-1 py-2 text-[11px] font-medium",
                maisAtivo ? "text-primary" : "text-muted-foreground",
              )}
            >
              <Menu className="size-6" aria-hidden />
              {textos.menu.mais}
            </button>
          </li>
        </ul>
      </nav>

      <Dialog open={maisAberto} onOpenChange={setMaisAberto}>
        <DialogContent
          showCloseButton
          className="top-auto bottom-0 left-0 max-h-[85dvh] w-full max-w-none translate-x-0 translate-y-0 overflow-y-auto rounded-t-2xl rounded-b-none sm:max-w-none"
        >
          <DialogHeader>
            <DialogTitle>{textos.menu.maisTitulo}</DialogTitle>
          </DialogHeader>
          <div className="flex items-center gap-3 rounded-xl bg-muted/60 px-3 py-2.5">
            {meuUsuario.data ? (
              <AvatarIniciais
                id={meuUsuario.data.id ?? "me"}
                nome={meuUsuario.data.nome}
                fotoUrl={meuUsuario.data.fotoUrl}
                className="flex size-10 items-center justify-center rounded-xl text-sm font-bold text-white"
              />
            ) : null}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold text-foreground">
                {meuUsuario.data?.nome}
              </p>
              <p className="truncate text-xs text-muted-foreground">
                {meuUsuario.data?.papel ?? papel} · {rotuloDaPresenca(statusAtual)}
              </p>
            </div>
          </div>
          <p className="text-[10px] font-bold tracking-[.12em] text-muted-foreground uppercase">
            {textos.rodape.presenca.rotulo}
          </p>
          <div className="flex gap-1">
            {OPCOES_PRESENCA.map((status) => (
              <button
                key={status}
                type="button"
                onClick={() => mudarPresenca.mutate(status)}
                className={cn(
                  "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-2 py-2 text-xs font-semibold",
                  statusAtual === status ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground",
                )}
              >
                <span
                  className="size-2 rounded-full"
                  style={{ backgroundColor: COR_PRESENCA[status] }}
                />
                {rotuloDaPresenca(status)}
                {statusAtual === status && <Check className="size-3.5" aria-hidden />}
              </button>
            ))}
          </div>
          <ul className="grid gap-0.5">
            {extras.map((item) => {
              const Icone = ICONES[item.chave];
              const rotulo = textos.menu.itens[item.chave] ?? item.chave;
              const ativo = pathname.startsWith(item.rota);
              return (
                <li key={item.chave}>
                  <Link
                    href={item.rota}
                    onClick={() => setMaisAberto(false)}
                    className={cn(
                      "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium",
                      ativo ? "bg-primary/10 text-primary" : "text-foreground hover:bg-muted",
                    )}
                  >
                    {Icone && <Icone className="size-5 shrink-0" aria-hidden />}
                    {rotulo}
                  </Link>
                </li>
              );
            })}
            {textos.novidades?.titulo && (
              <li>
                <button
                  type="button"
                  onClick={() => {
                    setMaisAberto(false);
                    setNovidadesAberto(true);
                  }}
                  className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-foreground hover:bg-muted"
                >
                  <Sparkles className="size-5 shrink-0" aria-hidden />
                  {textos.novidades.titulo}
                </button>
              </li>
            )}
            <li>
              <Link
                href="/configuracoes"
                onClick={() => setMaisAberto(false)}
                className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-foreground hover:bg-muted"
              >
                <Settings className="size-5 shrink-0" aria-hidden />
                {textos.configuracoes.abrir}
              </Link>
            </li>
            <li>
              <Link
                href="/trocar-senha"
                onClick={() => setMaisAberto(false)}
                className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-foreground hover:bg-muted"
              >
                <KeyRound className="size-5 shrink-0" aria-hidden />
                {textos.rodape.trocarSenha}
              </Link>
            </li>
            <li>
              <button
                type="button"
                onClick={() => void encerrarSessao()}
                className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-destructive hover:bg-destructive/10"
              >
                <LogOut className="size-5 shrink-0" aria-hidden />
                {textos.rodape.sair}
              </button>
            </li>
          </ul>
        </DialogContent>
      </Dialog>
      <NovidadesDialog aberto={novidadesAberto} onFechar={() => setNovidadesAberto(false)} />
    </>
  );
}
