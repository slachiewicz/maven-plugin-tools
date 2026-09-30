/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.tools.plugin.extractor.annotations;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.api.Session;
import org.apache.maven.di.Injector;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest;
import org.apache.maven.tools.plugin.scanner.DefaultMojoScanner;
import org.apache.maven.tools.plugin.test.ApiTestSupport;
import org.apache.maven.tools.plugin.test.ApiTestSupport.TestProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The Maven 4 dependency injection reads the hand maintained {@code META-INF/maven/org.apache.maven.api.di.Inject}
 * index files (the parent POM sets {@code proc} to {@code none}, so no annotation processor writes them).
 * A {@code @Named} class missing from an index fails at runtime, not at compile time.
 */
class Maven4DependencyInjectionTest {
    @TempDir
    private Path targetDir;

    @Test
    void scannerAndExtractorsAreWiredFromTheIndexFiles() throws Exception {
        Injector injector = Injector.create().discover(getClass().getClassLoader());

        DefaultMojoScanner scanner = injector.getInstance(DefaultMojoScanner.class);
        scanner.setActiveExtractors(Collections.singleton(JavaAnnotationsMojoDescriptorExtractor.NAME));

        TestProject project =
                ApiTestSupport.project("g", "a", "1", targetDir, Files.createDirectories(targetDir.resolve("classes")));
        Session session = ApiTestSupport.session(project);
        DefaultPluginToolsRequest request = new DefaultPluginToolsRequest(session, project, new PluginDescriptor());
        request.setSkipErrorNoDescriptorsFound(true);

        // fails with "No mojo extractor with 'java-annotations' id." if the extractor is not injected by name
        assertDoesNotThrow(() -> scanner.populatePluginDescriptor(request));

        JavaAnnotationsMojoDescriptorExtractor extractor =
                injector.getInstance(JavaAnnotationsMojoDescriptorExtractor.class);
        assertNotNull(extractor.mojoAnnotationsScanner);
        for (String field : new String[] {"javadocInlineTagsToHtmlConverter", "javadocBlockTagsToHtmlConverter"}) {
            Field converter = JavaAnnotationsMojoDescriptorExtractor.class.getDeclaredField(field);
            converter.setAccessible(true);
            assertNotNull(converter.get(extractor), field);
        }
    }
}
