package no.nav.kafkaconnect.vault;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import com.bettercloud.vault.response.LookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

public class VaultUtil {
    private static final Logger logger = LoggerFactory.getLogger(VaultUtil.class);
    public static final String VAULT_TOKEN_PROPERTY = "VAULT_TOKEN";
    public static final String VAULT_TOKEN_PATH_PROPERTY = "VAULT_TOKEN_PATH";
    public static final Long MIN_REFRESH_MARGIN = Duration.ofMinutes(10).toMillis();

    private static VaultUtil INSTANCE;
    private Vault vault;
    private Timer timer;

    private VaultUtil() {
        timer = new Timer("VaultScheduler", true);
    }

    public static VaultUtil getInstance() throws VaultError {
        if (INSTANCE == null) {
            VaultUtil util = new VaultUtil();
            util.init();
            INSTANCE = util;
        }
        return INSTANCE;
    }

    /**
     * We should refresh tokens from Vault before they expire, so we add a MIN_REFRESH_MARGIN margin
     * if the token is valid for less than MIN_REFRESH_MARGIN * 2, we use duration / 2 instead.
     *
     * @param duration - actual refresh interval
     * @return refresh interval in milliseconds
     */
    public static long suggestedRefreshInterval(long duration) {
        if (duration < MIN_REFRESH_MARGIN * 2) {
            return duration / 2;
        } else {
            return duration - MIN_REFRESH_MARGIN;
        }
    }

    public Vault getClient() {
        return vault;
    }

    public Timer getTimer() {
        return timer;
    }

    private Map<String, String> secretsPathMap() {
        Map<String, String> secretsMap = new HashMap<>();

    }

    private void init() throws VaultError {
        VaultConfig vaultConfig = null;
        try {
            vaultConfig = new VaultConfig()
                    .address(getPropertyOrDefault("VAULT_ADDR", "https://vault.adeo.no"))
                    .secretsEnginePathMap()
                    .token(getVaultToken())
                    .openTimeout(5)
                    .readTimeout(30)
                    .sslConfig(new SslConfig().build())
                    .build();
        } catch (VaultException e) {
            throw new VaultError("Could not instantiate the Vault REST client", e);
        }

        vault = new Vault(vaultConfig);

        LookupResponse lookupResponse = null;
        try {
            lookupResponse = vault.auth().lookupSelf();
        } catch (VaultException e) {
            if (e.getHttpStatusCode() == 403) {
                throw new VaultError("The application's vault token seems to be invalid", e);
            } else {
                throw new VaultError("Could not validate the application's vault token", e);
            }
        }
        if (lookupResponse.isRenewable()) {
            final class RefreshTokenTask extends TimerTask {
                @Override
                public void run() {
                    try {
                        logger.info("Refreshing Vault token (old TTL = {} seconds)", vault.auth().lookupSelf().getTTL());
                        AuthResponse response = vault.auth().renewSelf();
                        logger.info("Refreshed Vault token (new TTL = {} seconds)", vault.auth().lookupSelf().getTTL());
                        timer.schedule(new RefreshTokenTask(), suggestedRefreshInterval(Duration.ofSeconds(response.getAuthLeaseDuration()).toMillis()));
                    } catch (VaultException e) {
                        logger.error("Could not refresh the Vault token", e);
                        // Will try refreshing in 5 seconds
                        logger.warn("Waiting 5 secs before trying to refresh the Vault token");
                        timer.schedule(new RefreshTokenTask(), Duration.ofSeconds(5).toMillis());
                    }
                }
            }
            logger.info("Configuring a timer for refreshing");
            timer.schedule(new RefreshTokenTask(), suggestedRefreshInterval(Duration.ofSeconds(lookupResponse.getTTL()).toMillis()));
        } else {
            logger.warn("Vault Token is not renewable");
        }
    }

    private static String getPropertyOrDefault(String prop, String def) {
        return Optional.ofNullable(getProperty(prop)).orElse(def);
    }

    private static String getProperty(String propertyName) {
        return System.getProperty(propertyName, System.getenv(propertyName));
    }

    private static String getVaultToken() {
        try {
            if (getProperty(VAULT_TOKEN_PROPERTY) != null && !"".equals(getProperty(VAULT_TOKEN_PROPERTY))) {
                return getProperty(VAULT_TOKEN_PROPERTY);
            } else if (getProperty(VAULT_TOKEN_PATH_PROPERTY) != null) {
                byte[] encoded = Files.readAllBytes(Paths.get(getProperty(VAULT_TOKEN_PATH_PROPERTY)));
                return new String(encoded, "UTF-8").trim();
            } else if (Files.exists(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))) {
                byte[] encoded = Files.readAllBytes(Paths.get("/var/run/secrets/nais.io/vault/vault_token"));
                return new String(encoded, "UTF-8").trim();
            } else {
                throw new RuntimeException("Neither " + VAULT_TOKEN_PROPERTY + " or " + VAULT_TOKEN_PATH_PROPERTY + " is set");
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not get a vault token for authentication", e);
        }
    }
}

