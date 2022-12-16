import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class Attachment
{
    private Selector selector;
    private Attachment pairAttachment;
    private int bufferSize = 4096;
    private ByteBuffer buf = ByteBuffer.allocate(bufferSize);
    private SocketChannel channel;
    private boolean first = true;
    private int port;
    private boolean finishWrite = false;
    private boolean finishRead = false;
    private InetAddress host;

    Attachment(SocketChannel channel, Selector selector)
    {
        this.channel = channel;
        this.selector = selector;
    }
    Attachment getPairAttachment()
    {
        return pairAttachment;
    }

    ByteBuffer getBuf()
    {
        return buf;
    }

    SocketChannel getChannel()
    {
        return channel;
    }

    boolean isFinishRead()
    {
        return finishRead;
    }

    void setPairAttachment(Attachment pairAttachment)
    {
        this.pairAttachment = pairAttachment;
    }


    void setFinishRead(boolean finishRead_)
    {
        finishRead = finishRead_;
    }


    void setFinishWrite(boolean outputShutdown)
    {
        this.finishWrite = outputShutdown;
    }

    InetAddress getHost()
    {
        return host;
    }

    void setHost(InetAddress host)
    {
        this.host = host;
    }

    boolean isFirstMessage()
    {
        return first;
    }

    void setFirst(boolean first)
    {
        this.first = first;
    }

    boolean isFinishWrite()
    {
        return finishWrite;
    }

    void close()
    {
        try {
            channel.close();
            if(channel.keyFor(selector) != null)
                channel.keyFor(selector).cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void addOps(int ops)
    {
        var key = channel.keyFor(selector);
        key.interestOps(key.interestOps() | ops);
    }

    void deleteOps(int ops)
    {
        var key = channel.keyFor(selector);
        key.interestOps(key.interestOps() & ~ops);
    }
    void setPort(int port)
    {
        this.port = port;
    }
    int getPort()
    {
        return port;
    }

}
