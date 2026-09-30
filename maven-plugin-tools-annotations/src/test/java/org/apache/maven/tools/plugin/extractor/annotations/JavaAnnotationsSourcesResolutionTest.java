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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest;
import org.apache.maven.tools.plugin.extractor.ExtractionException;
import org.apache.maven.tools.plugin.test.ApiTestSupport;
import org.apache.maven.tools.plugin.test.ApiTestSupport.TestProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what the Maven 3 API did behind {@code RepositorySystem}, {@code ArchiverManager} and
 * {@code MavenProject#getProjectReferences()}.
 */
class JavaAnnotationsSourcesResolutionTest {
    @TempDir
    private Path tmp;

    private Path seedSourcesJar(Path localRepository, String entryName, String content) throws IOException {
        Path dir = Files.createDirectories(localRepository.resolve("g/a/1.0"));
        Path jar = dir.resolve("a-1.0-sources.jar");
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.write(dir.resolve("_remote.repositories"), "a-1.0-sources.jar>=\n".getBytes(StandardCharsets.UTF_8));
        return jar;
    }

    private DefaultPluginToolsRequest request(Session session, TestProject project) {
        return new DefaultPluginToolsRequest(session, project, new PluginDescriptor());
    }

    @Test
    void sourcesJarIsResolvedFromTheLocalRepositoryAndExtracted() throws Exception {
        Path localRepository = Files.createDirectories(tmp.resolve("repo"));
        seedSourcesJar(localRepository, "example/Foo.java", "package example; public class Foo {}\n");

        TestProject project =
                ApiTestSupport.project("me", "plugin", "1", tmp.resolve("project"), tmp.resolve("project/classes"));
        Session session = ApiTestSupport.sessionWithLocalRepository(localRepository, project);
        Artifact dependency = session.createArtifact("g", "a", "1.0", "jar");

        try (JavaSourceModel sourceModel = new JavaSourceModel(StandardCharsets.UTF_8)) {
            new JavaAnnotationsMojoDescriptorExtractor()
                    .extendJavaSourceModelWithSourcesJar(sourceModel, dependency, request(session, project), "sources");
        }

        Path extracted = tmp.resolve("project/target/maven-plugin-plugin-sources/g/a/1.0/sources/example/Foo.java");
        assertTrue(Files.isRegularFile(extracted), extracted.toString());
    }

    @Test
    void anUnresolvableSourcesJarIsSkipped() throws Exception {
        Path localRepository = Files.createDirectories(tmp.resolve("repo"));

        TestProject project =
                ApiTestSupport.project("me", "plugin", "1", tmp.resolve("project"), tmp.resolve("project/classes"));
        Session session = ApiTestSupport.sessionWithLocalRepository(localRepository, project);
        Artifact dependency = session.createArtifact("g", "a", "1.0", "jar");

        try (JavaSourceModel sourceModel = new JavaSourceModel(StandardCharsets.UTF_8)) {
            new JavaAnnotationsMojoDescriptorExtractor()
                    .extendJavaSourceModelWithSourcesJar(sourceModel, dependency, request(session, project), "sources");
        }

        assertFalse(Files.exists(tmp.resolve("project/target/maven-plugin-plugin-sources")));
    }

    @Test
    void anEntryOutsideTheExtractionDirectoryIsRejected() throws Exception {
        Path localRepository = Files.createDirectories(tmp.resolve("repo"));
        seedSourcesJar(localRepository, "../../evil.txt", "x");

        TestProject project =
                ApiTestSupport.project("me", "plugin", "1", tmp.resolve("project"), tmp.resolve("project/classes"));
        Session session = ApiTestSupport.sessionWithLocalRepository(localRepository, project);
        Artifact dependency = session.createArtifact("g", "a", "1.0", "jar");

        try (JavaSourceModel sourceModel = new JavaSourceModel(StandardCharsets.UTF_8)) {
            assertThrows(
                    ExtractionException.class,
                    () -> new JavaAnnotationsMojoDescriptorExtractor()
                            .extendJavaSourceModelWithSourcesJar(
                                    sourceModel, dependency, request(session, project), "sources"));
        }
        assertFalse(Files.exists(tmp.resolve("project/target/maven-plugin-plugin-sources/g/a/evil.txt")));
    }

    @Test
    void reactorProjectsAreFoundByCoordinates() {
        TestProject main =
                ApiTestSupport.project("me", "plugin", "1", tmp.resolve("plugin"), tmp.resolve("plugin/classes"));
        TestProject sibling =
                ApiTestSupport.project("me", "sibling", "1", tmp.resolve("sibling"), tmp.resolve("sibling/classes"));
        Session session = ApiTestSupport.session(main, sibling);
        JavaAnnotationsMojoDescriptorExtractor extractor = new JavaAnnotationsMojoDescriptorExtractor();

        Project found =
                extractor.getFromProjectReferences(session, session.createArtifact("me", "sibling", "1", "jar"));
        assertSame(sibling, found);
        assertNull(extractor.getFromProjectReferences(session, session.createArtifact("me", "sibling", "2", "jar")));
        assertNull(extractor.getFromProjectReferences(
                session, session.createArtifact("me", "sibling", "1", "tests", "jar", null)));
        assertEquals(2, session.getProjects().size());
    }
}
