package com.redhat.osevg.concessions;

public class DebugBundle {

    private String mongoUrl;
    private boolean connected;

    public DebugBundle(String mongoUrl, boolean connected) {
        this.mongoUrl = mongoUrl;
        this.connected = connected;
    }

    public String getMongoUrl() {
        return mongoUrl;
    }

    public boolean isConnected() {
        return connected;
    }
}
