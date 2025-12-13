/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.l2x6.cli.assured.OutputConsumer.DevNull;
import org.l2x6.cli.assured.OutputConsumer.OutputAsserts;
import org.l2x6.cli.assured.StreamExpectationsBuilder.ProcessOutput;
import org.l2x6.cli.assured.StreamExpectationsBuilder.StreamExpectations;
import org.l2x6.cli.assured.asserts.ExitCodeAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assertions applicable to an output of a {@link CommandProcess} or its exit code.
 *
 * @since  0.0.1
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class Expectations {
    static final Logger log = LoggerFactory.getLogger(Expectations.class);
    private static final Pattern MATCH_ANY_PATTERN = Pattern.compile(".*");
    private final Command command;
    private final boolean stderrToStdout;
    final Function<InputStream, OutputConsumer> stdout;
    final Function<InputStream, OutputConsumer> stderr;
    final ExitCodeAssert exitCodeAssert;

    Expectations(Command command, boolean stderrToStdout) {
        this.command = command;
        this.stderrToStdout = stderrToStdout;
        this.stdout = in -> new DevNull(in, ProcessOutput.stdout);
        this.stderr = in -> new OutputAsserts(in, StreamExpectations.hasNoLines(ProcessOutput.stderr));
        this.exitCodeAssert = ExitCodeAssert.of(0);
    }

    Expectations(
            Command command,
            Function<InputStream, OutputConsumer> stdout,
            Function<InputStream, OutputConsumer> stderr,
            ExitCodeAssert exitCodeAssert,
            boolean stderrToStdout) {
        this.command = command;
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.exitCodeAssert = Objects.requireNonNull(exitCodeAssert, "exitCodeAssert");
        this.stderrToStdout = stderrToStdout;
    }

    /**
     * @return new {@link StreamExpectationsBuilder}
     * @since  0.0.1
     */
    public StreamExpectationsBuilder stdout() {
        return new StreamExpectationsBuilder(this::stdout, StreamExpectationsBuilder.ProcessOutput.stdout);
    }

    /**
     * @return new {@link StreamExpectationsBuilder}
     * @since  0.0.1
     */
    public StreamExpectationsBuilder stderr() {
        if (stderrToStdout) {
            throw new IllegalStateException(
                    "You cannot set any assertions on stderr while you are redirecting stderr to stdout");
        }
        return new StreamExpectationsBuilder(this::stderr, StreamExpectationsBuilder.ProcessOutput.stderr);
    }

    /**
     * Assert that the process exits with any the given {@code expectedExitCodes}.
     *
     * @param  expectedExitCodes the exit codes to assert
     * @return                   an adjusted copy of this {@link Expectations}
     * @since                    0.0.1
     */
    public Expectations exitCode(int... expectedExitCodes) {
        return new Expectations(command, stdout, stderr, ExitCodeAssert.any(expectedExitCodes), stderrToStdout);
    }

    Expectations stdout(Function<InputStream, OutputConsumer> stdoutAsserts) {
        return new Expectations(command, stdoutAsserts, stderr, exitCodeAssert, stderrToStdout);
    }

    Expectations stderr(Function<InputStream, OutputConsumer> stderrAsserts) {
        return new Expectations(command, stdout, stderrAsserts, exitCodeAssert, stderrToStdout);
    }

    /**
     * Build new {@link Expectations}, pass them to the parent {@link CommandBuilder}
     * and start the command.
     *
     * @return a new {@link CommandProcess}
     * @since  0.0.1
     * @see    #execute()
     */
    public CommandProcess start() {
        return parent().start();
    }

    /**
     * Build new {@link Expectations}, pass them to the parent {@link CommandBuilder},
     * start the {@link CommandProcess} and awaits (potentially indefinitely) its termination.
     * A shorthand for {@link #start()}.{@link CommandProcess#awaitTermination() awaitTermination()}
     *
     * @return a {@link CommandResult}
     * @since  0.0.1
     */
    public CommandResult execute() {
        return parent().execute();
    }

    /**
     * Build new {@link Expectations}, pass them to the parent {@link CommandBuilder} and the {@link CommandProcess}
     * and
     * awaits (potentially indefinitely) its termination at most for the specified
     * duration.
     * A shorthand for {@link #start()}.{@link CommandProcess#awaitTermination(Duration) awaitTermination(Duration)}
     *
     * @param  timeout maximum time to wait for the underlying process to terminate
     *
     * @return         a {@link CommandResult}
     * @since          0.0.1
     */
    public CommandResult execute(Duration timeout) {
        return parent().execute(timeout);
    }

    /**
     * Build new {@link Expectations}, pass them to the parent {@link CommandBuilder} and the {@link CommandProcess}
     * and
     * awaits (potentially indefinitely) its termination at most for the specified
     * timeout in milliseconds.
     * A shorthand for {@link #start()}.{@link CommandProcess#awaitTermination(Duration) awaitTermination(Duration)}
     *
     * @param  timeoutMs maximum time in milliseconds to wait for the underlying process to terminate
     *
     * @return           a {@link CommandResult}
     * @since            0.0.1
     */
    public CommandResult execute(long timeoutMs) {
        return parent().execute(timeoutMs);
    }

    Command parent() {
        return command.expect(this);
    }

}
