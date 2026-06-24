package org.jboss.pnc.attester;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/")
public class Public {

    @GET
    public String getMe() throws Exception {
        return "hi";
    }
}
