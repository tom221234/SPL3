package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final MessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    // New fields for STOMP support
    private final StompMessagingProtocol<T> stompProtocol;

    // Original constructor - keep unchanged
    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.stompProtocol = null;
    }

    // New overloaded constructor for STOMP protocol
    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> encdec,
            StompMessagingProtocol<T> stompProtocol) {
        this.sock = sock;
        this.encdec = encdec;
        this.protocol = null;
        this.stompProtocol = stompProtocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { // just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            if (stompProtocol != null) {
                // STOMP mode - process() returns void, responses via Connections
                while (!stompProtocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                    T nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        stompProtocol.process(nextMessage);
                    }
                }
            } else {
                // Original mode - process() returns response
                while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                    T nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        T response = protocol.process(nextMessage);
                        if (response != null) {
                            out.write(encdec.encode(response));
                            out.flush();
                        }
                    }
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        try {
            if (out != null && connected) {
                synchronized (out) {
                    out.write(encdec.encode(msg));
                    out.flush();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
