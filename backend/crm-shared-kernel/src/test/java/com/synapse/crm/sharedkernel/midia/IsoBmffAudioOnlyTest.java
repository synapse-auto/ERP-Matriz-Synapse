package com.synapse.crm.sharedkernel.midia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IsoBmffAudioOnlyTest {

    @Test
    void reconheceMoofApenasComoCaixaRaizValida() {
        byte[] fragmentado = {
            0, 0, 0, 8, 'f', 't', 'y', 'p',
            0, 0, 0, 8, 'm', 'o', 'o', 'f'
        };

        assertThat(IsoBmffAudioOnly.ehFragmentado(fragmentado)).isTrue();
        assertThat(IsoBmffAudioOnly.ehFragmentado(new byte[] {0, 0, 0, 8, 'm', 'd', 'a', 't'}))
                .isFalse();
        assertThat(IsoBmffAudioOnly.ehFragmentado(new byte[] {0, 0, 0, 9, 'm', 'o', 'o', 'f'}))
                .isFalse();
    }
}
