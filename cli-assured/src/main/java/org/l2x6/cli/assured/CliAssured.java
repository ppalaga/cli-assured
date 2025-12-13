/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * @since  0.0.1
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class CliAssured {
    /**
     * @return a plain {@link Command}
     * @since  0.0.1
     */
    public static Command given() {
        return new Command(null, Collections.emptyList());
    }

    /**
     * @return a {@link Command} with the java exectuable of the current JVM set as
     *         {@link Command#executable(String)}
     * @since  0.0.1
     */
    public static Command java() {
        return new Command(Command.javaExecutable(), Collections.emptyList());
    }

    /**
     * @return a {@link Command} with the specified command set
     * @since  0.0.1
     */
    public static Command command(String executable, String... args) {
        return new Command(executable, Collections.unmodifiableList(new ArrayList<>(Arrays.asList(args))));
    }
}
