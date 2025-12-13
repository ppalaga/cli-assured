/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.l2x6.cli.assured.asserts.ByteCountAssert;
import org.l2x6.cli.assured.asserts.LineAssert;
import org.slf4j.LoggerFactory;

/**
 * A {@link StreamExpectations} builder.
 *
 * @since  0.0.1
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class StreamExpectationsBuilder {

    private final Function<Function<InputStream, OutputConsumer>, Expectations> expectations;
    private final StreamExpectationsBuilder.ProcessOutput stream;

    private final List<LineAssert> asserts;
    private final ByteCountAssert byteCountAssert;
    private final Charset charset;
    private final Supplier<OutputStream> redirect;

    StreamExpectationsBuilder(
            Function<Function<InputStream, OutputConsumer>, Expectations> expectations,
            ProcessOutput stream) {
        this.expectations = expectations;
        this.stream = stream;
        this.asserts = Collections.emptyList();
        this.byteCountAssert = null;
        this.charset = StandardCharsets.UTF_8;
        this.redirect = null;
    }

    StreamExpectationsBuilder(
            Function<Function<InputStream, OutputConsumer>, Expectations> expectations,
            StreamExpectationsBuilder.ProcessOutput stream,
            List<LineAssert> asserts,
            ByteCountAssert byteCountAssert,
            Charset charset,
            Supplier<OutputStream> redirect) {
        this.expectations = expectations;
        this.stream = stream;
        this.asserts = asserts;
        this.byteCountAssert = byteCountAssert;
        this.charset = charset;
        this.redirect = redirect;
    }

    /**
     * Assert that the given {@link LineAssert}s are satisfied.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder linesSatisfy(LineAssert... asserts) {
        return new StreamExpectationsBuilder(expectations, stream, CliAssertUtils.join(this.asserts, asserts), byteCountAssert,
                charset, redirect);
    }

    /**
     * Assert that the given {@link LineAssert}s are satisfied.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder linesSatisfy(Collection<LineAssert> asserts) {
        return new StreamExpectationsBuilder(expectations, stream, CliAssertUtils.join(this.asserts, asserts), byteCountAssert,
                charset, redirect);
    }

    /**
     * Assert that the given lines are present in the underlying output stream among other lines in any order.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder hasLines(String... lines) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLines(Arrays.asList(lines))), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that the given lines are present in the underlying output stream among other lines in any order.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder hasLines(Collection<String> lines) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLines(lines)), byteCountAssert, charset, redirect);
    }

    /**
     * Assert that the given lines are not present in the underlying output stream.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLines(String... lines) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLines(Arrays.asList(lines))), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that the given lines are not present in the underlying output stream.
     *
     * @param  lines the whole lines to look for
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLines(Collection<String> lines) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLines(lines)), byteCountAssert, charset, redirect);
    }

    /**
     * Assert that upon termination of the associated process, the underlying output stream has produced the given
     * number of lines.
     *
     * @param  expectedLineCount
     * @return                   this {@link StreamExpectationsBuilder}
     * @since                    0.0.1
     */
    public StreamExpectationsBuilder hasLineCount(int expectedLineCount) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLineCount(expectedLineCount)), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that upon termination of the associated process, the underlying output stream's number of lines satisfies
     * the given {@link Predicate}.
     *
     * @param  expected    the condition the number of actual lines must satisfy
     * @param  description the description of a failure typically something like
     *                     {@code "Expected number of lines <condition> but found %d lines"}
     * @return             this {@link StreamExpectationsBuilder}
     * @since              0.0.1
     */
    public StreamExpectationsBuilder hasLineCount(Predicate<Integer> expected, String description) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLineCount(expected, description)), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that upon termination of the associated process, the underlying output stream has produced the given number of
     * bytes.
     *
     * @param  expectedByteCount the number of bytes to enforce
     * @return                   this {@link StreamExpectationsBuilder}
     * @since                    0.0.1
     */
    public StreamExpectationsBuilder hasByteCount(long expectedByteCount) {
        return new StreamExpectationsBuilder(expectations, stream, asserts, ByteCountAssert.hasByteCount(expectedByteCount),
                charset, redirect);
    }

    /**
     * Assert that upon termination of the associated process, the underlying output stream's number of produced byted
     * satisfies the given {@link Predicate}.
     *
     * @param  expected    the condition the number of actual bytes must satisfy
     * @param  description the description of a failure, typically something like
     *                     {@code "Expected number of bytes <condition> but found %d bytes"}
     * @return             this {@link StreamExpectationsBuilder}
     * @since              0.0.1
     */
    public StreamExpectationsBuilder hasByteCount(Predicate<Long> expected, String description) {
        return new StreamExpectationsBuilder(expectations, stream, asserts, ByteCountAssert.hasByteCount(expected, description),
                charset, redirect);
    }

    /**
     * Assert that upon termination of the associated process, the underlying output stream has produced zero bytes.
     * An equivalent of {@link #hasByteCount(long) hasByteCount(0)}.
     *
     * @param  expectedLineCount
     * @return                   this {@link StreamExpectationsBuilder}
     * @since                    0.0.1
     */
    public StreamExpectationsBuilder isEmpty() {
        return new StreamExpectationsBuilder(expectations, stream, asserts, ByteCountAssert.hasByteCount(0), charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} are present in the underlying output stream among other
     * lines in any order.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder hasLinesContaining(String... substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesContaining(Arrays.asList(substrings))), byteCountAssert,
                charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} are present in the underlying output stream among other
     * lines in any order.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder hasLinesContaining(Collection<String> substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesContaining(substrings)), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} are not present in the underlying output stream.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesContaining(String... substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesContaining(Arrays.asList(substrings))),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} are not present in the underlying output stream.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesContaining(Collection<String> substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesContaining(substrings)), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} (using case insensitive comparison) are present in the
     * underlying output stream among other
     * lines in any order.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder hasLinesContainingCaseInsensitive(String... substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesContainingCaseInsensitive(
                        Stream.of(substrings).map(s -> s.toLowerCase(Locale.US)).collect(Collectors.toList()), Locale.US)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} (using case insensitive comparison) are present in the
     * underlying output stream among other
     * lines in any order.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder hasLinesContainingCaseInsensitive(Collection<String> substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesContainingCaseInsensitive(
                        substrings.stream().map(s -> s.toLowerCase(Locale.US)).collect(Collectors.toList()), Locale.US)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} (using case insensitive comparison) are not present in the
     * underlying output stream.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesContainingCaseInsensitive(String... substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesContainingCaseInsensitive(
                        Stream.of(substrings).map(s -> s.toLowerCase(Locale.US)).collect(Collectors.toList()), Locale.US)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines containing the given {@code substrings} (using case insensitive comparison) are not present in the
     * underlying output stream.
     *
     * @param  substrings the substrings to look for in the associated output stream
     * @return            this {@link StreamExpectationsBuilder}
     * @since             0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesContainingCaseInsensitive(Collection<String> substrings) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesContainingCaseInsensitive(
                        substrings.stream().map(s -> s.toLowerCase(Locale.US)).collect(Collectors.toList()), Locale.US)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are present in the underlying output stream among other
     * lines in any order.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder hasLinesMatching(Collection<String> regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesMatching(regex)), byteCountAssert, charset, redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are present in the underlying output stream among other
     * lines in any order.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder hasLinesMatching(String... regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesMatching(Arrays.asList(regex))), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are present in the underlying output stream among other
     * lines in any order.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder hasLinesMatching(Pattern... regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.hasLinesMatchingPatterns(Arrays.asList(regex))), byteCountAssert,
                charset, redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are not present in the underlying output stream.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesMatching(Collection<String> regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesMatching(regex)), byteCountAssert, charset,
                redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are not present in the underlying output stream.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesMatching(String... regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesMatching(Arrays.asList(regex))), byteCountAssert,
                charset, redirect);
    }

    /**
     * Assert that lines matching the given regular expressions are not present in the underlying output stream.
     * The regular expression is evaluated using {@link Matcher#find()} rather than {@link Matcher#matches()}
     *
     * @param  regex the regular expressions to look for in the associated output stream
     * @return       this {@link StreamExpectationsBuilder}
     * @since        0.0.1
     */
    public StreamExpectationsBuilder doesNotHaveLinesMatching(Pattern... regex) {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts, LineAssert.doesNotHaveLinesMatchingPatterns(Arrays.asList(regex))),
                byteCountAssert, charset, redirect);
    }

    /**
     * Read the underlying {@link InputStream} using the given {@code charset}
     *
     * @param  charset the character encoding to use when reading the underlying {@link InputStream}
     * @return         this {@link StreamExpectationsBuilder}
     * @since          0.0.1
     */
    public StreamExpectationsBuilder charset(Charset charset) {
        return new StreamExpectationsBuilder(expectations, stream, asserts, byteCountAssert, charset, redirect);
    }

    /**
     * Redirect the output to the given {@code file}.
     *
     * @param  file where to store the output of the underlying command
     * @return      this {@link StreamExpectationsBuilder}
     * @since       0.0.1
     */
    public StreamExpectationsBuilder redirect(Path file) {
        return new StreamExpectationsBuilder(expectations, stream, asserts, byteCountAssert, charset, () -> {
            try {
                return Files.newOutputStream(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not open " + file + " for writing", e);
            }
        });
    }

    /**
     * Redirect the output to the given {@code outputStream}.
     * Note that CLI Assured will not close the given {@code outputStream}.
     * The caller should take care to do so.
     *
     * @param  outputStream where to redirect the output of the underlying command
     * @return              this {@link StreamExpectationsBuilder}
     * @since               0.0.1
     */
    public StreamExpectationsBuilder redirect(OutputStream outputStream) {
        return new StreamExpectationsBuilder(expectations, stream, asserts, byteCountAssert, charset,
                () -> new StreamExpectationsBuilder.NonClosingOut(outputStream));
    }

    /**
     * Log each line of the output at {@code INFO} level using {@code org.l2x6.cli.assured.[stdout|stderr]} logger.
     *
     * @return this {@link StreamExpectationsBuilder}
     * @since  0.0.1
     */
    public StreamExpectationsBuilder log() {
        return new StreamExpectationsBuilder(expectations, stream,
                CliAssertUtils.join(this.asserts,
                        LineAssert.log(LoggerFactory.getLogger("org.l2x6.cli.assured." + stream.name())::info)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Log each line of the output using the given {@code logger}. Note that the {@link Consumer#accept(Object)}
     * method will be called from an output consuming thread.
     *
     * @param  logger a {@link Consumer} to notify about every new line.
     * @return        this {@link StreamExpectationsBuilder}
     * @since         0.0.1
     */
    public StreamExpectationsBuilder log(Consumer<String> logger) {
        return new StreamExpectationsBuilder(expectations, stream, CliAssertUtils.join(this.asserts, LineAssert.log(logger)),
                byteCountAssert, charset, redirect);
    }

    /**
     * Assert that the process exits with any the given {@code expectedExitCodes},
     * build new {@link StreamExpectations} and set it on the parent {@link Expectations}
     *
     * @param  expectedExitCodes the exit codes to assert
     * @return                   the parent {@link Expectations}
     * @since                    0.0.1
     */
    public Expectations exitCode(int... expectedExitCodes) {
        return parent().exitCode(expectedExitCodes);
    }

    /**
     * A shorthand for {@link #parent()}..{@link ExpectationsBuilder#stderr() stderr()}
     *
     * @return a new {@link OutputConsumer.CommandBuilder} to configure assertions for stderr
     * @since  0.0.1
     */
    public StreamExpectationsBuilder stderr() {
        return parent().stderr();
    }

    /**
     * Build new {@link StreamExpectations} from this {@link StreamExpectationsBuilder}, pass it to the parent
     * {@link ExpectationsBuilder}, pass them to the parent {@link CommandBuilder}
     * and start the command.
     *
     * @return a started {@link CommandProcess}
     * @since  0.0.1
     * @see    #execute()
     */
    public CommandProcess start() {
        return parent().start();
    }

    /**
     * Build new {@link StreamExpectations} from this {@link StreamExpectationsBuilder}, pass it to the parent
     * {@link ExpectationsBuilder}, pass them to the parent {@link CommandBuilder},
     * the {@link CommandProcess} and await (potentially indefinitely) its termination.
     * A shorthand for {@link #start()}.{@link CommandProcess#awaitTermination() awaitTermination()}
     *
     * @return a {@link CommandResult}
     * @since  0.0.1
     */
    public CommandResult execute() {
        return parent().execute();
    }

    /**
     * Build new {@link StreamExpectations} from this {@link StreamExpectationsBuilder}, pass it to the parent
     * {@link ExpectationsBuilder}, pass them to the parent {@link CommandBuilder},
     * start the {@link CommandProcess} and await (potentially indefinitely) its termination at most for the specified
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
     * Build new {@link StreamExpectations} from this {@link StreamExpectationsBuilder}, pass it to the parent
     * {@link ExpectationsBuilder}, pass them to the parent {@link CommandBuilder},
     * the {@link CommandProcess} and await (potentially indefinitely) its termination at most for the specified
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

    Function<InputStream, OutputConsumer> build() {
        final StreamExpectations streamExpectations = new StreamExpectations(
                asserts,
                byteCountAssert,
                charset,
                redirect,
                stream);
        return in -> new OutputConsumer.OutputAsserts(in, streamExpectations);
    }

    /**
     * Build new {@link StreamExpectations} from this {@link StreamExpectationsBuilder} and pass it to the parent
     * {@link Expectations}.
     *
     * @return the parent {@link Expectations}
     * @since  0.0.1
     */
    Expectations parent() {
        return expectations.apply(build());
    }

    static class StreamExpectations implements LineAssert {

        private final List<LineAssert> lineAsserts;
        private final ByteCountAssert byteCountAssert;
        final Charset charset;
        final Supplier<OutputStream> redirect;
        final StreamExpectationsBuilder.ProcessOutput stream;

        static StreamExpectations hasNoLines(StreamExpectationsBuilder.ProcessOutput stream) {
            return new StreamExpectations(
                    Collections.singletonList(LineAssert.doesNotHaveAnyLines(stream)),
                    null,
                    StandardCharsets.UTF_8,
                    null,
                    stream);
        }

        StreamExpectations(
                List<LineAssert> lineAsserts,
                ByteCountAssert byteCountAssert,
                Charset charset,
                Supplier<OutputStream> redirect,
                StreamExpectationsBuilder.ProcessOutput stream) {
            this.lineAsserts = Objects.requireNonNull(lineAsserts, "lineAsserts");
            this.byteCountAssert = byteCountAssert;
            this.charset = Objects.requireNonNull(charset, "charset");
            this.redirect = redirect;
            this.stream = stream;
        }

        @Override
        public void assertSatisfied() {
            lineAsserts.stream().forEach(LineAssert::assertSatisfied);
        }

        public void assertSatisfied(long byteCount) {
            lineAsserts.stream().forEach(LineAssert::assertSatisfied);
            if (byteCountAssert != null) {
                byteCountAssert.byteCount(byteCount).assertSatisfied();
            }
        }

        @Override
        public StreamExpectations line(String line) {
            lineAsserts.stream().forEach(a -> a.line(line));
            return this;
        }

        public Charset charset() {
            return charset;
        }

        public Supplier<OutputStream> redirect() {
            return redirect;
        }

        public boolean hasLineAsserts() {
            return lineAsserts.size() > 0;
        }
    }

    static class NonClosingOut extends FilterOutputStream {

        public NonClosingOut(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            /* The caller is responsible for closing */
        }
    }

    public static enum ProcessOutput {
        stdout, stderr
    }

}
