package app.farmgear.android.api;

public class API {
    private final String PROD_APILINK = "https://www.farmgear.app/api";
    private final String DEV_APILINK = "https://www.farmgear.app/api-dev";
    private final String ENV = "prod";
    public String getEnvironment() { return this.ENV; }

    public String getApiLink() {
        if (getEnvironment().equals("prod")) return PROD_APILINK;
        else return DEV_APILINK;
    }
}
