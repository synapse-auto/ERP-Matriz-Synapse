import { Geist } from "next/font/google";

// Referência visual do ZIP, carregada no build e aplicada somente aos layouts v2.
export const fonteFrontendV2 = Geist({
  subsets: ["latin"], display: "swap", preload: false, variable: "--fonte-frontend-v2",
});
