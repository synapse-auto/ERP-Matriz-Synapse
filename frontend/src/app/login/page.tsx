import { Bot, BriefcaseBusiness, ChartNoAxesCombined } from "lucide-react";

import { MarcaSynapse } from "@/components/auth/marca-synapse";
import { LoginForm } from "@/components/auth/login-form";
import { buscarTextos } from "@/lib/config/fetch-config";

export default async function LoginPage() {
  const textos = (await buscarTextos()).login;
  const destaques = [
    { icone: BriefcaseBusiness, texto: textos.destaqueAtendimentos },
    { icone: Bot, texto: textos.destaqueAutomacao },
    { icone: ChartNoAxesCombined, texto: textos.destaqueEquipe },
  ];

  return (
    <main className="synapse-login">
      <section className="synapse-login__apresentacao" aria-labelledby="apresentacao-login-titulo">
        <MarcaSynapse alt={textos.marcaSynapse} className="synapse-login__marca" />
        <div className="synapse-login__conteudo">
          <h1 id="apresentacao-login-titulo" className="synapse-login__titulo">
            {textos.apresentacaoTitulo}
          </h1>
          <p className="synapse-login__subtitulo">{textos.apresentacaoSubtitulo}</p>
        </div>
        <ul className="synapse-login__destaques">
          {destaques.map(({ icone: Icone, texto }) => (
            <li key={texto} className="synapse-login__destaque">
              <span className="synapse-login__icone-destaque">
                <Icone aria-hidden="true" size={18} />
              </span>
              {texto}
            </li>
          ))}
        </ul>
      </section>
      <section className="synapse-login__formulario" aria-label={textos.titulo}>
        <LoginForm />
      </section>
    </main>
  );
}
