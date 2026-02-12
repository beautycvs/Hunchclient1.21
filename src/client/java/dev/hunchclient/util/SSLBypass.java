package dev.hunchclient.util;

import dev.hunchclient.HunchClient;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * Utility to bypass SSL certificate verification for self-signed certificates.
 * This is needed because our server uses a self-signed certificate on the IP address.
 */
public class SSLBypass {

    private static boolean initialized = false;

    /**
     * Installs a trust manager that accepts all certificates.
     * Call this once at client startup.
     */
    public static synchronized void install() {
        if (initialized) {
            return;
        }

        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            initialized = true;
            HunchClient.LOGGER.info("SSL bypass installed for self-signed certificates");
        } catch (Exception e) {
            HunchClient.LOGGER.error("Failed to install SSL bypass: " + e.getMessage());
        }
    }
}
