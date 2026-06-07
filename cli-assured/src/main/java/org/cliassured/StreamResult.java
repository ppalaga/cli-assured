/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.cliassured;

import java.util.List;
import java.util.stream.Stream;
import org.cliassured.StreamExpectationsSpec.OutputCapture;

/**
 * An information about {@code stdout} or {@code stderr} of the executed command.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @since  0.1.0
 */
public class StreamResult {
    private final long byteCount;
    private final List<String> lines;

    StreamResult(long byteCount, List<String> lines) {
        this.byteCount = byteCount;
        this.lines = lines;
    }

    /**
     * @return                       a {@link Stream} of lines captured from {@code stdout} or {@code stderr} of the
     *                               executed command
     * @throws IllegalStateException if {@link StreamExpectationsSpec#captureAll()} was not called on the associated stream
     * @since                        0.1.0
     */
    public Stream<String> lines() {
        return lines.stream();
    }

    /**
     * @return the number of lines captured from {@code stdout} or {@code stderr} of the executed command
     * @since  0.1.0
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * @return the number of bytes captured from {@code stdout} or {@code stderr} of the executed command
     * @since  0.1.0
     */
    public long byteCount() {
        return byteCount;
    }
}
