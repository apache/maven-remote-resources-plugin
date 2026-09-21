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
package org.apache.maven.plugin.resources.remote.it.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.codehaus.plexus.util.FileUtils;

/**
 * Minimal test runner built on top of {@code maven-executor}, replacing the deprecated Verifier class this
 * module used to depend on. Only the subset of behaviour that the integration tests in this module rely on is
 * implemented.
 */
public class MavenRunner {

    private final File basedir;
    private final List<String> cliArguments = new ArrayList<>();
    private String localRepo;
    private String logFileName = "log.txt";
    private boolean autoclean = true;
    private ExecutorResult result;

    public MavenRunner(File basedir) {
        this.basedir = basedir;
    }

    public void setLocalRepo(String localRepo) {
        this.localRepo = localRepo;
    }

    public void setLogFileName(String logFileName) {
        this.logFileName = logFileName;
    }

    public void setAutoclean(boolean autoclean) {
        this.autoclean = autoclean;
    }

    public void addCliArgument(String cliArgument) {
        cliArguments.add(cliArgument);
    }

    public void execute() {
        List<String> args = new ArrayList<>();
        if (localRepo != null) {
            args.add("-Dmaven.repo.local=" + localRepo);
        }
        if (autoclean) {
            args.add("clean");
        }
        args.addAll(cliArguments);

        try (ExecutorHelper executorHelper = ExecutorHelper.forMavenInstallation(
                ExecutorRequest.discoverInstallationDirectory(), ExecutorHelper.Mode.AUTO)) {
            ExecutorRequest request = ExecutorRequest.mavenBuilder()
                    .cwd(basedir.toPath())
                    .arguments(args.toArray(new String[0]))
                    .grabOutputAsString(true)
                    .build();
            result = executorHelper.execute(request);
        }

        try {
            FileUtils.fileWrite(new File(basedir, logFileName), StandardCharsets.UTF_8.name(), stdOut() + stdErr());
        } catch (IOException e) {
            throw new ExecutorException("Failed to write log file", e);
        }
    }

    public void verifyErrorFreeLog() {
        if (result == null || !result.success() || stdOut().contains("[ERROR]") || stdErr().contains("[ERROR]")) {
            throw new AssertionError("Error in execution: exit code = " + (result != null ? result.exitCode() : "n/a")
                    + "\n" + stdOut() + stdErr());
        }
    }

    public void verifyTextInLog(String text) {
        if (!stdOut().contains(text) && !stdErr().contains(text)) {
            throw new AssertionError("Text not found in log: " + text);
        }
    }

    public void deleteArtifacts(String groupId) throws IOException {
        String repo = localRepo != null ? localRepo : System.getProperty("maven.repo.local", userHomeRepo());
        FileUtils.deleteDirectory(new File(repo, groupId.replace('.', '/')));
    }

    private static String userHomeRepo() {
        return System.getProperty("user.home") + "/.m2/repository";
    }

    private String stdOut() {
        return result != null ? result.stdOutString().orElse("") : "";
    }

    private String stdErr() {
        return result != null ? result.stdErrString().orElse("") : "";
    }
}
