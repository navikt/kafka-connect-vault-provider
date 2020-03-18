package no.nav.kafkaconnect;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import no.nav.kafkaconnect.vault.VaultError;
import no.nav.kafkaconnect.vault.VaultUtil;

import java.util.Map;

public class Accessor {
    public static void main(String[] args) throws VaultError, VaultException {
        System.setProperty("VAULT_ADDR", "http://localhost:8200");
        System.setProperty("VAULT_TOKEN", "123456789");

        Vault client = VaultUtil.getInstance().getClient();
        LogicalResponse read = client.logical().read("secret/postgres/local");
        Map<String, String> data = read.getData();
        String username = read.getData().get("username");
        String password = read.getData().get("password");
        System.out.println(username);
        System.out.println(password);
    }
}
