package com.atomist.rug.loader;

import com.atomist.project.archive.Operations;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface ProjectOperationsLoader {

    Operations load(ArtifactDescriptor artifact, ArtifactSource source)
            throws ProjectOperationsLoaderException;

    Operations load(String groug, String artifact, String version, ArtifactSource source)
            throws ProjectOperationsLoaderException;

    Operations load(ArtifactDescriptor artifact)
            throws ProjectOperationsLoaderException;
    
    Operations load(String groug, String artifact, String version)
            throws ProjectOperationsLoaderException;

}