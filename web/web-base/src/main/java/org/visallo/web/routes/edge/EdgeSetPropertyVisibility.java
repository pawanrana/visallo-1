package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.*;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.web.util.VisibilityValidator;

import java.util.ResourceBundle;

@Singleton
public class EdgeSetPropertyVisibility implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(EdgeSetPropertyVisibility.class);
    private final Graph graph;
    private final WorkspaceRepository workspaceRepository;
    private final VisibilityTranslator visibilityTranslator;
    private final GraphRepository graphRepository;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public EdgeSetPropertyVisibility(
            Graph graph,
            WorkspaceRepository workspaceRepository,
            VisibilityTranslator visibilityTranslator,
            GraphRepository graphRepository,
            WorkQueueRepository workQueueRepository
    ) {
        this.graph = graph;
        this.workspaceRepository = workspaceRepository;
        this.visibilityTranslator = visibilityTranslator;
        this.graphRepository = graphRepository;
        this.workQueueRepository = workQueueRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "graphEdgeId") String graphEdgeId,
            @Required(name = "newVisibilitySource") String newVisibilitySource,
            @Optional(name = "oldVisibilitySource") String oldVisibilitySource,
            @Optional(name = "propertyKey") String propertyKey,
            @Required(name = "propertyName") String propertyName,
            @ActiveWorkspaceId String workspaceId,
            ResourceBundle resourceBundle,
            User user,
            Authorizations authorizations
    ) throws Exception {
        Edge edge = graph.getEdge(graphEdgeId, authorizations);
        if (edge == null) {
            throw new VisalloResourceNotFoundException("Could not find edge: " + graphEdgeId, graphEdgeId);
        }

        VisibilityValidator.validate(graph, visibilityTranslator, resourceBundle, newVisibilitySource, user, authorizations);

        // add the vertex to the workspace so that the changes show up in the diff panel
        workspaceRepository.updateEntityOnWorkspace(workspaceId, edge.getVertexId(Direction.IN), user);
        workspaceRepository.updateEntityOnWorkspace(workspaceId, edge.getVertexId(Direction.OUT), user);

        Property property = graphRepository.updatePropertyVisibilitySource(
                edge,
                propertyKey,
                propertyName,
                oldVisibilitySource,
                newVisibilitySource,
                workspaceId,
                user,
                authorizations
        );
        this.graph.flush();

        workQueueRepository.pushGraphPropertyQueue(
                edge,
                property,
                workspaceId,
                newVisibilitySource,
                Priority.HIGH
        );

        return VisalloResponse.SUCCESS;
    }
}
