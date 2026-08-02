import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { Sidebar } from "@/components/shell/sidebar";
import { NOME_COOKIE_REFRESH } from "@/lib/auth/constants";

/**
 * Proteção de rota no servidor: só confere a PRESENÇA do cookie httpOnly (leitura, não consome),
 * nunca tenta renová-lo aqui — Server Components não podem setar cookie durante a renderização
 * (ver Javadoc do AuthProvider), e o refresh token rotaciona a cada uso, então mintar um token aqui
 * perderia a rotação. A hidratação de verdade do access token acontece no cliente, via
 * AuthProvider, assim que esta árvore monta.
 */
export default async function ShellLayout({ children }: { children: React.ReactNode }) {
  const cookieStore = await cookies();
  if (!cookieStore.has(NOME_COOKIE_REFRESH)) {
    redirect("/login");
  }

  return (
    <div className="flex flex-1">
      <Sidebar />
      <main className="flex flex-1 flex-col overflow-y-auto bg-background">{children}</main>
    </div>
  );
}
