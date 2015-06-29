package org.kylin.transport;

import java.net.URI;
import java.util.List;

/**
 * Created by jimmey on 15-6-25.
 */
public interface ClientFactory {

    void create(URI uri, Client.Listener listener);

    List<Client> listAll();

    void remove(Client client);

}
