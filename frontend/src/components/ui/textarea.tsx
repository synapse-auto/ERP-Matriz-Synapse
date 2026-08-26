import * as React from "react"

import { cn } from "@/lib/utils"

function Textarea({ className, onChange, ...props }: React.ComponentProps<"textarea">) {

  function ajustarAltura(elemento: HTMLTextAreaElement) {
    elemento.style.height = "auto"
    elemento.style.height = `${elemento.scrollHeight}px`
  }

  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex min-h-16 min-w-0 w-full break-words overflow-x-hidden rounded-lg border border-input bg-transparent px-2.5 py-2 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      onChange={(evento) => {
        ajustarAltura(evento.currentTarget)
        onChange?.(evento)
      }}
      {...props}
    />
  )
}

export { Textarea }
