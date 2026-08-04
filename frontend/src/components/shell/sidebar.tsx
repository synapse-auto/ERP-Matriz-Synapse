"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  Bell,
  BarChart3,
  CalendarClock,
  CalendarDays,
  Clock,
  Folder,
  LayoutDashboard,
  LogOut,
  Megaphone,
  Tag,
  UserCog,
  Users,
  Workflow,
  Zap,
} from "lucide-react";

import { apiFetch } from "@/lib/api/http-client";
import { atualizarPresenca, obterPresenca } from "@/lib/equipe/api";
import type { StatusPresenca } from "@/lib/equipe/types";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

interface ItemDeMenu {
  chave: string;
  rota: string;
  icone: React.ComponentType<{ className?: string }>;
  /** Ausente = feature central, sempre visível. Presente = só some se a flag não vier habilitada. */
  flag?: string;
}

const ITENS_MENU: ItemDeMenu[] = [
  { chave: "atendimentos", rota: "/atendimentos", icone: Users },
  { chave: "dashboard", rota: "/dashboard", icone: LayoutDashboard, flag: "dashboard" },
  { chave: "agenda", rota: "/agenda", icone: CalendarDays },
  { chave: "tags", rota: "/tags", icone: Tag },
  { chave: "mensagensRapidas", rota: "/mensagens-rapidas", icone: Zap },
  { chave: "bancoArquivos", rota: "/banco-arquivos", icone: Folder, flag: "banco_arquivos" },
  { chave: "mensagensProgramadas", rota: "/mensagens-programadas", icone: Clock },
  { chave: "lembretes", rota: "/lembretes", icone: Bell },
];

const ITENS_GESTAO: ItemDeMenu[] = [
  { chave: "equipe", rota: "/equipe", icone: UserCog },
  { chave: "campanhas", rota: "/campanhas", icone: Megaphone, flag: "campanhas" },
  { chave: "automacao", rota: "/automacao", icone: Workflow },
  { chave: "horarios", rota: "/horarios", icone: CalendarClock },
  { chave: "relatorios", rota: "/relatorios", icone: BarChart3, flag: "relatorios" },
];

/**
 * Único consumidor — inline por convenção (hooks.md: não extrair hook de um caller só).
 * `GET /api/v1/config/features` só devolve as chaves HABILITADAS (FeatureService.habilitadas):
 * uma flag desligada nunca aparece na resposta, então "item.flag está na lista" já é a checagem
 * completa — não existe um `{flag: false}` para filtrar.
 */
function useFeaturesHabilitadas() {
  return useQuery({
    queryKey: ["config", "features"],
    queryFn: () => apiFetch<string[]>("/api/v1/config/features"),
  });
}

async function encerrarSessao() {
  await fetch("/api/auth/logout", { method: "POST" }).catch(() => null);
  useAuthStore.getState().limparSessao();
  window.location.href = "/login";
}

export function Sidebar() {
  const textos = useTextos();
  const pathname = usePathname();
  const { data: flags, isLoading, isError } = useFeaturesHabilitadas();
  const papel = useAuthStore((estado) => estado.papel);
  const email = useAuthStore((estado) => estado.email);
  const cache = useQueryClient();
  const presenca = useQuery({ queryKey: ["minha-presenca"], queryFn: obterPresenca });
  const mudarPresenca = useMutation({ mutationFn: atualizarPresenca, onSuccess: (dados) => cache.setQueryData(["minha-presenca"], dados) });

  function itemVisivel(item: ItemDeMenu): boolean {
    if (item.chave === "equipe" && papel !== "GESTOR" && papel !== "ADMINISTRADOR") return false;
    if (!item.flag) return true;
    return (flags ?? []).includes(item.flag);
  }

  return (
    <aside className="flex w-[260px] shrink-0 flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex flex-col gap-0.5 px-5 py-5">
        <span className="text-lg font-bold text-sidebar-foreground">{textos.app.marca}</span>
        <span className="text-xs font-semibold tracking-widest text-uppercase text-texto-sidebar-sub">
          {textos.app.subtitulo}
        </span>
      </div>

      <nav className="flex-1 overflow-y-auto px-3">
        {isLoading && (
          <p className="px-2 py-4 text-sm text-texto-sidebar-sub">{textos.estados.carregando}</p>
        )}
        {isError && (
          <p role="alert" className="px-2 py-4 text-sm text-cor-erro-suave">
            {textos.estados.erroGenerico}
          </p>
        )}
        {!isLoading && !isError && (
          <>
            <MenuGrupo
              titulo={textos.menu.grupoMenu}
              itens={ITENS_MENU}
              visivel={itemVisivel}
              rotulos={textos.menu.itens}
              pathname={pathname}
            />
            <MenuGrupo
              titulo={textos.menu.grupoGestao}
              itens={ITENS_GESTAO}
              visivel={itemVisivel}
              rotulos={textos.menu.itens}
              pathname={pathname}
            />
          </>
        )}
      </nav>

      <div className="flex flex-col gap-2 border-t border-sidebar-border px-4 py-4">
        <label className="flex items-center gap-2 text-xs text-texto-sidebar-sub">
          <span>{textos.rodape.presenca.rotulo}</span>
          <select className="min-w-0 flex-1 rounded border border-sidebar-border bg-sidebar px-1 py-0.5"
            value={presenca.data?.status ?? "OFFLINE"}
            onChange={(e) => mudarPresenca.mutate(e.target.value as StatusPresenca)}>
            <option value="ONLINE">{textos.rodape.presenca.online}</option>
            <option value="AUSENTE">{textos.rodape.presenca.ausente}</option>
            <option value="OFFLINE">{textos.rodape.presenca.offline}</option>
          </select>
        </label>
        <div className="flex flex-col gap-0.5">
          {email && <span className="truncate text-sm font-medium text-sidebar-foreground">{email}</span>}
          {papel && <span className="text-xs text-texto-sidebar-sub">{papel}</span>}
        </div>
        <button
          type="button"
          onClick={() => void encerrarSessao()}
          className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-sidebar-foreground hover:bg-sidebar-accent"
        >
          <LogOut className="size-4" />
          {textos.rodape.trocarConta}
        </button>
        <button
          type="button"
          onClick={() => void encerrarSessao()}
          className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-sidebar-foreground hover:bg-sidebar-accent"
        >
          <LogOut className="size-4" />
          {textos.rodape.sair}
        </button>
      </div>
    </aside>
  );
}

interface MenuGrupoProps {
  titulo: string;
  itens: ItemDeMenu[];
  visivel: (item: ItemDeMenu) => boolean;
  rotulos: Record<string, string>;
  pathname: string;
}

function MenuGrupo({ titulo, itens, visivel, rotulos, pathname }: MenuGrupoProps) {
  const itensVisiveis = itens.filter(visivel);
  if (itensVisiveis.length === 0) return null;

  return (
    <div className="mb-4">
      <p className="px-2 pt-3 pb-1.5 text-xs font-semibold tracking-widest text-uppercase text-texto-sidebar-titulo">
        {titulo}
      </p>
      <ul className="flex flex-col gap-0.5">
        {itensVisiveis.map((item) => {
          const Icone = item.icone;
          const ativo = pathname.startsWith(item.rota);
          return (
            <li key={item.chave}>
              <Link
                href={item.rota}
                className={
                  ativo
                    ? "flex items-center gap-2.5 rounded-md bg-sidebar-primary px-2.5 py-1.5 text-sm text-sidebar-primary-foreground"
                    : "flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm text-sidebar-foreground hover:bg-sidebar-accent"
                }
              >
                <Icone className="size-4 shrink-0" />
                <span className="truncate">{rotulos[item.chave] ?? item.chave}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
