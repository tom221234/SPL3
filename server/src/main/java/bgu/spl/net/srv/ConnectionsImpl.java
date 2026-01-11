package bgu.spl.net.srv;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T> implements Connections<T> {

    // connetionHandler for each ID (KEY:ID VALUE:ConnectionHandler)
    private Map<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();

    // Channel for each ID (KEY: Channel VALUE: set of IDs)
    private Map<String, Set<Integer>> channels = new ConcurrentHashMap<>();

    // ID with list of his channels (KEY: ID VALUE: set of channels)
    private Map<Integer, Set<String>> clientChannels = new ConcurrentHashMap<>();

    // AtomicInteger for the next ID (thread safe)
    private AtomicInteger nextId = new AtomicInteger(0);

    public int registerConnection(ConnectionHandler<T> handler) {
        int id = nextId.getAndIncrement();
        connections.put(id, handler);
        clientChannels.put(id, ConcurrentHashMap.newKeySet());
        return id;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connections.get(connectionId); // get the handler
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        Set<Integer> subscribers = channels.get(channel); // get the subscribers for channel
        if (subscribers != null) {
            for (Integer id : subscribers) {
                send(id, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        Set<String> myChannels = clientChannels.remove(connectionId);
        if (myChannels != null) {
            // for each channel remove the connection id
            for (String channel : myChannels) {
                Set<Integer> subs = channels.get(channel);
                if (subs != null) {
                    subs.remove(connectionId);
                }
            }
        }
        connections.remove(connectionId); // remove the connection
    }

    public void subscribe(int connectionId, String channel) {
        // checks if the channel exists, if not creates it
        channels.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        clientChannels.get(connectionId).add(channel);
    }

    public void unsubscribe(int connectionId, String channel) {
        Set<String> myChannels = clientChannels.get(connectionId);
        if (myChannels != null && myChannels.contains(channel)) {
            myChannels.remove(channel);
            channels.get(channel).remove(connectionId);
        }

    }

    public ConnectionHandler<T> getHandler(int connectionId) {
        return connections.get(connectionId);
    }

    public boolean isSubscribed(int connectionId, String channel) {
        Set<String> myChannels = clientChannels.get(connectionId);
        return myChannels != null && myChannels.contains(channel);
    }
}
