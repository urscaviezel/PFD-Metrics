import com.pfd.metricstool.UpdatePolicy;

public class UpdatePolicyTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        check(UpdatePolicy.repository("owner/PFD-Metrics"));
        check(!UpdatePolicy.repository(""));
        check(!UpdatePolicy.repository("https://github.com/owner/repo"));
        check(!UpdatePolicy.repository("owner/.."));
        check(UpdatePolicy.newer(101, 101, 100, 100));
        check(UpdatePolicy.newer(100, 100, 24, 20));
        check(!UpdatePolicy.newer(100, 100, 100, 100));
        check(!UpdatePolicy.newer(99, 101, 100, 100));
        check(!UpdatePolicy.newer(101, 99, 100, 100));
        check(!UpdatePolicy.newer(Long.MAX_VALUE, 101, 100, 100));
        String asset = "https://github.com/owner/PFD-Metrics/releases/download/v1.0.1/PFDMetricsTool.apk";
        check(UpdatePolicy.assetUrl(asset, "owner/PFD-Metrics"));
        check(!UpdatePolicy.assetUrl(asset.replace("https:", "http:"), "owner/PFD-Metrics"));
        check(!UpdatePolicy.assetUrl(asset.replace("github.com", "github.com.evil.test"), "owner/PFD-Metrics"));
        check(!UpdatePolicy.assetUrl(asset.replace("/owner/", "/other/"), "owner/PFD-Metrics"));
        check(!UpdatePolicy.assetUrl(asset.replace("/v1.0.1/", "/../"), "owner/PFD-Metrics"));
        check(!UpdatePolicy.assetUrl(asset+"?redirect=elsewhere", "owner/PFD-Metrics"));
        System.out.println("Update policy: all assertions passed");
    }
}
