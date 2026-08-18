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
  LayoutDashboard,
  LogOut,
  Megaphone,
  MessageSquareText,
  Tag,
  Users,
} from "lucide-react";

import { apiFetch } from "@/lib/api/http-client";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { atualizarPresenca } from "@/lib/equipe/api";
import type { StatusPresenca } from "@/lib/equipe/types";
import { useMeuUsuario } from "@/lib/equipe/use-equipe";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { iniciaisDoNome } from "@/lib/utils";

interface ItemDeMenu {
  chave: string;
  rota: string;
  icone: React.ComponentType<{ className?: string }>;
  /** Ausente = feature central, sempre visível. Presente = só some se a flag não vier habilitada. */
  flag?: string;
}

const ITENS_MENU: ItemDeMenu[] = [
  { chave: "atendimentos", rota: "/atendimentos", icone: Headset },
  { chave: "dashboard", rota: "/dashboard", icone: LayoutDashboard, flag: "dashboard" },
  { chave: "agenda", rota: "/agenda", icone: BookUser },
  { chave: "tags", rota: "/tags", icone: Tag },
  { chave: "mensagensRapidas", rota: "/mensagens-rapidas", icone: MessageSquareText },
  { chave: "bancoArquivos", rota: "/banco-arquivos", icone: Folder, flag: "banco_arquivos" },
  { chave: "mensagensProgramadas", rota: "/mensagens-programadas", icone: Clock },
  { chave: "lembretes", rota: "/lembretes", icone: Bell },
];

const ITENS_GESTAO: ItemDeMenu[] = [
  { chave: "equipe", rota: "/equipe", icone: Users },
  { chave: "campanhas", rota: "/campanhas", icone: Megaphone, flag: "campanhas" },
  { chave: "automacao", rota: "/automacao", icone: Bot },
  { chave: "horarios", rota: "/horarios", icone: CalendarClock, flag: "horarios" },
  { chave: "relatorios", rota: "/relatorios", icone: BarChart3, flag: "relatorios" },
];

const OPCOES_PRESENCA: StatusPresenca[] = ["ONLINE", "AUSENTE", "OFFLINE"];

/**
 * Cor por `var(--token)`, não classe Tailwind: `--texto-fraco` (usado para OFFLINE) não está
 * registrado em `@theme inline` — só existe como custom property, no mesmo padrão que
 * `pagina-tags.tsx` já usa para a paleta de cor da tag.
 */
const COR_PRESENCA: Record<StatusPresenca, string> = {
  ONLINE: "var(--cor-sucesso)",
  AUSENTE: "var(--cor-atencao)",
  OFFLINE: "var(--texto-fraco)",
};

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

function useTemaConfig() {
  return useQuery({
    queryKey: ["config", "tema"],
    queryFn: () => apiFetch<{ logoUrl?: string | null }>("/api/v1/config/tema"),
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
  const { data: flags, isLoading, isError, refetch } = useFeaturesHabilitadas();
  const { data: tema } = useTemaConfig();
  const papel = useAuthStore((estado) => estado.papel);
  const meuUsuario = useMeuUsuario();
  const cache = useQueryClient();
  const mudarPresenca = useMutation({
    mutationFn: atualizarPresenca,
    onSuccess: (dados) =>
      cache.setQueryData(["me"], (atual: typeof meuUsuario.data) =>
        atual ? { ...atual, presenca: dados.status } : atual,
      ),
  });
  const [popupAberto, setPopupAberto] = useState(false);
  // E31b: logoUrl agora aponta sempre para a rota (/api/v1/config/logo), nunca mais null — a
  // ausência do arquivo vira 404 no servidor, não um logoUrl vazio. O onError é o que faz o
  // fallback (quadrado com gradiente) continuar funcionando quando o filho não tem logo.
  const [logoFalhou, setLogoFalhou] = useState(false);

  function itemVisivel(item: ItemDeMenu): boolean {
    if (item.chave === "equipe" && papel !== "GESTOR" && papel !== "ADMINISTRADOR") return false;
    if (
      item.chave === "dashboard" &&
      papel !== "GESTOR" &&
      papel !== "SUBGESTOR" &&
      papel !== "ADMINISTRADOR"
    )
      return false;
    if (!item.flag) return true;
    return (flags ?? []).includes(item.flag);
  }

  const statusAtual = meuUsuario.data?.presenca ?? "OFFLINE";
  const rotuloDaPresenca = (status: StatusPresenca) =>
    ({
      ONLINE: textos.rodape.presenca.online,
      AUSENTE: textos.rodape.presenca.ausente,
      OFFLINE: textos.rodape.presenca.offline,
    })[status];

  return (
    <aside
      className="m-3 flex w-[260px] shrink-0 flex-col overflow-hidden rounded-xl bg-sidebar text-texto-sidebar-item"
      data-slot="sidebar"
    >
      <div className="flex items-center gap-3 px-[18px] py-5">
        {tema?.logoUrl && !logoFalhou ? (
          // logoUrl é dado de instância (tema.json), não um asset local — next/image exigiria
          // declarar o domínio em next.config a cada filho novo, o que quebraria "trocar de
          // cliente sem editar código". <img> puro aceita qualquer URL em runtime.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={tema.logoUrl}
            alt={textos.app.marca}
            className="size-10 flex-none rounded-lg object-contain"
            onError={() => setLogoFalhou(true)}
          />
        ) : (
          <div
            className="flex size-10 flex-none items-center justify-center rounded-lg"
            style={{
              background: `linear-gradient(150deg, var(--marca-icone-gradiente-inicio), var(--marca-icone-gradiente-fim))`,
            }}
          >
            <div className="size-[17px] rotate-45 rounded-[3px] bg-white" />
          </div>
        )}
        <div className="min-w-0">
          <p className="text-[15px] leading-tight font-extrabold tracking-tight text-white">
            {textos.app.marca}
          </p>
          <p className="mt-0.5 text-[10px] font-semibold tracking-[.16em] text-texto-sidebar-sub uppercase">
            {textos.app.subtitulo}
          </p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3.5">
        {isLoading && (
          <p className="px-2 py-4 text-sm text-texto-sidebar-sub">{textos.estados.carregando}</p>
        )}
        {isError && (
          <ErroDeCarregamento
            mensagem={textos.estados.erroGenerico}
            onTentarNovamente={() => refetch()}
            className="mx-2 border-white/15 bg-white/5 text-cor-erro-suave"
          />
        )}
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
      </nav>

      <div className="relative border-t border-white/8 px-3 py-3.5">
        {popupAberto && (
          <div className="absolute inset-x-3 bottom-[74px] z-40 rounded-xl border border-white/12 bg-fundo-sidebar-bloco p-1.5 shadow-lg">
            <p className="px-2.5 pt-2 pb-1.5 text-[10px] font-bold tracking-[.12em] text-texto-sidebar-sub">
              {textos.rodape.presenca.rotulo}
            </p>
            {OPCOES_PRESENCA.map((status) => {
              const selecionado = statusAtual === status;
              return (
                <button
                  key={status}
                  type="button"
                  onClick={() => mudarPresenca.mutate(status)}
                  className={
                    selecionado
                      ? "flex w-full items-center gap-2.5 rounded-lg bg-sidebar-item-overlay-ativo px-2.5 py-2 text-left text-[13.5px] font-bold text-white"
                      : "flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13.5px] font-semibold text-texto-sidebar-item hover:bg-sidebar-item-overlay-hover hover:text-sidebar-item-texto-hover"
                  }
                >
                  <span
                    className="size-2.5 flex-none rounded-full"
                    style={{ backgroundColor: COR_PRESENCA[status] }}
                  />
                  <span className="flex-1">{rotuloDaPresenca(status)}</span>
                  {selecionado && <Check className="size-4 text-sidebar-item-icone-ativo" />}
                </button>
              );
            })}
            <div className="my-1.5 h-px bg-white/10" />
            <Link
              href="/trocar-senha"
              onClick={() => setPopupAberto(false)}
              className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13.5px] font-semibold text-texto-sidebar-item hover:bg-sidebar-item-overlay-hover hover:text-sidebar-item-texto-hover"
            >
              <KeyRound className="size-4" />
              {textos.rodape.trocarSenha}
            </Link>
            <button
              type="button"
              onClick={() => void encerrarSessao()}
              className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13.5px] font-semibold text-sidebar-item-texto-perigo hover:bg-sidebar-item-overlay-perigo"
            >
              <LogOut className="size-4" />
              {textos.rodape.sair}
            </button>
          </div>
        )}

        <button
          type="button"
          onClick={() => setPopupAberto((atual) => !atual)}
          className="flex w-full min-w-0 items-center gap-2.5 rounded-xl px-2 py-2 hover:bg-sidebar-item-overlay-hover"
        >
          <span className="relative flex size-[38px] flex-none items-center justify-center rounded-[11px] bg-primary text-sm font-bold text-white">
            {meuUsuario.data ? iniciaisDoNome(meuUsuario.data.nome) : "?"}
            <span
              className="absolute -right-0.5 -bottom-0.5 size-3 rounded-full border-2 border-sidebar"
              style={{ backgroundColor: COR_PRESENCA[statusAtual] }}
            />
          </span>
          <span className="min-w-0 flex-1 text-left">
            {meuUsuario.data && (
              <span className="block truncate text-[13.5px] font-bold text-white">
                {meuUsuario.data.nome}
              </span>
            )}
            <span className="block truncate text-[11.5px] font-medium text-texto-sidebar-sub">
              {meuUsuario.data?.papel ?? papel} · {rotuloDaPresenca(statusAtual)}
            </span>
          </span>
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
    <div className="mb-2">
      <p className="px-2.5 pt-3 pb-[7px] text-[10.5px] font-bold tracking-[.11em] text-texto-sidebar-titulo uppercase">
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
                    ? "flex items-center gap-3 rounded-[10px] bg-sidebar-item-overlay-ativo px-3 py-2.5 text-[16px] font-semibold text-white shadow-[inset_3px_0_0_var(--sidebar-item-acento-ativo)] hover:bg-sidebar-item-overlay-ativo-hover"
                    : "flex items-center gap-3 rounded-[10px] px-3 py-2.5 text-[16px] font-medium text-texto-sidebar-item hover:bg-sidebar-item-overlay-hover hover:text-sidebar-item-texto-hover"
                }
              >
                <Icone
                  className={ativo ? "size-[21px] shrink-0 text-sidebar-item-icone-ativo" : "size-[21px] shrink-0"}
                />
                <span className="flex-1">{rotulos[item.chave] ?? item.chave}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
