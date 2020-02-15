package com.healthsaas.edge.safe.SafeDb;


public class SafeDbEntry {
    public long ID;
    public String url;
    public String headers;
    public String payload;
    public long sending;

    public SafeDbEntry() {
        this.ID = -1;
        this.url = "";
        this.headers = "";
        this.payload = "";
        this.sending = 0;
    }
    public SafeDbEntry(String payload, String headers, String url) {
        this.ID = -1;
        this.url = url;
        this.headers = headers;
        this.payload = payload;
        this.sending = 0;

    }
}



