import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import java.util.*;

class Proxy {
    private Messages messages = new Messages();
    private Selector selector;
    static DatagramChannel dnsChannel;
    static HashMap<Integer, SelectionKey> dnsMap = new HashMap<>();
    void run(int port) throws IOException
    {
        selector = Selector.open();
        var serverSocket = ServerSocketChannel.open();

        serverSocket.bind(new InetSocketAddress("localhost", port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.socket().bind(new InetSocketAddress(123));
        var DNSKey = dnsChannel.register(selector, SelectionKey.OP_READ);

        while (true)
        {
            selector.select();
            var iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext())
            {
                var key = iterator.next();
                iterator.remove();
                {
                    if (key.isValid() && key.isAcceptable())
                    {
                        accept(key);
                    }
                    if (key.isValid() && key.isConnectable())
                    {
                        connect(key);
                    }
                    if (key.isValid() && key.isReadable())
                    {
                        if (key.equals(DNSKey))
                            resolveDns();
                        else
                            read(key);
                    }
                    if (key.isValid() && key.isWritable())
                    {
                        write(key);
                    }
                }

            }

        }
    }

    private void resolveDns() throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        if (dnsChannel.read(buf) <= 0)
            return;
        Message message = new Message(buf.array());
        var records = message.getSection(Section.ANSWER).stream().findAny();

        int id = message.getHeader().getID();
        System.out.println(id);
        SelectionKey key = dnsMap.get(id);

        Attachment attachment = (Attachment) key.attachment();
        attachment.setHost(InetAddress.getByName(records.get().rdataToString()));
        messages.connectHost(attachment, attachment.getHost(), attachment.getPort(), selector);
        messages.successAnswer(attachment, attachment.getHost(), (byte) attachment.getPort());
        return;
    }

    private void accept(SelectionKey key) throws IOException
    {
        System.out.println("Accepting new client");
        var channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        Attachment client = new Attachment(channel, selector);
        channel.register(selector, SelectionKey.OP_READ, client);
    }

    private void connect(SelectionKey key)
    {
        var channel = ((SocketChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();
        try {
            channel.finishConnect();
            attachment.deleteOps(SelectionKey.OP_CONNECT);
            attachment.addOps(SelectionKey.OP_WRITE);
        } catch (IOException e) {
            e.printStackTrace();
            attachment.close();
        }
    }

    private void read(SelectionKey key)
    {
        Attachment attachment = (Attachment) key.attachment();
        try
        {
            int byteRead = attachment.getChannel().read(attachment.getBuf());
            if (attachment.getPairAttachment() == null)
            {
                if (attachment.isFirstMessage())
                {
                    messages.helloMessage(attachment);
                } else
                {
                    messages.headersMessage(key, attachment, byteRead, selector);
                }
            } else
            {
                Attachment pairAttachment = attachment.getPairAttachment();
                if (byteRead > 0 && pairAttachment.getChannel().isConnected())
                {
                    pairAttachment.addOps(SelectionKey.OP_WRITE);
                }
                if (attachment.getBuf().position() == 0)
                {
                    attachment.deleteOps(SelectionKey.OP_READ);
                    pairAttachment.setFinishWrite(true);
                    if (attachment.isFinishWrite() || pairAttachment.getBuf().position() == 0)
                    {
                        System.out.println("Close channels");
                        attachment.close();
                        pairAttachment.close();
                    }
                }
                if (byteRead == -1)
                {
                    System.out.println("Read all data");
                    attachment.setFinishRead(true);
                }
            }
        } catch (IOException e) {
            attachment.close();
        }

    }

    private void write(SelectionKey key) throws IOException
    {
        Attachment attachment = (Attachment) key.attachment();
        Attachment pairAttachment = attachment.getPairAttachment();
        pairAttachment.getBuf().flip();

        int byteWrite = attachment.getChannel().write(pairAttachment.getBuf());
        if (byteWrite > 0)
        {
            pairAttachment.getBuf().compact();
            pairAttachment.addOps(SelectionKey.OP_READ);
        }
        if (pairAttachment.getBuf().position() == 0)
        {

            attachment.deleteOps(SelectionKey.OP_WRITE);
            if (pairAttachment.isFinishRead())
            {
                System.out.println("Write all data");
                attachment.getChannel().shutdownOutput();
                attachment.setFinishWrite(true);
                if (pairAttachment.isFinishWrite())
                {
                    System.out.println("Close channels");
                    attachment.close();
                    pairAttachment.close();
                }
            }
        }

    }
}