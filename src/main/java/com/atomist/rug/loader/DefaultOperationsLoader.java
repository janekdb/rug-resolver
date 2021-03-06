package com.atomist.rug.loader;

import static scala.collection.JavaConverters.asJavaCollectionConverter;
import static scala.collection.JavaConverters.asScalaBufferConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.atomist.project.Executor;
import com.atomist.project.ProjectOperation;
import com.atomist.project.archive.DefaultAtomistConfig$;
import com.atomist.project.archive.Operations;
import com.atomist.project.archive.ProjectOperationArchiveReader;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.project.review.ProjectReviewer;
import com.atomist.rug.EmptyRugFunctionRegistry;
import com.atomist.rug.RugRuntimeException;
import com.atomist.rug.kind.DefaultTypeRegistry$;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptor.Extension;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;
import com.atomist.rug.runtime.rugdsl.DefaultEvaluator;
import com.atomist.source.ArtifactSource;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;
import com.atomist.source.file.ZipFileArtifactSourceReader;
import com.atomist.source.file.ZipFileInput;

import scala.Option;

public class DefaultOperationsLoader implements OperationsLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOperationsLoader.class);

    private final DependencyResolver resolver;

    public DefaultOperationsLoader(DependencyResolver resolver) {
        this.resolver = resolver;
    }
    
    @Override
    public Operations load(ArtifactDescriptor artifact) throws OperationsLoaderException {
        return load(artifact, null);
    }

    @Override
    public Operations load(String group, String artifact, String version)
            throws OperationsLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version, Extension.ZIP));
    }

    public final Operations load(String group, String artifact, String version,
            ArtifactSource source) throws OperationsLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version, Extension.ZIP), source);
    }

    @Override
    public final Operations load(ArtifactDescriptor artifact, ArtifactSource source)
            throws OperationsLoaderException {

        String version = null;
        List<ArtifactDescriptor> dependencies;

        try {
            version = resolver.resolveVersion(artifact);
            artifact = ArtifactDescriptorFactory.copyFrom(artifact, version);
            dependencies = resolver.resolveTransitiveDependencies(artifact);
        }
        catch (DependencyResolverException e) {
            throw new OperationsLoaderException(String.format(
                    "Failed to resolveTransitiveDependencies dependencies for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version), e);
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Loading operations into Rug runtime for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version));
        }

        dependencies = postProcessArfifactDescriptors(artifact, dependencies);

        List<ProjectOperation> otherOperations = new ArrayList<>();
        ProjectOperationArchiveReader reader = operationsReader();

        Operations operations = null;
        for (ArtifactDescriptor ad : dependencies) {
            if (ad.match(artifact.group(), artifact.artifact(), artifact.version(),
                    Extension.ZIP)) {
                // Make sure to load the ArtifactSource if it hasn't been provided
                if (source == null) {
                    source = createArtifactSource(ad);
                }
                
                operations = loadArtifact(ad, source, reader, otherOperations);
            }
            else {
                ArtifactSource artifactSource = createArtifactSource(ad);
                otherOperations.addAll(asJavaCollectionConverter(
                        loadArtifact(ad, artifactSource, reader, otherOperations).allOperations())
                                .asJavaCollection());
            }
        }

        if (operations == null) {
            operations = new Operations(
                    asScalaBufferConverter(Collections.<ProjectGenerator> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<ProjectEditor> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<ProjectReviewer> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<Executor> emptyList()).asScala());
        }

        if (LOGGER.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("editors: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.editorNames()).asJavaCollection(), ", "));
            sb.append("] generators: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.generatorNames()).asJavaCollection(),
                    ", "));
            sb.append("] reviewers: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.reviewerNames()).asJavaCollection(),
                    ", "));
            sb.append("] executors: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.executorNames()).asJavaCollection(),
                    ", "));
            sb.append("]");
            LOGGER.info(String.format("Loaded operations for %s:%s:%s: %s", artifact.group(),
                    artifact.artifact(), artifact.version(), sb.toString()));
        }

        return postProcess(artifact, operations, source);
    }

    protected DependencyResolver dependencyResolver() {
        return resolver;
    }

    protected List<ArtifactDescriptor> postProcessArfifactDescriptors(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies) {
        return dependencies;
    }

    protected Operations postProcess(ArtifactDescriptor artifact, Operations operations,
            ArtifactSource source) {
        return operations;
    }

    protected ArtifactSource createArtifactSource(ArtifactDescriptor artifact) {
        try {
            File archiveRoot = new File(artifact.uri());
            if (archiveRoot.isFile()) {
                return ZipFileArtifactSourceReader
                        .fromZipSource(new ZipFileInput(new FileInputStream(archiveRoot)));
            }
            else {
                return new FileSystemArtifactSource(
                        new SimpleFileSystemArtifactSourceIdentifier(archiveRoot));
            }
        }
        catch (FileNotFoundException e) {
            LOGGER.warn(String.format("Failed to read Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
        }
        return null;
    }

    protected Operations loadArtifact(ArtifactDescriptor artifact, ArtifactSource source,
            ProjectOperationArchiveReader reader, List<ProjectOperation> otherOperations)
            throws OperationsLoaderException {
        try {
            return reader.findOperations(source,
                    Option.apply(artifact.group() + "." + artifact.artifact()),
                    asScalaBufferConverter(otherOperations).asScala().toList());
        }
        catch (RugRuntimeException e) {
            LOGGER.error(String.format("Failed to load Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
            throw new OperationsLoaderRuntimeException(
                    String.format("Failed to load Rug archive for %s:%s:%s:\n  %s", artifact.group(),
                            artifact.artifact(), artifact.version(), e.getMessage()),
                    e);
        }
    }

    protected ProjectOperationArchiveReader operationsReader() {
        return new ProjectOperationArchiveReader(DefaultAtomistConfig$.MODULE$,
                new DefaultEvaluator(new EmptyRugFunctionRegistry()), DefaultTypeRegistry$.MODULE$);
    }
}
