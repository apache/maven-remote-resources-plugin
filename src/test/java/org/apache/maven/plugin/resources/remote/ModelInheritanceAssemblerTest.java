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
package org.apache.maven.plugin.resources.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the SCM path normalization in {@link ModelInheritanceAssembler}.
 */
public class ModelInheritanceAssemblerTest {

    private final ModelInheritanceAssembler assembler = new ModelInheritanceAssembler();

    @Test
    public void appendPathPreservesTrailingSlash() {
        assertEquals(
                "http://svn.example.com/repo/",
                assembler.appendPath("http://svn.example.com/repo/", null, null, false));
    }

    @Test
    public void appendPathAppendsChild() {
        assertEquals(
                "http://svn.example.com/repo/child",
                assembler.appendPath("http://svn.example.com/repo", "child", null, true));
    }

    @Test
    public void appendPathCollapsesDotSegment() {
        assertEquals(
                "http://svn.example.com/repo/child",
                assembler.appendPath("http://svn.example.com/repo/./child", null, null, false));
    }

    @Test
    public void appendPathResolvesParentDirectory() {
        assertEquals(
                "http://svn.example.com/repos",
                assembler.appendPath("http://svn.example.com/repos/project/..", null, null, false));
    }

    @Test
    public void appendPathCollapsesRedundantSeparators() {
        assertEquals(
                "http://svn.example.com/repo/child",
                assembler.appendPath("http://svn.example.com/repo//child", null, null, false));
    }

    @Test
    public void appendPathPreservesExcessParentDirectory() {
        assertEquals("../x", assembler.appendPath("a/../../x", null, null, false));
    }

    @Test
    public void appendPathResolvesParentDirectoryInAbsolutePath() {
        assertEquals("/x", assembler.appendPath("/a/../x", null, null, false));
    }

    @Test
    public void appendPathKeepsParentDirectoryAboveRoot() {
        assertEquals("/../x", assembler.appendPath("/../x", null, null, false));
    }

    /**
     * A segment containing {@code ':'} (a port, as common in SVN/git SCM URLs) must normalize
     * identically on every platform.
     */
    @Test
    public void appendPathNormalizesUrlWithPort() {
        assertEquals("http://host:8080/x", assembler.appendPath("http://host:8080/repo/../x", null, null, false));
    }

    @Test
    public void appendPathNormalizesUrlPathWithoutChangingQueryString() {
        assertEquals(
                "http://host/project?p=a/../b",
                assembler.appendPath("http://host/repo/../project?p=a/../b", null, null, false));
    }

    @Test
    public void appendPathAddsChildBeforeQueryString() {
        assertEquals(
                "http://host/repo/child?ref=release",
                assembler.appendPath("http://host/repo?ref=release", "child", null, true));
    }

    @Test
    public void appendPathPreservesFragmentWhenNormalizingUrlPath() {
        assertEquals(
                "http://host/project#section/../heading",
                assembler.appendPath("http://host/repo/../project#section/../heading", null, null, false));
    }

    @Test
    public void appendPathPreservesScpLikeScmUrl() {
        assertEquals(
                "scm:git:git@github.com:org/../repo.git",
                assembler.appendPath("scm:git:git@github.com:org/../repo.git", null, null, false));
    }
}
