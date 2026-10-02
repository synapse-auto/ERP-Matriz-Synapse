import { Suspense } from "react";

import { ExigeAcessoACampanhas } from "@/components/campanhas/exige-acesso-a-campanhas";
import { PaginaNovaCampanha } from "@/components/campanhas/pagina-nova-campanha";

export default function NovaCampanha() {
  return (
    <ExigeAcessoACampanhas>
      <Suspense>
        <PaginaNovaCampanha />
      </Suspense>
    </ExigeAcessoACampanhas>
  );
}
