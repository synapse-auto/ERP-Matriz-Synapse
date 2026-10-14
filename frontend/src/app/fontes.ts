import { Inter } from "next/font/google";
import localFont from "next/font/local";

export const inter = Inter({ subsets: ["latin", "latin-ext"], display: "swap", variable: "--fonte-base-carregada" });
export const jetBrainsMono = localFont({ src: "./fonts/JetBrainsMono-Variable.woff2", weight: "100 800", display: "swap", variable: "--fonte-mono-carregada" });
export const classesDasFontes = `${inter.variable} ${jetBrainsMono.variable} ${inter.className}`;
