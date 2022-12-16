import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class Messages
{
    void headersMessage(SelectionKey key, Attachment attachment, int byteRead, Selector selector) throws IOException
    {
        byte[] array = attachment.getBuf().array();
        if (checkIPv4Request(array))
        {
            InetAddress host = getAddress(array);
            int port = getPort(array, byteRead);
            connectHost(attachment, host, port, selector);
            successAnswer(attachment, host, (byte) port);
        }
        if (checkDomainRequest(array))
        {
            int length = array[4];
            StringBuilder domain = new StringBuilder();
            int i;
            for (i = 5; i < 5 + length; i++)
            {
                domain.append((char) array[i]);
            }
            int port = (((0xFF & array[i]) << 8) + (0xFF & array[i + 1]));
            attachment.setPort(port);
            Name name = org.xbill.DNS.Name.fromString(domain.toString(), Name.root);
            Record rec = Record.newRecord(name, Type.A, DClass.IN);
            Message msg = Message.newQuery(rec);
            Proxy.dnsChannel.write(ByteBuffer.wrap(msg.toWire()));
            Proxy.dnsMap.put(msg.getHeader().getID(), key);
        }
    }

    void successAnswer(Attachment attachment, InetAddress host, byte port) throws IOException
    {
        byte[] answer = {
                0x05,
                0x00,
                0x00,
                0x01,
                host.getAddress()[0],
                host.getAddress()[1],
                host.getAddress()[2],
                host.getAddress()[3],
                0x00,
                port};
        attachment.getChannel().write(ByteBuffer.wrap(answer));
        attachment.getBuf().clear();
    }

    void connectHost(Attachment attachment, InetAddress host, int port, Selector selector) throws IOException
    {
        SocketChannel newHostChannel;
        newHostChannel = SocketChannel.open();
        newHostChannel.configureBlocking(false);
        newHostChannel.connect(new InetSocketAddress(host, port));
        Attachment newHost = new Attachment(newHostChannel, selector);
        attachment.setPairAttachment(newHost);
        newHost.setPairAttachment(attachment);
        attachment.getBuf().clear();
        attachment.getPairAttachment().getBuf().clear();
        newHostChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, newHost);
    }

    void helloMessage(Attachment attachment) throws IOException
    {
        if (checkHello(attachment.getBuf().array()))
        {
            byte[] answer = {0x05, 0x00};
            attachment.getChannel().write(ByteBuffer.wrap(answer));
            attachment.getBuf().clear();
            attachment.setFirst(false);
        } else
        {
            byte[] answer = {0x05, (byte) 0xFF};
            attachment.getChannel().write(ByteBuffer.wrap(answer));
            attachment.close();
        }
    }

    private boolean checkIPv4Request(byte[] buf)
    {
        return buf[0] == 0x05 && buf[1] == 0x01 && buf[3] == 0x01;
    }

    private boolean checkDomainRequest(byte[] buf)
    {
        return buf[0] == 0x05 && buf[1] == 0x01 && buf[3] == 0x03;
    }

    private InetAddress getAddress(byte[] buf) throws UnknownHostException
    {
        if (buf[3] == 0x01)
        {
            byte[] addr = new byte[]{buf[4], buf[5], buf[6], buf[7]};
            return InetAddress.getByAddress(addr);
        }
        return null;
    }

    private int getPort(byte[] buf, int lenBuf)
    {
        return (((0xFF & buf[lenBuf - 2]) << 8) + (0xFF & buf[lenBuf - 1]));
    }

    private boolean checkHello(byte[] buf)
    {
        return buf[0] == 0x05 && buf[1] != 0 && buf[2] == 0x00;
    }
}