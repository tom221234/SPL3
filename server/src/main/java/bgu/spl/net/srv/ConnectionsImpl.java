package bgu.spl.net.srv;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T> implements Connections<T> {

    // connectionHandler for each ID (KEY:ID VALUE:ConnectionHandler)
    private Map<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();

    // Channel for each ID (KEY: Channel VALUE: set of IDs)
    private Map<String, Set<Integer>> channels = new ConcurrentHashMap<>();

    // ID with list of his channels (KEY: ID VALUE: set of channels)
    private Map<Integer, Set<String>> clientChannels = new ConcurrentHashMap<>();

    // Subscription ID mapping: connectionId -> (channel -> subscriptionId)
    private Map<Integer, Map<String, Integer>> subscriptionIds = new ConcurrentHashMap<>();

    // AtomicInteger for the next ID (thread safe)
    private AtomicInteger nextId = new AtomicInteger(0);

    /**
     * Registers a handler and returns the assigned connection ID
     */
    public int registerAndGetId(ConnectionHandler<T> handler) {
        int id = nextId.getAndIncrement();
        if (handler != null) {
            connections.put(id, handler);
        }
        clientChannels.put(id, ConcurrentHashMap.newKeySet());
        subscriptionIds.put(id, new ConcurrentHashMap<>());
        return id;
    }

    /**
     * Sets/updates the handler for a given connection ID
     */
    public void setHandler(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }

    /**
     * Legacy method - registers and assigns ID internally
     */
    public void registerConnection(ConnectionHandler<T> handler) {
        int id = nextId.getAndIncrement();
        connections.put(id, handler);
        clientChannels.put(id, ConcurrentHashMap.newKeySet());
        subscriptionIds.put(id, new ConcurrentHashMap<>());
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        Set<Integer> subscribers = channels.get(channel);
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
            for (String channel : myChannels) {
                Set<Integer> subs = channels.get(channel);
                if (subs != null) {
                    subs.remove(connectionId);
                }
            }
        }
        subscriptionIds.remove(connectionId);
        connections.remove(connectionId);
    }

    /**
     * Subscribe with subscription ID tracking
     */
    public void subscribe(int connectionId, String channel, int subscriptionId) {
        channels.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        Set<String> myChannels = clientChannels.get(connectionId);
        if (myChannels != null) {
            myChannels.add(channel);
        }
        Map<String, Integer> mySubIds = subscriptionIds.get(connectionId);
        if (mySubIds != null) {
            mySubIds.put(channel, subscriptionId);
        }
    }

    /**
     * Legacy subscribe without subscription ID (for backwards compatibility)
     */
    public void subscribe(int connectionId, String channel) {
        subscribe(connectionId, channel, 0);
    }

    public void unsubscribe(int connectionId, String channel) {
        Set<String> myChannels = clientChannels.get(connectionId);
        if (myChannels != null && myChannels.contains(channel)) {
            myChannels.remove(channel);
            Set<Integer> subs = channels.get(channel);
            if (subs != null) {
                subs.remove(connectionId);
            }
        }
        Map<String, Integer> mySubIds = subscriptionIds.get(connectionId);
        if (mySubIds != null) {
            mySubIds.remove(channel);
        }
    }

    public ConnectionHandler<T> getHandler(int connectionId) {
        return connections.get(connectionId);
    }

    public boolean isSubscribed(int connectionId, String channel) {
        Set<String> myChannels = clientChannels.get(connectionId);
        return myChannels != null && myChannels.contains(channel);
    }

    /**
     * Get the subscription ID for a specific connection and channel
     */
    public int getSubscriptionId(int connectionId, String channel) {
        Map<String, Integer> mySubIds = subscriptionIds.get(connectionId);
        if (mySubIds != null) {
            Integer subId = mySubIds.get(channel);
            return subId != null ? subId : 0;
        }
        return 0;
    }

    /**
     * Get all subscribers for a channel
     */
    public Set<Integer> getSubscribers(String channel) {
        return channels.get(channel);
    }
}
