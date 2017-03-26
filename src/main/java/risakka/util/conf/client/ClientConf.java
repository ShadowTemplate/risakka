
package risakka.util.conf.client;

import risakka.util.conf.server.ServerConf;
import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.ExtensionIdProvider;

public class ClientConf extends AbstractExtensionId<ClientConfImpl>
        implements ExtensionIdProvider {

    public final static ClientConf SettingsProvider = new ClientConf();

    private ClientConf() {
    }

    public ServerConf lookup() {
        return ServerConf.SettingsProvider;
    }

    public ClientConfImpl createExtension(ExtendedActorSystem system) {
        return new ClientConfImpl(system.settings().config());
    }
}
