import * as React from "react"

import { cn } from "@/lib/utils"

function atribuirRef<T>(ref: React.Ref<T> | undefined, valor: T | null) {
  if (typeof ref === "function") {
    ref(valor)
  } else if (ref) {
    ;(ref as React.MutableRefObject<T | null>).current = valor
  }
}

function Textarea({ className, onChange, ref: referenciaExterna, ...props }: React.ComponentProps<"textarea">) {
  const referenciaInterna = React.useRef<HTMLTextAreaElement>(null)

  const atribuirReferencias = React.useCallback((elemento: HTMLTextAreaElement | null) => {
    referenciaInterna.current = elemento
    atribuirRef(referenciaExterna, elemento)
  }, [referenciaExterna])

  function ajustarAltura(elemento: HTMLTextAreaElement) {
    elemento.style.height = "auto"
    elemento.style.height = `${elemento.scrollHeight}px`
  }

  React.useLayoutEffect(() => {
    if (referenciaInterna.current) {
      ajustarAltura(referenciaInterna.current)
    }
  }, [props.value])

  return (
    <textarea
      data-slot="textarea"
      ref={atribuirReferencias}
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
