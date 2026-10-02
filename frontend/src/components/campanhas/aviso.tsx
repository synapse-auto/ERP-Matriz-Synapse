import { CircleCheck, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * Avisos com o texto na cor do corpo (contraste AA garantido) e a cor semântica só no ícone. Os tons
 * `cor-atencao` e `cor-sucesso` sobre fundo claro ficam abaixo de 4,5:1 como cor de texto pequeno.
 */
export function AvisoDeSucesso({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p role="status" className={cn("flex items-center gap-1.5 text-sm", className)}>
      <CircleCheck className="size-4 shrink-0 text-cor-sucesso" aria-hidden />
      {children}
    </p>
  );
}

export function AvisoDeAtencao({
  children,
  className,
  destacado,
  papel = "status",
}: {
  children: ReactNode;
  className?: string;
  destacado?: boolean;
  papel?: "status" | "alert";
}) {
  return (
    <p
      role={papel}
      className={cn(
        "flex items-start gap-2 text-xs",
        destacado && "rounded-lg border border-cor-atencao/40 bg-cor-atencao/10 p-3 text-sm",
        className,
      )}
    >
      <TriangleAlert className="mt-0.5 size-4 shrink-0 text-cor-atencao" aria-hidden />
      <span>{children}</span>
    </p>
  );
}
