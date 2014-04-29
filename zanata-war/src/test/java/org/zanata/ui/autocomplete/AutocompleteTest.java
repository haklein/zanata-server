/*
 * Copyright 2014, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.ui.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zanata.common.LocaleId;
import org.zanata.dao.LocaleDAO;
import org.zanata.model.HLocale;
import org.zanata.seam.SeamAutowire;
import org.zanata.service.LocaleService;

import com.google.common.collect.Lists;

/**
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
@Test(groups = { "unit-tests" })
public class AutocompleteTest {

    static final List<HLocale> supportedLocales = Lists.newArrayList(
            new HLocale(LocaleId.DE), new HLocale(LocaleId.EN), new HLocale(
                    LocaleId.EN_US), new HLocale(LocaleId.ES), new HLocale(
                    LocaleId.FR));

    SeamAutowire seam = SeamAutowire.instance();

    @Mock
    LocaleService localeServiceImpl;
    @Mock
    LocaleDAO localeDAO;

    @BeforeMethod
    public void prepareTest() {
        MockitoAnnotations.initMocks(this);
        seam.reset()
            .use("localeServiceImpl", localeServiceImpl)
            .use("localeDAO", localeDAO)
            .ignoreNonResolvable();
    }

    @Test
    public void suggestLocales() {
        when(localeServiceImpl.getSupportedLocales())
                .thenReturn(supportedLocales);
        when(localeDAO.findAllActive())
                .thenReturn(supportedLocales);

        LocaleAutocomplete autocomplete = new LocaleAutocomplete() {
            @Override
            protected Collection<HLocale> getLocales() {
                return Lists.newArrayList();
            }

            @Override
            public void onSelectItemAction() {
            }
        };

        autocomplete.setQuery("en");
        assertThat(autocomplete.suggest()).hasSize(3);
        // ENglish, ENglish-us and frENch
        assertThat(autocomplete.suggest()).contains(new HLocale(LocaleId.EN),
                new HLocale(LocaleId.EN_US), new HLocale(LocaleId.FR));

        autocomplete.setQuery("e");
        assertThat(autocomplete.suggest()).hasSize(5);
        // Everything
        assertThat(autocomplete.suggest()).contains(new HLocale(LocaleId.DE),
                new HLocale(LocaleId.EN), new HLocale(LocaleId.EN_US),
                new HLocale(LocaleId.ES), new HLocale(LocaleId.FR));

        autocomplete.setQuery("no-results-expected");
        assertThat(autocomplete.suggest()).hasSize(0);
        // Nothing
    }
}
