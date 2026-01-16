package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;

import java.io.Closeable;
import java.util.function.Supplier;

public interface Server<T> extends Closeable {

    /**
     * The main loop of the server, Starts listening and handling new clients.
     */
    void serve();

    /**
     * This function returns a new instance of a thread per client pattern server
     * 
     * @param port                  The port for the server socket
     * @param protocolFactory       A factory that creates new
     *                              StompMessagingProtocols
     * @param encoderDecoderFactory A factory that creates new MessageEncoderDecoder
     * @param <T>                   The Message Object for the protocol
     * @return A new Thread per client server
     */
    public static <T> Server<T> threadPerClient(
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encoderDecoderFactory) {

        ConnectionsImpl<T> connections = new ConnectionsImpl<>();
        return new BaseServer<T>(port, protocolFactory, encoderDecoderFactory, connections) {
            @Override
            protected void execute(BlockingConnectionHandler<T> handler) {
                new Thread(handler).start();
            }
        };

    }

    /**
     * This function returns a new instance of a reactor pattern server
     * 
     * @param nthreads              Number of threads available for protocol
     *                              processing
     * @param port                  The port for the server socket
     * @param protocolFactory       A factory that creates new
     *                              StompMessagingProtocols
     * @param encoderDecoderFactory A factory that creates new MessageEncoderDecoder
     * @param <T>                   The Message Object for the protocol
     * @return A new reactor server
     */
    public static <T> Server<T> reactor(
            int nthreads,
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encoderDecoderFactory) {
        ConnectionsImpl<T> connections = new ConnectionsImpl<>();
        return new Reactor<T>(nthreads, port, protocolFactory, encoderDecoderFactory, connections);
    }

}
