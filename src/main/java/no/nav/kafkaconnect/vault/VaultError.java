package no.nav.kafkaconnect.vault;

public class VaultError extends Exception {
    public VaultError(String message, Throwable cause) {
        super(message, cause);
    }
}
