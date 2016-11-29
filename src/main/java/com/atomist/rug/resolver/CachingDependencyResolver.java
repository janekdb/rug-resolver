package com.atomist.rug.resolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.atomist.rug.manifest.Manifest;
import com.atomist.rug.resolver.ArtifactDescriptor.Scope;

public class CachingDependencyResolver implements DependencyResolver {

    // Name of the resolver plan
    private static final String LOCAL_PLAN_FILE_NAME = "_local_resolver.plan";
    private static final String PLAN_FILE_NAME = "_resolver.plan";
    // Default timeout 1 hour
    private static final long TIMEOUT = 1000 * 60 * 60;

    private DependencyResolver delegate;
    private String repoHome;

    public CachingDependencyResolver(DependencyResolver delegate) {
        this(delegate, System.getProperty("user.home"));
    }

    public CachingDependencyResolver(DependencyResolver delegate, String repoHome) {
        this.delegate = delegate;
        this.repoHome = repoHome;
    }

    @Override
    public List<ArtifactDescriptor> resolveDirectDependencies(ArtifactDescriptor artifact)
            throws DependencyResolverException {
        return delegate.resolveDirectDependencies(artifact);
    }

    @Override
    public List<ArtifactDescriptor> resolveTransitiveDependencies(ArtifactDescriptor artifact)
            throws DependencyResolverException {

        File artifactRoot = createPlanFile(artifact);
        if (artifactRoot.exists() && !isOutdated(artifact, artifactRoot)) {
            Optional<List<ArtifactDescriptor>> planDependencies = readDependenciesFromPlan(
                    artifactRoot);
            if (planDependencies.isPresent()) {
                return planDependencies.get();
            }
        }

        List<ArtifactDescriptor> dependencies = delegate.resolveTransitiveDependencies(artifact);
        writeDependenciesToPlan(dependencies, artifactRoot);
        return dependencies;
    }

    @Override
    public String resolveVersion(ArtifactDescriptor artifact) throws DependencyResolverException {
        return delegate.resolveVersion(artifact);
    }

    protected boolean isOutdated(ArtifactDescriptor artifact, File file) {
        if (artifact instanceof LocalArtifactDescriptor) {
            File manifest = new File(new File(artifact.uri()),
                    Manifest.ATOMIST_ROOT + "/" + Manifest.FILE_NAME);
            File packageJson = new File(new File(artifact.uri()),
                    Manifest.ATOMIST_ROOT + "/package.json");
            return manifest.lastModified() > file.lastModified()
                    || packageJson.lastModified() > file.lastModified();
        }
        return System.currentTimeMillis() - file.lastModified() > TIMEOUT;
    }

    private File createPlanFile(ArtifactDescriptor artifact) {
        File repoRoot = new File(repoHome);
        File artifactRoot = new File(repoRoot, artifact.group().replace(".", File.separator)
                + File.separator + artifact.artifact() + File.separator + artifact.version());
        if (!artifactRoot.exists()) {
            artifactRoot.mkdirs();
        }
        if (artifact instanceof LocalArtifactDescriptor) {
            return new File(artifactRoot, LOCAL_PLAN_FILE_NAME);
        }
        else {
            return new File(artifactRoot, PLAN_FILE_NAME);
        }
    }

    private Optional<List<ArtifactDescriptor>> readDependenciesFromPlan(File artifactRoot) {
        List<ArtifactDescriptor> dependencies = null;
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(artifactRoot))) {
            BufferedReader br = new BufferedReader(isr);
            dependencies = new ArrayList<>();
            String line = null;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("#");
                dependencies.add(new DefaultArtifactDescriptor(parts[0], parts[1], parts[2],
                        ArtifactDescriptorFactory.toExtension(parts[3]), Scope.COMPILE,
                        URI.create(parts[4])));
            }
        }
        catch (FileNotFoundException e) {
            // At this time we know the file exists
        }
        catch (IOException e) {
            // Fine, just move on with no plan
        }
        return validateDependenciesFromPlan(Optional.ofNullable(dependencies));
    }

    private Optional<List<ArtifactDescriptor>> validateDependenciesFromPlan(
            Optional<List<ArtifactDescriptor>> dependencies) {
        if (dependencies.isPresent()) {
            Optional<ArtifactDescriptor> missingArtifact = dependencies.get().stream()
                    .filter(d -> !new File(d.uri()).exists()).findAny();
            if (missingArtifact.isPresent()) {
                return Optional.empty();
            }
        }
        return dependencies;
    }

    private void writeDependenciesToPlan(List<ArtifactDescriptor> dependencies, File artifactRoot) {
        try (FileWriter writer = new FileWriter(artifactRoot)) {
            dependencies.forEach(d -> {
                try {
                    writer.write(d.group() + "#" + d.artifact() + "#" + d.version() + "#"
                            + d.extension().toString().toLowerCase() + "#" + d.uri().toString()
                            + "\n");
                }
                catch (IOException e) {
                }
            });
            writer.flush();
        }
        catch (IOException e) {
            // Something went wrong, just delete the plan file
            artifactRoot.delete();
        }
    }

}
