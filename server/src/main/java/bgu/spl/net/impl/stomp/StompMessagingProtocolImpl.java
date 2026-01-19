package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;
    private String username;

    // static maps for the server (thread safe)
    private static Map<String, String> registeredUsers = new ConcurrentHashMap<>();
    private static Map<String, Integer> loggedInUsers = new ConcurrentHashMap<>();
    private static java.util.concurrent.atomic.AtomicInteger messageIdCounter = new java.util.concurrent.atomic.AtomicInteger(
            0);

    // private maps for the specific client
    private Map<Integer, String> subscriptionIdToChannel = new HashMap<>();
    private Map<String, Integer> channelToSubscriptionId = new HashMap<>();
    private java.util.Set<String> recordedFiles = new java.util.HashSet<>();  // Track files already logged

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }

    @Override
    public void process(String message) {
        String[] lines = message.split("\n");
        String command = lines[0].trim();

        // extract headers from the message
        Map<String, String> headers = new HashMap<>();
        int bodyStart = 1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                bodyStart = i + 1;
                break;
            }
            String[] parts = lines[i].split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }

        // extract body from the message
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = bodyStart; i < lines.length; i++) {
            if (i > bodyStart)
                bodyBuilder.append("\n");
            bodyBuilder.append(lines[i]);
        }
        String body = bodyBuilder.toString();

        switch (command) {
            case "CONNECT":
                handleConnect(headers);
                break;
            case "SUBSCRIBE":
                handleSubscribe(headers);
                break;
            case "SEND":
                handleSend(headers, body);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(headers);
                break;
            case "DISCONNECT":
                handleDisconnect(headers);
                break;
            default:
                sendError("Unknown command: " + command, headers.get("receipt"));
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void handleConnect(Map<String, String> headers) {
        String login = headers.get("login");
        String passcode = headers.get("passcode");

        if (login == null || passcode == null) {
            sendError("Missing login or passcode", null);
            return;
        }
        synchronized (registeredUsers) {
            if (loggedInUsers.containsKey(login)) {
                sendError("User already logged in", null);
                return;
            }
            if (registeredUsers.containsKey(login)) {
                if (!registeredUsers.get(login).equals(passcode)) {
                    sendError("Wrong password", null);
                    return;
                }
            } else {
                registeredUsers.put(login, passcode);
                // Record new user in database
                SqlClient.registerUser(login, passcode);
            }
            loggedInUsers.put(login, connectionId);
            this.username = login;
            // Record login in database
            SqlClient.recordLogin(login);
        }

        String response = "CONNECTED\nversion:1.2\n\n";
        connections.send(connectionId, response);
    }

    private void handleSubscribe(Map<String, String> headers) {
        String destination = headers.get("destination");
        String id = headers.get("id");
        String receipt = headers.get("receipt");

        if (destination == null || id == null) {
            sendError("Missing destination or id", receipt);
            return;
        }

        if (destination.startsWith("/")) {
            destination = destination.substring(1);
        }

        int subscriptionId = Integer.parseInt(id);

        connections.subscribe(connectionId, destination, subscriptionId);
        subscriptionIdToChannel.put(subscriptionId, destination);
        channelToSubscriptionId.put(destination, subscriptionId);

        if (receipt != null) {
            String response = "RECEIPT\nreceipt-id:" + receipt + "\n\n";
            connections.send(connectionId, response);
        }
    }

    private void handleSend(Map<String, String> headers, String body) {
        String destination = headers.get("destination");
        String receipt = headers.get("receipt");

        if (destination == null) {
            sendError("Missing destination", receipt);
            return;
        }

        if (destination.startsWith("/")) {
            destination = destination.substring(1);
        }

        if (!connections.isSubscribed(connectionId, destination)) {
            sendError("Cannot send to channel you are not subscribed to", receipt);
            return;
        }

        int messageId = messageIdCounter.getAndIncrement();
        sendMessageToChannel(destination, body, messageId);

        // Parse source file from body for file tracking
        String filename = "unknown";
        String[] bodyLines = body.split("\n");
        for (String line : bodyLines) {
            if (line.startsWith("source file: ")) {
                filename = line.substring(13).trim();
                break;
            }
        }

        // Record file upload in database (only once per file per session)
        String fileKey = filename + ":" + destination;
        if (!recordedFiles.contains(fileKey)) {
            recordedFiles.add(fileKey);
            SqlClient.recordFileUpload(username, filename, destination);
        }

        if (receipt != null) {
            String response = "RECEIPT\nreceipt-id:" + receipt + "\n\n";
            connections.send(connectionId, response);
        }
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        String id = headers.get("id");
        String receipt = headers.get("receipt");

        if (id == null) {
            sendError("Missing id", receipt);
            return;
        }

        int subscriptionId = Integer.parseInt(id);
        String channel = subscriptionIdToChannel.remove(subscriptionId);

        if (channel != null) {
            channelToSubscriptionId.remove(channel);
            connections.unsubscribe(connectionId, channel);
        }

        if (receipt != null) {
            String response = "RECEIPT\nreceipt-id:" + receipt + "\n\n";
            connections.send(connectionId, response);
        }
    }

    private void handleDisconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");

        // Send receipt BEFORE disconnecting (so handler still exists)
        if (receipt != null) {
            String response = "RECEIPT\nreceipt-id:" + receipt + "\n\n";
            connections.send(connectionId, response);
        }

        if (username != null) {
            synchronized (registeredUsers) {
                loggedInUsers.remove(username);
            }
            // Record logout in database
            SqlClient.recordLogout(username);
        }

        connections.disconnect(connectionId);
        shouldTerminate = true;
    }

    private void sendMessageToChannel(String channel, String body, int messageId) {
        // Get all subscribers for this channel
        java.util.Set<Integer> subscribers = connections.getSubscribers(channel);
        if (subscribers == null) {
            return;
        }

        // Send personalized MESSAGE to each subscriber with THEIR subscription ID
        for (Integer subscriberId : subscribers) {
            int subId = connections.getSubscriptionId(subscriberId, channel);
            String messageFrame = "MESSAGE\n" +
                    "subscription:" + subId + "\n" +
                    "message-id:" + messageId + "\n" +
                    "destination:/" + channel + "\n" +
                    "\n" +
                    body;
            connections.send(subscriberId, messageFrame);
        }
    }

    private void sendError(String message, String receiptId) {
        StringBuilder error = new StringBuilder();
        error.append("ERROR\n");
        error.append("message:").append(message).append("\n");
        if (receiptId != null) {
            error.append("receipt-id:").append(receiptId).append("\n");
        }
        error.append("\n");

        connections.send(connectionId, error.toString());

        if (username != null) {
            synchronized (registeredUsers) {
                loggedInUsers.remove(username);
            }
        }

        shouldTerminate = true;
    }
}
