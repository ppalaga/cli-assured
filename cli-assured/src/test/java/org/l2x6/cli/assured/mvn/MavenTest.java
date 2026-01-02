/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured.mvn;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MavenTest {
    @Test
    void hashString() {
        Assertions
                .assertThat(
                        Mvn.hashString(
                                "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"))
                .isEqualTo("a2d47e15");
    }

    @Test
    void md5() {
        Assertions
                .assertThat(
                        Mvn.md5(
                                "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"))
                .isEqualTo("439sdfsg2nbdob9ciift5h5nse");
    }
}
