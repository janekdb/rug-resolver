package com.atomist.rug.loader;

import com.atomist.project.archive.Handlers;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.runtime.js.interop.JavaScriptHandlerContext;
import com.atomist.source.ArtifactSource;

public interface HandlerOperationsLoader {

    Handlers loadHandlers(String teamId, ArtifactDescriptor artifact, ArtifactSource source, JavaScriptHandlerContext ctx)
            throws ProjectOperationsLoaderException;

    Handlers loadHandlers(String teamId, String group, String artifact, String version,
            ArtifactSource source, JavaScriptHandlerContext ctx)
            throws ProjectOperationsLoaderException;

    Handlers loadHandlers(String teamId, ArtifactDescriptor artifact,JavaScriptHandlerContext ctx) throws ProjectOperationsLoaderException;

    Handlers loadHandlers(String teamId, String group, String artifact, String version,
                          JavaScriptHandlerContext ctx)
            throws ProjectOperationsLoaderException;
}
