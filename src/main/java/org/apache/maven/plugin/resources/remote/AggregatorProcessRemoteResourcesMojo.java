package org.apache.maven.plugin.resources.remote;

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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;

/**
 * <p>
 * Pull down resourceBundles containing remote resources and process the resources contained inside. When that is done,
 * the resources are injected into the current (in-memory) Maven project, making them available to the process-resources
 * phase.
 * </p>
 * <p>
 * Resources that end in ".vm" are treated as Velocity templates. For those, the ".vm" is stripped off for the final
 * artifact name and it's fed through Velocity to have properties expanded, conditions processed, etc...
 * </p>
 * Resources that don't end in ".vm" are copied "as is".
 */
@Mojo( name = "aggregator-process",
       defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
       aggregator = true,
       threadSafe = true )
public class AggregatorProcessRemoteResourcesMojo
    extends AbstractProcessRemoteResourcesMojo
{
    @Override
    protected Set<Artifact> getProjectArtifacts()
    {
        return mavenSession.getProjects().stream().map( ProjectArtifact::new ).collect( Collectors.toSet() );
    }

    @Override
    protected Set<Artifact> getAllDependencies()
    {
        LinkedHashSet<Artifact> result = new LinkedHashSet<>();
        for ( MavenProject mavenProject : mavenSession.getProjects() )
        {
            result.addAll( mavenProject.getArtifacts() );
        }
        return result;
    }

    @Override
    protected Set<Artifact> getDirectDependencies()
    {
        LinkedHashSet<Artifact> result = new LinkedHashSet<>();
        for ( MavenProject mavenProject : mavenSession.getProjects() )
        {
            result.addAll( mavenProject.getDependencyArtifacts() );
        }
        return result;
    }
}
