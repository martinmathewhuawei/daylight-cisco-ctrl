/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.yang2sources.plugin;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.yang.model.api.Module;
import org.opendaylight.controller.yang.model.api.SchemaContext;
import org.opendaylight.controller.yang.model.parser.api.YangModelParser;
import org.opendaylight.controller.yang2sources.plugin.ConfigArg.CodeGeneratorArg;
import org.opendaylight.controller.yang2sources.plugin.ConfigArg.ResourceProviderArg;
import org.opendaylight.controller.yang2sources.spi.CodeGenerator;
import org.opendaylight.controller.yang2sources.spi.ResourceGenerator;

import com.google.common.collect.Lists;

@Ignore
public class GenerateSourcesTest {

    @Mock
    private YangModelParser parser;
    private String yang;
    private YangToSourcesMojo mojo;
    private File outDir;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        yang = new File(getClass().getResource("/mock.yang").getFile())
                .getParent();
        outDir = new File("outputDir");
        mojo = new YangToSourcesMojo(
                new ResourceProviderArg[] {
                        new ResourceProviderArg(ProviderMock.class.getName(),
                                outDir),
                        new ResourceProviderArg(ProviderMock2.class.getName(),
                                outDir) },
                new CodeGeneratorArg[] { new CodeGeneratorArg(
                        GeneratorMock.class.getName(), outDir) }, parser, yang);
    }

    @Test
    public void test() throws Exception {
        mojo.execute();
        verify(parser, times(1)).parseYangModels(anyListOf(File.class));
        assertThat(GeneratorMock.called, is(1));
        assertThat(GeneratorMock.outputDir, is(outDir));
    }

    @Test
    public void testRes() throws Exception {
        mojo.execute();
        assertThat(ProviderMock.called, is(1));
        assertThat(ProviderMock2.called, is(1));
        assertThat(ProviderMock2.baseDir, is(outDir));
        assertThat(ProviderMock.baseDir, is(outDir));
    }

    public static class GeneratorMock implements CodeGenerator {

        private static int called = 0;
        private static File outputDir;

        @Override
        public Collection<File> generateSources(SchemaContext context,
                File baseDir, Set<Module> yangModules) {
            called++;
            outputDir = baseDir;
            return Lists.newArrayList();
        }
    }

    public static class ProviderMock implements ResourceGenerator {

        private static int called = 0;
        private static File baseDir;

        @Override
        public void generateResourceFiles(Collection<File> resources,
                File outputDir) {
            called++;
            baseDir = outputDir;
        }
    }

    public static class ProviderMock2 implements ResourceGenerator {

        private static int called = 0;
        private static File baseDir;

        @Override
        public void generateResourceFiles(Collection<File> resources,
                File outputDir) {
            called++;
            baseDir = outputDir;
        }
    }
}
