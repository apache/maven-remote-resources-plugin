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

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpdxLicensesTest {

    private final SpdxLicenses spdx = new SpdxLicenses();

    @Test
    void declaredId() {
        assertEquals("Apache-2.0", spdx.id("Apache-2.0", null));
        assertEquals("Apache-2.0", spdx.id("apache-2.0", null));
        assertEquals("GPL-2.0", spdx.id("GPL-2.0", null));
    }

    @Test
    void expression() {
        assertEquals("(Apache-2.0 OR EPL-2.0)", spdx.id("(Apache-2.0 or epl-2.0)", null));
        assertEquals(
                "GPL-2.0-only WITH Classpath-exception-2.0",
                spdx.id("GPL-2.0-only with Classpath-exception-2.0", null));
        assertNull(spdx.id("Apache-2.0 OR Unknown-1.0", null));
        assertNull(spdx.id("Apache-2.0 MIT", null));
    }

    @Test
    void name() {
        assertEquals("Apache-2.0", spdx.id("The Apache Software License, Version 2.0", null));
        assertEquals("Apache-2.0", spdx.id("  apache license version 2.0 ", null));
        assertEquals("MIT", spdx.id("The MIT License (MIT)", null));
        assertEquals("BSD-3-Clause", spdx.id("Eclipse Distribution License - v 1.0", null));
        assertEquals("GPL-2.0-only WITH Classpath-exception-2.0", spdx.id("GPL2 w/ CPE", null));
    }

    @Test
    void url() {
        assertEquals("Apache-2.0", spdx.id(null, "http://www.apache.org/licenses/LICENSE-2.0.txt"));
        assertEquals("Apache-2.0", spdx.id("", "https://apache.org/licenses/LICENSE-2.0/"));
        assertEquals("EPL-2.0", spdx.id("Some name", "https://www.eclipse.org/legal/epl-v20.html"));
    }

    @Test
    void urlMustNameTheLicense() {
        assertEquals("Apache-2.0", spdx.id(null, "http://repository.jboss.org/licenses/apache-2.0.txt"));
        assertNull(spdx.id("BSD", "https://asm.ow2.io/license.html"));
        assertNull(spdx.id(null, "https://jsoup.org/license"));
    }

    @Test
    void idAtEndOfUrl() {
        assertEquals("MIT", spdx.id(null, "https://spdx.org/licenses/MIT.txt"));
        assertEquals("BSD-2-Clause", spdx.id(null, "https://opensource.org/license/bsd-2-clause"));
        assertEquals("MIT-0", spdx.id(null, "https://spdx.org/licenses/MIT-0.html"));
        assertNull(spdx.id(null, "https://opensource.org/licenses/bsd-license.php"));
    }

    @Test
    void nameAndUrlDisagree() {
        assertNull(spdx.id("MIT License", "https://www.apache.org/licenses/LICENSE-2.0"));
    }

    @Test
    void ambiguousGnuDeclarationsMatchNothing() {
        assertNull(spdx.id("GNU Lesser General Public License", "https://www.gnu.org/licenses/lgpl.html"));
        assertNull(spdx.id(null, "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"));
    }

    @Test
    void unknown() {
        assertNull(spdx.id("Public Domain", null));
        assertNull(spdx.id(null, null));
        assertNull(spdx.id((License) null));
    }

    @Test
    void severalLicensesAreAChoice() {
        assertEquals(
                "CDDL-1.1 OR (GPL-2.0-only WITH Classpath-exception-2.0)",
                spdx.expression(project(
                        license("CDDL 1.1", "https://glassfish.java.net/public/CDDL+GPL_1_1.html"),
                        license("GPL2 w/ CPE", "https://glassfish.java.net/public/CDDL+GPL_1_1.html"))));
        assertEquals(
                "Apache-2.0 OR (MIT OR EPL-2.0)",
                spdx.expression(project(license("Apache 2.0", null), license("MIT OR EPL-2.0", null))));
    }

    @Test
    void projectExpression() {
        assertEquals("MIT", spdx.expression(project(license("MIT", null), license("The MIT License", null))));
        assertNull(spdx.expression(project(license("MIT", null), license("Public Domain", null))));
        assertNull(spdx.expression(project()));
        assertNull(spdx.expression(null));
    }

    private static License license(String name, String url) {
        License license = new License();
        license.setName(name);
        license.setUrl(url);
        return license;
    }

    private static MavenProject project(License... licenses) {
        Model model = new Model();
        for (License license : licenses) {
            model.addLicense(license);
        }
        return new MavenProject(model);
    }

    @Test
    void license() {
        License license = new License();
        license.setName("Apache License, Version 2.0");
        license.setUrl("https://www.apache.org/licenses/LICENSE-2.0");
        assertEquals("Apache-2.0", spdx.id(license));
    }
}
