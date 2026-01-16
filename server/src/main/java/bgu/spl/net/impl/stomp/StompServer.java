package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc|reactor>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number: " + args[0]);
            return;
        }

        String serverType = args[1].toLowerCase();

        if (serverType.equals("tpc")) {
            Server.threadPerClient(
                    port,
                    StompMessagingProtocolImpl::new,
                    StompMessageEncoderDecoder::new).serve();
        } else if (serverType.equals("reactor")) {
            Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    StompMessagingProtocolImpl::new,
                    StompMessageEncoderDecoder::new).serve();
        } else {
            System.out.println("Unknown server type: " + args[1] + ". Use 'tpc' or 'reactor'.");
        }
    }
}
