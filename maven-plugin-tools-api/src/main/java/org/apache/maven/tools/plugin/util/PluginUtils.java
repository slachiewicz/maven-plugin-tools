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
package org.apache.maven.tools.plugin.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Convenience methods to play with Maven plugins.
 *
 * @author jdcasey
 *
 */
public final class PluginUtils {
    private PluginUtils() {
        // nop
    }

    /**
     * @param basedir not null
     * @param include not null
     * @return list of included files with default SCM excluded files
     */
    public static String[] findSources(String basedir, String include) {
        return PluginUtils.findSources(basedir, include, null);
    }

    /**
     * @param basedir not null
     * @param include not null
     * @param exclude could be null
     * @return list of included files
     */
    public static String[] findSources(String basedir, String include, String exclude) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(basedir);
        scanner.setIncludes(new String[] {include});
        if (!(exclude == null || exclude.isEmpty())) {
            scanner.setExcludes(new String[] {exclude, StringUtils.join(FileUtils.getDefaultExcludes(), ",")});
        } else {
            scanner.setExcludes(FileUtils.getDefaultExcludes());
        }

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Sorts the specified mojo descriptors by goal name.
     *
     * @param mojoDescriptors The mojo descriptors to sort, may be <code>null</code>.
     * @see MojoDescriptor#getGoal()
     */
    public static void sortMojos(List<MojoDescriptor> mojoDescriptors) {
        if (mojoDescriptors != null) {
            mojoDescriptors.sort(Comparator.comparing(MojoDescriptor::getGoal));
        }
    }

    /**
     * Sorts the specified mojo parameters by name.
     *
     * @param parameters The mojo parameters to sort, may be <code>null</code>.
     * @see Parameter#getName()
     * @since 2.4.4
     */
    public static void sortMojoParameters(List<Parameter> parameters) {
        if (parameters != null) {
            parameters.sort(Comparator.comparing(Parameter::getName));
        }
    }

    /**
     * @param mojoClassName a fully qualified Mojo implementation class name, not null
     * @param session the current session, used to resolve the compile classpath of the project, could be null
     *                if the project is also null
     * @param project a Project instance, could be null
     * @return <code>true</code> if the Mojo class implements <code>MavenReport</code>,
     * <code>false</code> otherwise.
     * @throws IllegalArgumentException if any
     * @since 5.0.0
     */
    public static boolean isMavenReport(String mojoClassName, Session session, Project project)
            throws IllegalArgumentException {
        if (mojoClassName == null) {
            throw new IllegalArgumentException("mojo implementation should be declared");
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (project != null) {
            List<Path> classPathEntries = new ArrayList<>();
            addCompileClasspath(session, project, classPathEntries);
            Optional<Project> executionProject =
                    session.getService(ProjectManager.class).getExecutionProject(project);
            if (executionProject.isPresent()) {
                addCompileClasspath(session, executionProject.get(), classPathEntries);
            }

            List<URL> urls = new ArrayList<>(classPathEntries.size());
            for (Path classPathEntry : classPathEntries) {
                try {
                    urls.add(classPathEntry.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), classLoader);
        }

        try {
            Class<?> clazz = Class.forName(mojoClassName, false, classLoader);

            return MavenReport.class.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void addCompileClasspath(Session session, Project project, List<Path> classPathEntries) {
        Path outputDirectory = project.getOutputDirectory(ProjectScope.MAIN);
        if (outputDirectory != null) {
            classPathEntries.add(outputDirectory);
        }
        classPathEntries.addAll(session.resolveDependencies(project, PathScope.MAIN_COMPILE));
    }
}
