package com.atomist.rug.loader;

public class ProjectOperationsLoaderRuntimeException extends ProjectOperationsLoaderException {

    private static final long serialVersionUID = -1788230882200234770L;

    public ProjectOperationsLoaderRuntimeException(String msg, Exception e) {
        super(msg, e);
    }
}
