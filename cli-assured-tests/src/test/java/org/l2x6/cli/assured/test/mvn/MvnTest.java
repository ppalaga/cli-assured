/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured.test.mvn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.cli.assured.CliAssured;
import org.l2x6.cli.assured.mvn.Mvn;

public class MvnTest {
    @Test
    void installIfNeeded() throws IOException {
        final Path m2Dir = Paths.get("target/m2-" + UUID.randomUUID());
        Mvn mvn = Mvn.version("3.9.11")
                .m2Directory(m2Dir);
        Assertions.assertThat(mvn.isInstalled()).isFalse();

        Files.createDirectories(m2Dir);
        Assertions.assertThat(mvn.isInstalled()).isFalse();

        Assertions.assertThatThrownBy(mvn::assertInstalled)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Maven 3.9.11 is not installed ");

        mvn = mvn.installIfNeeded();

        Assertions.assertThat(mvn.isInstalled()).isTrue();

        Assertions.assertThatThrownBy(mvn::install)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("because it exists already");

        CliAssured.command(mvn.executable(), "-v")
                .then()
                .stdout()
                .hasLines("Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)")
                .execute()
                .assertSuccess();

    }
}
