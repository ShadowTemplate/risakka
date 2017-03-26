
package risakka.util.conf.server;

import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.ExtensionIdProvider;

public class ServerConf extends AbstractExtensionId<ServerConfImpl>
        implements ExtensionIdProvider {

    public final static ServerConf SettingsProvider = new ServerConf();

    private ServerConf() {
    }

    public ServerConf lookup() {
        return ServerConf.SettingsProvider;
    }

    public ServerConfImpl createExtension(ExtendedActorSystem system) {
        return new ServerConfImpl(system.settings().config());
    }
}
