package org.fogbowcloud.arrebol.execution.docker.helpers;

import org.apache.http.client.methods.HttpPost;
import org.fogbowcloud.arrebol.execution.docker.DockerCommandExecutor;
import org.fogbowcloud.arrebol.execution.docker.request.HttpWrapper;

public class DockerRequestHelper {
    private DockerCommandExecutor dockerCommandExecutor;

    public DockerRequestHelper(DockerCommandExecutor dockerCommandExecutor) {
        this.dockerCommandExecutor = dockerCommandExecutor;
    }

    public void pullImage(String apiAddress, String imageId) throws Exception {
        final String endpoint =
            String.format("%s/images/create?fromImage=%s:latest", apiAddress, imageId);
        HttpWrapper.doRequest(HttpPost.METHOD_NAME, endpoint);
    }
}