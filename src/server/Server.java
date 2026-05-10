package server;

import remote.ILobbyService;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) throws RemoteException {
        // Helps callbacks (server → client) resolve correctly on many Windows setups
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");

        int port = 6666;
        String name = "lobby";

        try {
            LocateRegistry.createRegistry(port);
            ILobbyService lobby = new LobbyServiceImpl();
            Naming.rebind("rmi://127.0.0.1:" + port + "/" + name, lobby);
            System.out.println("Server ready — registry on port " + port);
            System.out.println("Bound ILobbyService at rmi://127.0.0.1:" + port + "/" + name);
        } catch (MalformedURLException e) {
            throw new RemoteException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }

        System.out.println("Press Enter to stop the server...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }
}
