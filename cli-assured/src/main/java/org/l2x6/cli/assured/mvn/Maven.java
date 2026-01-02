package org.l2x6.cli.assured.mvn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Maven {
    private static final Pattern M2_DIST_DIRS_PATTERN = Pattern.compile("^apache-maven-(.*)-bin$");
    private static final Pattern MAVEN_CORE_PATTERN = Pattern.compile("^maven-core-(.*)\\.jar$");

    private final String version;
    private final Path m2Directory;
    private final Path home;
    private final String downloadUrl;

    public static Maven version(String version) {
        return new Maven(version);
    }

    public static Maven fromMvnw(Path directory) {
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
                return fromDistributionUrl((String)props.get("distributionUrl"));
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find .mvn/wrapper/maven-wrapper.properties in the parent hierarchy of " + directory);
    }

    static Maven fromDistributionUrl(String distributionUrl) {
        final String[] hashes = new String[] {hashString(distributionUrl), md5(distributionUrl)};
        final Path m2Directory = findM2Directory();
        final Path distsDir = m2Directory.resolve("wrapper/dists");
        final Path mavenHome;
        try (Stream<Path> versionDirs = Files.list(distsDir)) {
            mavenHome = versionDirs
                .flatMap(vd -> Stream.of(hashes).map(hd -> distsDir.resolve(vd).resolve(hd)))
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No installation of " + distributionUrl +" found in " + distsDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + distsDir, e);
        }
        final String version = findVersion(mavenHome);
        return new Maven(version, m2Directory, mavenHome, distributionUrl);
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

    private Maven(String version) {
        this.version = version;
        this.m2Directory = findM2Directory();
        this.downloadUrl = defaultDownloadUrl(version);
        this.home = Stream.<Supplier<Path>> of(
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

    private Maven(String version, Path m2Directory, Path home, String downloadUrl) {
        this.version = version;
        this.m2Directory = m2Directory;
        this.home = home;
        this.downloadUrl = downloadUrl;
    }

    public Maven m2Directory(Path m2Directory) {
        return new Maven(version, m2Directory, home, downloadUrl);
    }

    public Maven home(Path home) {
        return new Maven(version, m2Directory, home, downloadUrl);
    }

    public Maven downloadUrl(String downloadUrl) {
        return new Maven(version, m2Directory, home, downloadUrl);
    }

    public String executable() {
        return executablePath()
                .toString();
    }

    public Path executablePath() {
        return home
                .resolve("bin/mvn"
                        + (System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ""));
    }

    public Maven install() {

        if (Files.exists(home)) {
            throw new IllegalStateException(
                    "Cannot download " + downloadUrl + " to " + home + " because it exists already");
        }

        final CompletableFuture<Path> f = new CompletableFuture<>();
        try (final PipedInputStream pipeOut = new PipedInputStream()) {
            Files.createDirectories(home);
            final Thread t = new Thread(() -> {
                try (final PipedOutputStream pipeIn = new PipedOutputStream(pipeOut);
                        InputStream in = new URL(downloadUrl).openStream()) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) >= 0) {
                        pipeIn.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    f.completeExceptionally(e);
                }
                f.complete(home);
            }, "get-" + downloadUrl);

            t.start();

            byte[] buffer = new byte[4096];
            try (ZipInputStream zis = new ZipInputStream(pipeOut)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path newFile = home.resolve(entry.getName());
                    if (!entry.isDirectory()) {
                        Files.createDirectories(newFile.getParent());
                        try (OutputStream fos = Files.newOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not unzip " + downloadUrl + " to " + home, e);
            }
            t.join();
            f.get();
            return this;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Could not unzip " + downloadUrl + " to " + home, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unzip " + downloadUrl + " to " + home, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not unzip " + downloadUrl + " to " + home, e);
        }
    }

    public Maven assertInstalled() {
        if (!Files.isDirectory(home)) {
            throw new AssertionError("Home directory " + home + " of Maven " + version + " does not exist");
        }
        final Path executable = executablePath();
        if (!Files.isRegularFile(executable)) {
            throw new AssertionError("Executable " + executable + " of Maven " + version + " does not exist");
        }
        return this;
    }

    public boolean isInstalled() {
        return Files.isDirectory(home) && Files.isRegularFile(executablePath());
    }

    public Maven installIfNeeded() {
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
}
