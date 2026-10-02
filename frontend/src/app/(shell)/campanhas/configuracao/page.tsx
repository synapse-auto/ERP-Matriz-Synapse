import { ExigeAcessoACampanhas } from "@/components/campanhas/exige-acesso-a-campanhas";
import { PaginaConfiguracaoDeCampanhas } from "@/components/campanhas/pagina-configuracao-de-campanhas";

export default function ConfiguracaoDeCampanhas() {
  return (
    <ExigeAcessoACampanhas>
      <PaginaConfiguracaoDeCampanhas />
    </ExigeAcessoACampanhas>
  );
}
