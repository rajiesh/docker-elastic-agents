/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cd.go.contrib.elasticagents.docker;

import cd.go.contrib.elasticagents.docker.executors.*;
import cd.go.contrib.elasticagents.docker.requests.*;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cd.go.contrib.elasticagents.docker.Constants.PLUGIN_IDENTIFIER;

@Extension
public class DockerPlugin implements GoPlugin {

    public static final Logger LOG = Logger.getLoggerFor(DockerPlugin.class);

    private Map<String, DockerContainers> clusterSpecificAgentInstances;
    private PluginRequest pluginRequest;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor accessor) {
        pluginRequest = new PluginRequest(accessor);
        clusterSpecificAgentInstances = new HashMap<>();
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) {
        ClusterProfile clusterProfile;
        try {
            switch (Request.fromString(request.requestName())) {
                case REQUEST_SHOULD_ASSIGN_WORK:
                    ShouldAssignWorkRequest shouldAssignWorkRequest = ShouldAssignWorkRequest.fromJSON(request.requestBody());
                    clusterProfile = shouldAssignWorkRequest.getClusterProfileProperties();
                    refreshInstancesForCluster(clusterProfile);
                    return shouldAssignWorkRequest.executor(getAgentInstancesFor(clusterProfile)).execute();
                case REQUEST_CREATE_AGENT:
                    CreateAgentRequest createAgentRequest = CreateAgentRequest.fromJSON(request.requestBody());
                    clusterProfile = createAgentRequest.getClusterProfileProperties();
                    refreshInstancesForCluster(clusterProfile);
                    return createAgentRequest.executor(getAgentInstancesFor(clusterProfile), pluginRequest).execute();
                case REQUEST_SERVER_PING:
                    ServerPingRequest serverPingRequest = ServerPingRequest.fromJSON(request.requestBody());
                    List<ClusterProfile> clusterProfiles = serverPingRequest.allClusterProfileProperties();
                    refreshInstancesForAllClusters(clusterProfiles);
                    return serverPingRequest.executor(clusterSpecificAgentInstances, pluginRequest).execute();
                case PLUGIN_SETTINGS_GET_VIEW:
                    return new GetViewRequestExecutor().execute();
                case REQUEST_GET_PROFILE_METADATA:
                    return new GetProfileMetadataExecutor().execute();
                case REQUEST_GET_PROFILE_VIEW:
                    return new GetProfileViewExecutor().execute();
                case REQUEST_VALIDATE_PROFILE:
                    return ProfileValidateRequest.fromJSON(request.requestBody()).executor().execute();
                case PLUGIN_SETTINGS_GET_ICON:
                    return new GetPluginSettingsIconExecutor().execute();
                case PLUGIN_SETTINGS_GET_CONFIGURATION:
                    return new GetPluginConfigurationExecutor().execute();
                case PLUGIN_SETTINGS_VALIDATE_CONFIGURATION:
                    return ValidatePluginSettings.fromJSON(request.requestBody()).executor().execute();
                case REQUEST_GET_CLUSTER_PROFILE_METADATA:
                    return new GetClusterProfileMetadataExecutor().execute();
                case REQUEST_VALIDATE_CLUSTER_PROFILE_CONFIGURATION:
                    return ClusterProfileValidateRequest.fromJSON(request.requestBody()).executor().execute();
                case REQUEST_GET_CLUSTER_PROFILE_VIEW:
                    return new GetViewRequestExecutor().execute();
                case REQUEST_STATUS_REPORT:
                    return new DefaultGoPluginApiResponse(200);
//                    refreshInstances();
//                    return new StatusReportExecutor(pluginRequest, agentInstances, ViewBuilder.instance()).execute();
                case REQUEST_AGENT_STATUS_REPORT:
                    return new DefaultGoPluginApiResponse(200);
//                    refreshInstances();
//                    return AgentStatusReportRequest.fromJSON(request.requestBody()).executor(pluginRequest, agentInstances).execute();
                case REQUEST_CAPABILITIES:
                    return new GetCapabilitiesExecutor().execute();
                case REQUEST_JOB_COMPLETION:
                    JobCompletionRequest jobCompletionRequest = JobCompletionRequest.fromJSON(request.requestBody());
                    clusterProfile = jobCompletionRequest.getClusterProfile();
                    refreshInstancesForCluster(clusterProfile);
                    return jobCompletionRequest.executor(getAgentInstancesFor(clusterProfile), pluginRequest).execute();
                default:
                    throw new UnhandledRequestTypeException(request.requestName());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshInstancesForAllClusters(List<ClusterProfile> clusterProfiles) throws Exception {
        for (ClusterProfile clusterProfile : clusterProfiles) {
            refreshInstancesForCluster(clusterProfile);
        }
    }

    private AgentInstances<DockerContainer> getAgentInstancesFor(ClusterProfile clusterProfile) {
        return clusterSpecificAgentInstances.get(clusterProfile.uuid());
    }

    private void refreshInstancesForCluster(ClusterProfile clusterProfile) throws Exception {
        DockerContainers dockerContainers = clusterSpecificAgentInstances.getOrDefault(clusterProfile.uuid(), new DockerContainers());
        dockerContainers.refreshAll(clusterProfile);

        clusterSpecificAgentInstances.put(clusterProfile.uuid(), dockerContainers);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return PLUGIN_IDENTIFIER;
    }

}
