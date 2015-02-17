package nlp.stamr.annotation;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * This handles creating annotation objects via a server-style communication, so I can leave the process
 * running and not constantly have to wait
 */
public class AnnotationServer {

    AnnotationManager manager = new AnnotationManager();
    public final static int COMM_PORT = 2109;  // socket port for client comms

    private ServerSocket serverSocket;

    /** Default constructor. */
    public AnnotationServer()
    {
        initServerSocket();
        try
        {
            while (true)
            {
                // listen for and accept a client connection to serverSocket
                Socket sock = this.serverSocket.accept();
                InputStream iStream = sock.getInputStream();
                ObjectInputStream oiStream = new ObjectInputStream(iStream);
                String input = (String)oiStream.readObject();

                System.out.println("Annotating: "+input);
                MultiSentenceAnnotationWrapper annotationWrapper = manager.annotateMultiSentence(input);
                System.out.println("Done");

                OutputStream oStream = sock.getOutputStream();
                ObjectOutputStream ooStream = new ObjectOutputStream(oStream);
                ooStream.writeObject(annotationWrapper);  // send serilized payload
                ooStream.close();
                sock.close();
            }
        }
        catch (SecurityException se)
        {
            System.err.println("Unable to get host address due to security.");
            System.err.println(se.toString());
            System.exit(1);
        }
        catch (IOException ioe)
        {
            System.err.println("Unable to read data from an open socket.");
            System.err.println(ioe.toString());
            System.exit(1);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally
        {
            try
            {
                this.serverSocket.close();
            }
            catch (IOException ioe)
            {
                System.err.println("Unable to close an open socket.");
                System.err.println(ioe.toString());
                System.exit(1);
            }
        }
    }

    /** Initialize a server socket for communicating with the client. */
    private void initServerSocket()
    {
        try
        {
            this.serverSocket = new ServerSocket(COMM_PORT);
            assert this.serverSocket.isBound();
            System.out.println("SERVER inbound data port " +
                    this.serverSocket.getLocalPort() +
                    " is ready and waiting for client to connect...");
        }
        catch (SocketException se)
        {
            System.err.println("Unable to create socket.");
            System.err.println(se.toString());
            System.exit(1);
        }
        catch (IOException ioe)
        {
            System.err.println("Unable to read data from an open socket.");
            System.err.println(ioe.toString());
            System.exit(1);
        }
    }

    /**
     * Run this class as an application.
     */
    public static void main(String[] args)
    {
        AnnotationServer server = new AnnotationServer();
    }
}
