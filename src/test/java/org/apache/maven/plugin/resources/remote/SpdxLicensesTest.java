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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        assertEquals("LicenseRef-Widget", spdx.id("LicenseRef-Widget", null));
    }

    @Test
    void expression() {
        assertEquals("(Apache-2.0 OR EPL-2.0)", spdx.id("(Apache-2.0 or epl-2.0)", null));
        assertEquals(
                "GPL-2.0-only WITH Classpath-exception-2.0",
                spdx.id("GPL-2.0-only with Classpath-exception-2.0", null));
        assertNull(spdx.id("Apache-2.0 OR Unknown-1.0", null));
        assertNull(spdx.id("Apache-2.0 MIT", null));
        assertNull(spdx.id("OR", null));
        assertNull(spdx.id("Apache-2.0 OR OR MIT", null));
        assertNull(spdx.id("(Apache-2.0 OR MIT", null));
        assertNull(spdx.id("Apache-2.0 OR MIT)", null));
        assertNull(spdx.id("MIT WITH", null));
        assertEquals("LicenseRef-Widget OR MIT", spdx.id("LicenseRef-Widget or MIT", null));
        assertEquals("(MIT OR Apache-2.0) AND BSD-3-Clause", spdx.id("( mit OR apache-2.0 ) and bsd-3-clause", null));
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

    @Test
    void groupsListEachLicenseOnce() {
        MavenProject a = project(license("Apache License, Version 2.0", null));
        MavenProject b = project(license("The Apache Software License, Version 2.0", null));
        MavenProject c = project(license("CDDL 1.1", null), license("GPL2 w/ CPE", null));
        MavenProject d = project(license("Public Domain", null));
        MavenProject e = project(license("Apache-2.0 OR MIT", null));
        List<MavenProject> projects = Arrays.asList(a, b, c, d, e);

        Map<String, List<MavenProject>> groups = spdx.group(projects);
        assertEquals(
                Arrays.asList("Apache-2.0", "CDDL-1.1"),
                Arrays.asList(groups.keySet().toArray()));
        assertEquals(Arrays.asList(a, b, e), groups.get("Apache-2.0"));
        assertEquals(Arrays.asList(c), groups.get("CDDL-1.1"));
        assertEquals(
                Arrays.asList("Apache-2.0", "CDDL-1.1"),
                Arrays.asList(spdx.ids(projects).toArray()));
        assertEquals(Arrays.asList(d), spdx.unresolved(projects));
    }

    @Test
    void preferredSkipsUnresolvedChoices() {
        // javassist 3.30.2-GA and rocksdbjni 9.7.3 as their POMs declare them
        assertEquals(
                "Apache-2.0",
                spdx.preferred(project(
                        license("MPL 1.1", "https://www.mozilla.org/en-US/MPL/1.1/"),
                        license("LGPL 2.1", "https://www.gnu.org/licenses/lgpl-2.1.html"),
                        license("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"))));
        assertEquals(
                "Apache-2.0",
                spdx.preferred(project(
                        license("Apache License 2.0", "http://www.apache.org/licenses/LICENSE-2.0.html"),
                        license("GNU General Public License, version 2", "http://www.gnu.org/licenses/gpl-2.0.html"))));
        assertNull(spdx.preferred(project(license("Public Domain", null))));
        assertNull(spdx.preferred(project()));
        assertNull(spdx.preferred(null));
    }

    @Test
    void preferredFollowsCategory() {
        assertEquals("MPL-1.1", spdx.preferred(project(license("LGPL-2.1-only", null), license("MPL 1.1", null))));
        assertEquals("Apache-2.0", spdx.preferred(project(license("EPL-2.0 OR Apache-2.0", null))));
        assertEquals("MIT", spdx.preferred(project(license("(MIT OR Apache-2.0)", null))));
        assertEquals("GPL-2.0-only", spdx.preferred(project(license("GPL-2.0-only", null))));
        assertEquals(
                "LicenseRef-Widget",
                spdx.preferred(project(license("GPL-3.0-only", null), license("LicenseRef-Widget", null))));
        assertEquals(
                "GPL-2.0-only WITH Classpath-exception-2.0",
                spdx.preferred(project(license("GPL-3.0-only", null), license("GPL2 w/ CPE", null))));
    }

    @Test
    void category() {
        assertEquals("A", spdx.category("Apache-2.0"));
        assertEquals("B", spdx.category("EPL-2.0"));
        assertEquals("X", spdx.category("LGPL-2.1-or-later"));
        assertEquals("B", spdx.category("GPL-2.0-only WITH Classpath-exception-2.0"));
        assertEquals("X", spdx.category("GPL-3.0-only WITH Classpath-exception-2.0"));
        assertEquals("A", spdx.category("(MIT OR GPL-2.0-only)"));
        assertEquals("B", spdx.category("MIT AND EPL-2.0"));
        assertNull(spdx.category("MIT AND Zlib"));
        assertNull(spdx.category("Zlib"));
        assertNull(spdx.category(null));
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
    void mappings() {
        SpdxLicenses mapped = new SpdxLicenses(Arrays.asList(
                mapping("BSD-3-Clause", "BSD", "https://example.org/widget/LICENSE"),
                mapping("LicenseRef-Bouncy-Castle", "Bouncy Castle Licence", null),
                mapping("Apache-2.0 OR MIT", null, "https://example.com/LICENSE")));
        assertEquals("BSD-3-Clause", mapped.id("bsd", "http://www.example.org/widget/LICENSE.txt"));
        assertNull(mapped.id("BSD", null));
        assertNull(mapped.id(null, "https://example.org/widget/LICENSE"));
        assertEquals("LicenseRef-Bouncy-Castle", mapped.id("Bouncy Castle Licence", "https://example.org/"));
        assertEquals("Apache-2.0 OR MIT", mapped.id("Apache 2.0", "https://example.com/LICENSE.txt"));
        assertEquals("MIT", mapped.id("MIT", null));
    }

    @Test
    void mappingIds() {
        assertEquals("Zlib", spdx.canonicalMappingId("Zlib"));
        assertEquals("Apache-2.0 OR MIT", spdx.canonicalMappingId(" apache-2.0 or mit "));
        assertEquals("LicenseRef-Widget", spdx.canonicalMappingId("LicenseRef-Widget"));
        assertNull(spdx.canonicalMappingId("Apache License 2.0"));
        assertNull(spdx.canonicalMappingId("OR"));
        assertNull(spdx.canonicalMappingId(""));
    }

    private static LicenseMapping mapping(String spdxId, String name, String url) {
        LicenseMapping mapping = new LicenseMapping();
        mapping.setSpdxId(spdxId);
        mapping.setName(name);
        mapping.setUrl(url);
        return mapping;
    }

    @Test
    void license() {
        License license = new License();
        license.setName("Apache License, Version 2.0");
        license.setUrl("https://www.apache.org/licenses/LICENSE-2.0");
        assertEquals("Apache-2.0", spdx.id(license));
    }
}
