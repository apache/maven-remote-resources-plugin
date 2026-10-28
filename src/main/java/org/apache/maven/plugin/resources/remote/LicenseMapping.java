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

/**
 * Maps a license, as dependencies declare it, to an SPDX license ID or expression for <code>$spdx</code> in
 * templates. A mapping applies to a declared license when each of its <code>name</code> and <code>url</code>
 * that is set matches, after the same normalisation as the built-in aliases.
 *
 * @since 3.4.0
 */
public class LicenseMapping {

    /** The SPDX license ID or expression to return, for example <code>BSD-3-Clause</code>. */
    private String spdxId;

    /** The declared license name to match. */
    private String name;

    /** The declared license URL to match. */
    private String url;

    public String getSpdxId() {
        return spdxId;
    }

    public void setSpdxId(String spdxId) {
        this.spdxId = spdxId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "LicenseMapping{spdxId=" + spdxId + ", name=" + name + ", url=" + url + "}";
    }
}
