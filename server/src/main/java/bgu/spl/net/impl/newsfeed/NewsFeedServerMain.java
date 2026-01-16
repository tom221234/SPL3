package bgu.spl.net.impl.newsfeed;

import bgu.spl.net.impl.rci.ObjectEncoderDecoder;
import bgu.spl.net.impl.rci.RemoteCommandInvocationProtocol;
import bgu.spl.net.srv.Server;

public class NewsFeedServerMain {

    public static void main(String[] args) {
        // NOTE: This example is disabled because the server was refactored
        // to use StompMessagingProtocol instead of MessagingProtocol.
        // Use StompServer instead.

        System.out.println(
                "NewsFeedServerMain is disabled. The server has been refactored to use StompMessagingProtocol.");
        System.out.println(
                "Use StompServer instead: mvn exec:java -Dexec.mainClass=\"bgu.spl.net.impl.stomp.StompServer\" -Dexec.args=\"<port> tpc\"");
    }
}
