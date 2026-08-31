package objects;

import java.util.Map;

public class RdpConfig {

    private String manageUrl;
    private String managePort;
    private String tenantId;
    private Map<String, String> headers;

    public String getManageUrl() {
        return manageUrl;
    }

    public void setManageUrl(String manageUrl) {
        this.manageUrl = manageUrl;
    }

    public String getManagePort() {
        return managePort;
    }

    public void setManagePort(String managePort) {
        this.managePort = managePort;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}

