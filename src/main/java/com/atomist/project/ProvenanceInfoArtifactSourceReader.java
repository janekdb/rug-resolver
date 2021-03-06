package com.atomist.project;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;

import com.atomist.rug.git.RepositoryDetails;
import com.atomist.rug.git.RepositoryDetailsReader;
import com.atomist.rug.manifest.Manifest;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.file.FileSystemArtifactSourceIdentifier;

import scala.Option;

public abstract class ProvenanceInfoArtifactSourceReader {

    @SuppressWarnings("unchecked")
    public static Optional<ProvenanceInfo> read(ArtifactSource source) {
        String repo = null;
        String branch = null;
        String sha = null;

        Option<FileArtifact> manifest = source.findFile(".atomist/" + Manifest.FILE_NAME);

        if (manifest.isDefined()) {
            Yaml yaml = new Yaml();
            Map<String, Object> allDocuments = new HashMap<>();
            Iterable<?> iterator = yaml.loadAll(manifest.get().content());
            iterator.forEach(d -> {
                Map<String, Object> document = (Map<String, Object>) d;
                allDocuments.putAll(document);
            });
            if (allDocuments.containsKey("repo")) {
                repo = (String) allDocuments.get("repo");
            }
            if (allDocuments.containsKey("branch")) {
                branch = (String) allDocuments.get("branch");
            }
            if (allDocuments.containsKey("sha")) {
                // Sometimes the sha gets written out as number
                sha = allDocuments.get("sha").toString();
            }
        }
        if (repo != null && branch != null && sha != null) {
            return Optional.of(new SimpleProvenanceInfo(repo, branch, sha));
        }

        // In case of local project we are working on, there is still a chance to obtain the
        // provenance info
        if (source.id() instanceof FileSystemArtifactSourceIdentifier) {
            File rootFile = ((FileSystemArtifactSourceIdentifier) source.id()).rootFile();
            try {
                Optional<RepositoryDetails> detailsOptional = RepositoryDetailsReader
                        .read(rootFile);
                if (detailsOptional.isPresent()) {
                    RepositoryDetails details = detailsOptional.get();
                    return Optional.of(new SimpleProvenanceInfo(details.repo(), details.branch(),
                            details.sha()));
                }
            }
            catch (IOException e) {
            }
        }

        return Optional.empty();

    }

}
