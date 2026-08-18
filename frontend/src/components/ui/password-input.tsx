import * as React from "react"
import { Eye, EyeOff } from "lucide-react"

import { Input } from "@/components/ui/input"
import { useTextos } from "@/lib/config/textos-provider"
import { cn } from "@/lib/utils"

function PasswordInput({ className, ...props }: React.ComponentProps<"input">) {
  const [mostrar, setMostrar] = React.useState(false)
  const t = useTextos()
  // Usa o fallback para n\u00e3o quebrar caso o textos.json falhe ou n\u00e3o tenha carregado,
  // mas espera-se que t.auth exista.
  const authTextos = t?.auth || { mostrarSenha: "Mostrar senha", ocultarSenha: "Ocultar senha" }

  return (
    <div className="relative">
      <Input
        type={mostrar ? "text" : "password"}
        className={cn("pr-9", className)}
        {...props}
      />
      <button
        type="button"
        className="absolute right-0 top-0 flex h-full items-center justify-center px-2.5 text-muted-foreground hover:text-foreground outline-none focus-visible:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        onClick={() => setMostrar((prev) => !prev)}
        aria-label={mostrar ? authTextos.ocultarSenha : authTextos.mostrarSenha}
        title={mostrar ? authTextos.ocultarSenha : authTextos.mostrarSenha}
      >
        {mostrar ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  )
}

export { PasswordInput }
