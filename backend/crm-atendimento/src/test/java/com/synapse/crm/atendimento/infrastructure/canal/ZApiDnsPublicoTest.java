package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;

class ZApiDnsPublicoTest {
    @Test void rejeitaRedesPrivadasERebindingNaMesmaResolucao() throws Exception {
        for (String endereco : List.of("127.0.0.1", "10.0.0.1", "169.254.169.254", "172.16.1.1", "192.168.1.1", "100.64.0.1", "0.0.0.0", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1")) {
            var dns = new ZApiDnsPublico(h -> List.of(InetAddress.getByName(endereco)));
            assertThatThrownBy(() -> dns.lookup("cdn.exemplo.test")).isInstanceOf(java.net.UnknownHostException.class)
                    .hasMessage("Z_API_MIDIA_DNS_RECUSADO");
        }
        var misto = new ZApiDnsPublico(h -> List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")));
        assertThatThrownBy(() -> misto.lookup("cdn.exemplo.test")).isInstanceOf(java.net.UnknownHostException.class);
    }
    @Test void retornaExatamenteOsIpsValidadosParaOTransporte() throws Exception {
        var ips = List.of(InetAddress.getByName("8.8.8.8"));
        var dns = new ZApiDnsPublico(h -> ips);
        assertThat(dns.lookup("cdn.exemplo.test")).containsExactlyElementsOf(ips);
    }
}
