import { ExigeAcessoACampanhas } from "@/components/campanhas/exige-acesso-a-campanhas";
import { PaginaCampanhas } from "@/components/campanhas/pagina-campanhas";

export default function Campanhas() {
  return (
    <ExigeAcessoACampanhas>
      <PaginaCampanhas />
    </ExigeAcessoACampanhas>
  );
}
