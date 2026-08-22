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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
