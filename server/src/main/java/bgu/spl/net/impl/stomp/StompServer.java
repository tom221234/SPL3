package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Reactor;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc|reactor>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1].toLowerCase();

        ConnectionsImpl<String> connections = new ConnectionsImpl<>();

        if (serverType.equals("tpc")) {
            new BaseServer<String>(
                    port,
                    StompMessagingProtocolImpl::new,
                    StompMessageEncoderDecoder::new,
                    connections) {
                @Override
                protected void execute(BlockingConnectionHandler<String> handler) {
                    new Thread(handler).start();
                }
            }.serve();
        } else if (serverType.equals("reactor")) {
            int numThreads = Runtime.getRuntime().availableProcessors();
            new Reactor<String>(
                    numThreads,
                    port,
                    StompMessagingProtocolImpl::new,
                    StompMessageEncoderDecoder::new,
                    connections).serve();
        } else {
            System.out.println("Unknown server type: " + serverType);
        }
    }
}
