/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 CLI Assured contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.cli.assured.mvn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.slf4j.LoggerFactory;

public class Mvn {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Mvn.class);
    private static final Pattern MAVEN_CORE_PATTERN = Pattern.compile("^maven-core-(.*)\\.jar$");
    private static final int BUFFER_SIZE = 8192;

    private final String version;
    private final Path m2Directory;
    private final Path home;
    private final String downloadUrl;

    public static Mvn version(String version) {
        return new Mvn(version);
    }

    public static Mvn fromMvnw(Path directory) {
        Path dir = directory;
        while (dir != null) {
            final Path wrapperProps = dir.resolve(".mvn/wrapper/maven-wrapper.properties");
            if (Files.isRegularFile(wrapperProps)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(wrapperProps)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read " + wrapperProps, e);
                }
                return fromDistributionUrl((String) props.get("distributionUrl"));
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Could not find .mvn/wrapper/maven-wrapper.properties in the parent hierarchy of " + directory);
    }

    static Mvn fromDistributionUrl(String distributionUrl) {
        final String[] hashes = new String[] { hashString(distributionUrl), md5(distributionUrl) };
        final Path m2Directory = findM2Directory();
        final Path distsDir = m2Directory.resolve("wrapper/dists");
        final Path mavenHome;
        try (Stream<Path> versionDirs = Files.list(distsDir)) {
            mavenHome = versionDirs
                    .flatMap(vd -> Stream.of(hashes).map(hd -> distsDir.resolve(vd).resolve(hd)))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("No installation of " + distributionUrl + " found in " + distsDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + distsDir, e);
        }
        final String version = findVersion(mavenHome);
        return new Mvn(version, m2Directory, mavenHome, distributionUrl);
    }

    static String findVersion(Path mavenHome) {
        final Path libDir = mavenHome.resolve("lib");
        try (Stream<Path> libs = Files.list(libDir)) {
            return libs
                    .map(p -> MAVEN_CORE_PATTERN.matcher(p.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find maven-core-*.jar in " + libDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + libDir, e);
        }
    }

    private Mvn(String version) {
        this.version = version;
        this.m2Directory = findM2Directory();
        this.downloadUrl = defaultDownloadUrl(version);
        this.home = null;
    }

    static Path findDefaultHome(String version, Path m2Directory, String downloadUrl) {
        return Stream.<Supplier<Path>> of(
                () -> m2Directory.resolve("wrapper/dists/apache-maven-" + version),
                () -> m2Directory.resolve("wrapper/dists/apache-maven-" + version + "-bin"))
                .map(Supplier::get)
                .filter(Files::isDirectory)
                .flatMap(versionDir -> hashDirs(versionDir, downloadUrl))
                .map(Supplier::get)
                .filter(Files::isDirectory)
                .findFirst()
                .orElseGet(() -> m2Directory.resolve("wrapper/dists/apache-maven-" + version + "/" + hashString(downloadUrl)));
    }

    static Stream<Supplier<Path>> hashDirs(Path versionDir, String downloadUrl) {
        return Stream.<Supplier<Path>> of(
                () -> versionDir.resolve(hashString(downloadUrl)),
                () -> versionDir.resolve(md5(downloadUrl)) // older wrapper versions
        );
    }

    static Path findM2Directory() {
        final String muh = System.getenv("MAVEN_USER_HOME");
        if (muh != null) {
            return Paths.get(muh);
        }
        return Paths.get(System.getProperty("user.home") + "/.m2");
    }

    private Mvn(String version, Path m2Directory, Path home, String downloadUrl) {
        this.version = version;
        this.m2Directory = m2Directory;
        this.home = home;
        this.downloadUrl = downloadUrl;
    }

    public Mvn m2Directory(Path m2Directory) {
        return new Mvn(version, m2Directory, home, downloadUrl);
    }

    public Mvn home(Path home) {
        return new Mvn(version, m2Directory, home, downloadUrl);
    }

    public Path home() {
        return home != null ? home : findDefaultHome(version, m2Directory, downloadUrl);
    }

    public Mvn downloadUrl(String downloadUrl) {
        return new Mvn(version, m2Directory, home, downloadUrl);
    }

    public String executable() {
        return executablePath()
                .toString();
    }

    public Path executablePath() {
        return home()
                .resolve("bin/mvn"
                        + (System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ""));
    }

    public Mvn install() {
        final Path home = home();
        if (Files.exists(home)) {
            throw new IllegalStateException(
                    "Cannot download " + downloadUrl + " to " + home + " because it exists already");
        }
        log.info("Downloading " + downloadUrl + " to " + home);

        try {
            Files.createDirectories(home);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + home, e);
        }
        final Path localFile = home.resolve(UUID.randomUUID() + ".zip");
        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, localFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not download " + downloadUrl + " to " + home, e);
        }
        final String actualSha512 = sha512(localFile);
        final String expectedSha512 = dowloadText(downloadUrl + ".sha512", 512);

        if (!actualSha512.equals(expectedSha512)) {
            throw new AssertionError("Could not verify " + localFile + " downloaded from " + downloadUrl + ": expected SHA-512 "
                    + expectedSha512 + " but found " + actualSha512);
        }

        try (ZipFile zip = new ZipFile(localFile.toFile())) {
            Enumeration<ZipEntry> entries = zip.entries();
            final byte[] buff = new byte[BUFFER_SIZE];
            ZipEntry entry;
            int fileCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path entryPath = Paths.get(entry.getName());
                    final int cnt = entryPath.getNameCount();
                    entryPath = entryPath.subpath(1, cnt);
                    Path newFile = home.resolve(entryPath).normalize();
                    if (!newFile.startsWith(home)) {
                        throw new AssertionError("Zip entry " + newFile + " attempted to write outside of " + home);
                    }
                    log.debug("Unpacking " + newFile);
                    Files.createDirectories(newFile.getParent());
                    try (OutputStream fos = Files.newOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buff)) >= 0) {
                            fos.write(buff, 0, len);
                        }
                    }
                    fileCount++;
                }
                zis.closeEntry();
            }
            log.info("Unpacked {} files to {}", fileCount, home);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unzip " + downloadUrl + " to " + home, e);
        }
        try {
            Files.delete(localFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + localFile, e);
        }
        return new Mvn(version, m2Directory, home, downloadUrl);
    }

    static String dowloadText(String url, int expectedByteSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expectedByteSize);
        try (InputStream in = new URL(url).openStream()) {
            final byte[] buff = new byte[Math.min(expectedByteSize, BUFFER_SIZE)];
            int bytesRead;
            while ((bytesRead = in.read(buff)) >= 0) {
                out.write(buff, 0, bytesRead);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not download " + url, e);
        }
    }

    static String sha512(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not compute SHA-512 has for " + file, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compute SHA-512 has for " + file, e);
        }
    }

    public Mvn assertInstalled() {
        final Path home = home();
        if (!Files.isDirectory(home)) {
            throw new AssertionError("Maven " + version + " is not installed in " + home
                    + " (directory does not exist). You may want to set Mvn.home(Path) or call Mvn.installIfNeeded()");
        }
        final Path executable = executablePath();
        if (!Files.isRegularFile(executable)) {
            throw new AssertionError("Maven " + version + " is not installed in " + home
                    + " (bin/mvn[.cmd] does not exist). You may want to set Mvn.home(Path) or call Mvn.installIfNeeded()");
        }
        return this;
    }

    public boolean isInstalled() {
        final Path home = home();
        return Files.isDirectory(home) && Files.isRegularFile(executablePath());
    }

    public Mvn installIfNeeded() {
        if (!isInstalled()) {
            return install();
        }
        return this;
    }

    static String defaultDownloadUrl(String version) {
        return "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/" + version + "/apache-maven-" + version
                + "-bin.zip";
    }

    static String md5(String s) {
        if (s == null) {
            s = "";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            messageDigest.update(bytes);
            return new BigInteger(1, messageDigest.digest()).toString(32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String hashString(String s) {
        if (s == null) {
            s = "";
        }
        long h = 0L;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int code = b & 0xFF;
            h = (h * 31 + code) & 0xFFFF_FFFFL;
        }
        return Long.toHexString(h);
    }

    static void applyUnixPermissions(ZipEntry entry, Path path) {
        try {
            int unixMode = entry.getUnixMode();
            if (unixMode <= 0) {
                return;
            }

            Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);

            if ((unixMode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
            if ((unixMode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
            if ((unixMode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);

            if ((unixMode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
            if ((unixMode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
            if ((unixMode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);

            if ((unixMode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
            if ((unixMode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
            if ((unixMode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);

            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem or permissions not supported
        }
    }
}
