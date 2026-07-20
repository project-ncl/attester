package org.jboss.pnc.attester;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.time.ZonedDateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Provenance;

import io.quarkus.security.Authenticated;
import lombok.extern.slf4j.Slf4j;

@Path("/")
@Slf4j
public class Public {

    @ConfigProperty(name = "quarkus.application.name")
    String name;

    @Inject
    Attester attester;

    @POST
    @Path("/attest/{buildId}")
    @Produces(APPLICATION_JSON)
    @Authenticated
    public Provenance attest(String buildId) throws Exception {
        return attester.attest(buildId);
    }

    @GET
    @Path("/version")
    public ComponentVersion getVersion() {
        return ComponentVersion.builder()
                .name(name)
                .version(Constants.ATTESTER_VERSION)
                .commit(Constants.COMMIT_HASH)
                .builtOn(ZonedDateTime.parse(Constants.BUILD_TIME))
                .build();
    }
}
