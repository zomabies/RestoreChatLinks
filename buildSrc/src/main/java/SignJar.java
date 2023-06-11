/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

// Modified from https://github.com/MinecraftForge/ForgeGradle/blob/82a2d9ddc80700a4033e7f214423294a33351609/src/common/java/net/minecraftforge/gradle/common/tasks/SignJar.java
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import groovy.util.MapEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@NonNullApi
public abstract class SignJar extends DefaultTask implements PatternFilterable {
    private final PatternSet patternSet = new PatternSet();
    private boolean saveRefTimestamp;
    private long zipTimestamp = -1L;

    @TaskAction
    public void doTask() throws IOException {
        final Map<String, Entry<byte[], Long>> ignoredStuff = new HashMap<>();
        File input = getInputFile().get().getAsFile();
        File toSign = new File(getTemporaryDir(), input.getName() + ".unsigned.tmp");
        File signed = new File(getTemporaryDir(), input.getName() + ".signed.tmp");
        File output = getOutputFile().get().getAsFile();

        if (getSignedFileName().isPresent()){
            output = new File(output.getParent(), getSignedFileName().get());
        } else {
            output = getOutputFile().get().getAsFile();
        }
        saveRefTimestamp = getRefTimeStampFile().isPresent();

        // load in input jar, and create temp jar
        processInputJar(input, toSign, ignoredStuff);

        // SIGN!
        Map<String, Object> map = new HashMap<>();
        map.put("alias", getAlias().get());
        map.put("storePass", getStorePass().get());
        map.put("jar", toSign.getAbsolutePath());
        map.put("signedJar", signed.getAbsolutePath());

        if (getKeyPass().isPresent())
            map.put("keypass", getKeyPass().get());
        if (getKeyStore().isPresent())
            map.put("keyStore", getKeyStore().get());
        if (getStoreType().isPresent())
            map.put("storetype", getStoreType().get());
        if (getSigFile().isPresent())
            map.put("sigfile", getSigFile().get());

        if (getDigestAlg().isPresent())
            map.put("digestalg", getDigestAlg().get());
        if (getSigAlg().isPresent())
            map.put("sigalg", getSigAlg().get());

        if (getTsa().isPresent())
            map.put("tsaurl", getTsa().get());
        if (getTsaDigestAlg().isPresent())
            map.put("tsadigestalg", getTsaDigestAlg().get());

        if (getVerbose().isPresent())
            map.put("verbose", getVerbose().get());


        getProject().getAnt().invokeMethod("signjar", map);

        // write out
        writeOutputJar(signed, output, ignoredStuff);
    }

    private void processInputJar(File inputJar, File toSign, final Map<String, Entry<byte[], Long>> unsigned) throws IOException {
        final Spec<FileTreeElement> spec = patternSet.getAsSpec();

        toSign.getParentFile().mkdirs();
        final JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(toSign)));

        getProject().zipTree(inputJar).visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails details) {
                try {
                    String path = details.getPath();
                    ZipEntry entry = new ZipEntry(path.endsWith("/") ? path : path + "/");
                    outs.putNextEntry(entry);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public void visitFile(FileVisitDetails details) {
                try {
                    if (saveRefTimestamp && zipTimestamp == -1L && getRefTimeStampFile().get().equals(details.getName())) {
                        zipTimestamp = details.getLastModified();
                    }
                    if (spec.isSatisfiedBy(details)) {
                        ZipEntry entry = new ZipEntry(details.getPath());
                        entry.setTime(details.getLastModified());
                        outs.putNextEntry(entry);
                        details.copyTo(outs);
                        outs.closeEntry();
                    } else {
                        InputStream stream = details.open();
                        unsigned.put(details.getPath(), new MapEntry(ByteStreams.toByteArray(stream), details.getLastModified()));
                        stream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });

        outs.close();
    }

    private void writeOutputJar(File signedJar, File outputJar, Map<String, Entry<byte[], Long>> unsigned) throws IOException {
        outputJar.getParentFile().mkdirs();

        JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar)));

        ZipFile base = new ZipFile(signedJar);
        for (ZipEntry e : Collections.list(base.entries())) {
            if (e.isDirectory()) {
                if (zipTimestamp != -1L) {
                    e.setTime(zipTimestamp);
                }
                outs.putNextEntry(e);

            } else {
                ZipEntry n = new ZipEntry(e.getName());
                if (zipTimestamp != -1L) {
                    n.setTime(zipTimestamp);
                } else {
                    n.setTime(e.getTime());
                }
                outs.putNextEntry(n);
                ByteStreams.copy(base.getInputStream(e), outs);
                outs.closeEntry();
            }
        }
        base.close();

        for (Entry<String, Entry<byte[], Long>> e : unsigned.entrySet()) {
            ZipEntry n = new ZipEntry(e.getKey());
            n.setTime(zipTimestamp != -1L ? zipTimestamp : e.getValue().getValue());
            outs.putNextEntry(n);
            outs.write(e.getValue().getKey());
            outs.closeEntry();
        }

        outs.close();
    }

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getAlias();

    @Input
    public abstract Property<String> getStorePass();

    @Input
    @Optional
    public abstract Property<String> getKeyPass();

    @Input
    @Optional
    public abstract Property<String> getKeyStore();

    @Input
    @Optional
    public abstract Property<String> getStoreType();

    @Input
    @Optional
    public abstract Property<String> getSigFile();

    @Input
    @Optional
    public abstract Property<String> getDigestAlg();

    @Input
    @Optional
    public abstract Property<String> getSigAlg();

    @Input
    @Optional
    public abstract Property<String> getTsa();

    @Input
    @Optional
    public abstract Property<String> getVerbose();

    @Input
    @Optional
    public abstract Property<String> getTsaDigestAlg();

    @Input
    @Optional
    public abstract Property<String> getSignedFileName();

    @Input
    @Optional
    public abstract Property<String> getRefTimeStampFile();

    @Override
    public PatternFilterable exclude(String... arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Iterable<String> arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Closure arg0) {
        return patternSet.exclude(arg0);
    }

    @Internal
    @Override
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Internal
    @Override
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    public PatternFilterable include(String... arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Iterable<String> arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Closure arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> arg0) {
        return patternSet.setExcludes(arg0);
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> arg0) {
        return patternSet.setIncludes(arg0);
    }
}
