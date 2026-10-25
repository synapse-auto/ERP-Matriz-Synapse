package com.synapse.crm.atendimento.infrastructure.canal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;

/** O proprio transporte usa os IPs validados: nao ha segunda resolucao vulneravel a rebinding. */
final class ZApiDnsPublico implements Dns {
    private final Dns origem;
    ZApiDnsPublico(Dns origem) { this.origem = origem; }
    @Override public List<InetAddress> lookup(String host) throws UnknownHostException {
        List<InetAddress> ips;
        try { ips = origem.lookup(host); }
        catch (UnknownHostException e) { throw new UnknownHostException("Z_API_MIDIA_DNS_INDISPONIVEL"); }
        if (ips.isEmpty() || ips.stream().anyMatch(ip -> !publico(ip)))
            throw new UnknownHostException("Z_API_MIDIA_DNS_RECUSADO");
        return List.copyOf(ips);
    }
    private static boolean publico(InetAddress ip) {
        if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress() || ip.isMulticastAddress()) return false;
        byte[] b = ip.getAddress();
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && c >= 64 && c <= 127) || (a == 169 && c == 254)
                    || (a == 172 && c >= 16 && c <= 31) || (a == 192 && (c == 168 || c == 0))
                    || (a == 198 && (c == 18 || c == 19 || (c == 51 && d == 100)))
                    || (a == 203 && c == 0 && d == 113));
        }
        // Somente global unicast 2000::/3; exclui blocos especiais 2001::/23 e 2002::/16.
        return b.length == 16 && (b[0] & 224) == 32
                && !((b[0] & 255) == 32 && ((b[1] & 255) == 2
                    || ((b[1] & 255) == 1 && ((b[2] & 255) <= 1
                        || ((b[2] & 255) == 13 && (b[3] & 255) == 184)))));
    }
}
