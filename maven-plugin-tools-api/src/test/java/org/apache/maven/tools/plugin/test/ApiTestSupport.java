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
package org.apache.maven.tools.plugin.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Language;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.SourceRoot;
import org.apache.maven.api.Type;
import org.apache.maven.api.Version;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.impl.standalone.ApiRunner;

/**
 * Test doubles for the Maven 4 API.
 * <p>
 * The v4 {@link Project} has no public implementation outside of Maven core, and a standalone {@link Session}
 * from {@link ApiRunner} cannot work with a foreign {@link Project}. So the session here is the standalone one for
 * everything that does not concern a project (versions, artifact factories, local repository lookups), and the
 * project related calls the plugin tools make are answered from a {@link TestProject}. The calls not covered fail
 * with {@link UnsupportedOperationException}, which keeps the list of API used by the tools explicit.
 */
public final class ApiTestSupport {
    private ApiTestSupport() {}

    /**
     * A project with just what the plugin tools read from it.
     */
    public static final class TestProject implements Project {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final Path basedir;
        private final Model model;
        private final List<Path> compileSourceRoots = new ArrayList<>();
        private final List<Path> classpath = new ArrayList<>();

        TestProject(String groupId, String artifactId, String version, Path basedir, Path outputDirectory) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.basedir = basedir;
            this.model = Model.newBuilder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(version)
                    .build(Build.newBuilder()
                            .directory(basedir.resolve("target").toString())
                            .outputDirectory(outputDirectory.toString())
                            .build())
                    .build();
        }

        /**
         * @return the source roots which {@link ProjectManager#getEnabledSourceRoots} returns for this project
         */
        public List<Path> getCompileSourceRoots() {
            return compileSourceRoots;
        }

        /**
         * @return the paths which {@link Session#resolveDependencies(Project, PathScope)} returns for this project
         */
        public List<Path> getClasspath() {
            return classpath;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public org.apache.maven.api.Packaging getPackaging() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<org.apache.maven.api.ProducedArtifact> getArtifacts() {
            return Collections.singletonList(
                    standaloneSession().createProducedArtifact(groupId, artifactId, version, "pom"));
        }

        @Override
        public Model getModel() {
            return model;
        }

        @Override
        public Path getPomPath() {
            return basedir.resolve("pom.xml");
        }

        @Override
        public Path getBasedir() {
            return basedir;
        }

        @Override
        public List<org.apache.maven.api.DependencyCoordinates> getDependencies() {
            return Collections.emptyList();
        }

        @Override
        public List<org.apache.maven.api.DependencyCoordinates> getManagedDependencies() {
            return Collections.emptyList();
        }

        @Override
        public boolean isTopProject() {
            return true;
        }

        @Override
        public boolean isRootProject() {
            return true;
        }

        @Override
        public Path getRootDirectory() {
            return basedir;
        }

        @Override
        public Optional<Project> getParent() {
            return Optional.empty();
        }

        @Override
        public List<org.apache.maven.api.model.Profile> getDeclaredProfiles() {
            return Collections.emptyList();
        }

        @Override
        public List<org.apache.maven.api.model.Profile> getEffectiveProfiles() {
            return Collections.emptyList();
        }

        @Override
        public List<org.apache.maven.api.model.Profile> getDeclaredActiveProfiles() {
            return Collections.emptyList();
        }

        @Override
        public List<org.apache.maven.api.model.Profile> getEffectiveActiveProfiles() {
            return Collections.emptyList();
        }
    }

    /**
     * The v4 API has no factory for {@link Dependency}, it only hands them out from a resolved dependency graph.
     */
    public static Dependency dependency(
            String groupId, String artifactId, String version, DependencyScope scope, String type) {
        Session session = standaloneSession();
        Version parsedVersion = session.parseVersion(version);
        Type dependencyType = session.requireType(type);
        return new Dependency() {
            @Override
            public Type getType() {
                return dependencyType;
            }

            @Override
            public DependencyScope getScope() {
                return scope;
            }

            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public DependencyCoordinates toCoordinates() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getGroupId() {
                return groupId;
            }

            @Override
            public String getArtifactId() {
                return artifactId;
            }

            @Override
            public Version getVersion() {
                return parsedVersion;
            }

            @Override
            public Version getBaseVersion() {
                return parsedVersion;
            }

            @Override
            public String getClassifier() {
                return "";
            }

            @Override
            public String getExtension() {
                return dependencyType.getExtension();
            }

            @Override
            public boolean isSnapshot() {
                return false;
            }
        };
    }

    private static Session standalone;

    /**
     * A standalone session working on an own, initially empty local repository, so that a test controls which
     * artifacts resolve. The standalone session has no transport: it can only resolve what is in this repository,
     * and each artifact needs a {@code _remote.repositories} file next to it to count as available.
     */
    public static Session sessionWithLocalRepository(Path localRepository, Project... reactorProjects) {
        return wrap(ApiRunner.createSession(null, localRepository), reactorProjects);
    }

    private static synchronized Session standaloneSession() {
        if (standalone == null) {
            standalone = ApiRunner.createSession();
        }
        return standalone;
    }

    /**
     * Creates a project, to be used with a session from {@link #session(Project...)}.
     */
    public static TestProject project(
            String groupId, String artifactId, String version, Path basedir, Path outputDirectory) {
        return new TestProject(groupId, artifactId, version, basedir, outputDirectory);
    }

    /**
     * Creates a session whose {@link Session#getProjects()} are the given ones.
     */
    public static Session session(Project... reactorProjects) {
        return wrap(standaloneSession(), reactorProjects);
    }

    private static Session wrap(Session standalone, Project... reactorProjects) {
        ProjectManager projectManager = (ProjectManager) Proxy.newProxyInstance(
                ApiTestSupport.class.getClassLoader(), new Class<?>[] {ProjectManager.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getEnabledSourceRoots":
                            return ((TestProject) args[0])
                                    .getCompileSourceRoots().stream().map(ApiTestSupport::sourceRoot);
                        case "getExecutionProject":
                            return Optional.empty();
                        case "getRemoteProjectRepositories":
                            return Collections.emptyList();
                        case "toString":
                            return "test ProjectManager";
                        default:
                            throw new UnsupportedOperationException(method.toString());
                    }
                });
        return (Session) Proxy.newProxyInstance(
                ApiTestSupport.class.getClassLoader(), new Class<?>[] {Session.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getService":
                            if (args[0] == ProjectManager.class) {
                                return projectManager;
                            }
                            break;
                        case "getProjects":
                            return Collections.unmodifiableList(Arrays.asList(reactorProjects));
                        case "resolveDependencies":
                            if (args.length == 2 && args[0] instanceof TestProject) {
                                return ((TestProject) args[0]).getClasspath();
                            }
                            throw new UnsupportedOperationException(method.toString());
                        default:
                            break;
                    }
                    try {
                        return method.invoke(standalone, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static SourceRoot sourceRoot(Path directory) {
        return new SourceRoot() {
            @Override
            public Path directory() {
                return directory;
            }

            @Override
            public java.nio.file.PathMatcher matcher(Collection<String> defaultIncludes, boolean useDefaultExcludes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProjectScope scope() {
                return ProjectScope.MAIN;
            }

            @Override
            public Language language() {
                return Language.JAVA_FAMILY;
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
    }
}
