import java.io.IOException;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        int port;
        if (args.length != 1)
        {
            System.out.println("Enter port");
            return;
        }

        port = Integer.parseInt(args[0]);
        Proxy proxy = new Proxy();
        proxy.run(port);
    }
}